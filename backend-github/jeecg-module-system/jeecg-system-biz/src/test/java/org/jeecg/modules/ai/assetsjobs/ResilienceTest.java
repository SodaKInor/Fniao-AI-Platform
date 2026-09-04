package org.jeecg.modules.ai.assetsjobs;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import static org.junit.Assert.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.jeecg.modules.ai.application.jobs.*;
import org.jeecg.modules.ai.application.streams.*;
import org.jeecg.modules.ai.config.jobs.*;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;

public class ResilienceTest {
    private DbFixture f;
    @Before public void before() throws Exception { f=new DbFixture(); }
    @After public void after() throws Exception { f.close(); }

    @Test public void activeResultDownloadIsNotStolenBeforeLease() throws Exception {
        JobRecord fetching=fetching("active-fetch");
        JobWorker worker=jobWorker(request -> { throw new AssertionError("must not infer"); },f.reader(),60000,
                AiRuntimeMetrics.disabled());
        worker.start();
        try { Thread.sleep(450); } finally { worker.close(); }
        assertEquals(JobState.FETCHING_RESULT,f.get(fetching).getState());
        assertEquals(0,f.reads.get()); assertEquals(0,f.calls.get());
    }

    @Test public void staleResultRecoveryReadsArtifactsWithoutReplayingInference() throws Exception {
        JobRecord fetching=fetching("stale-fetch"); ageJob(fetching,10000);
        AtomicInteger providerCalls=new AtomicInteger();
        JobWorker worker=jobWorker(request -> { providerCalls.incrementAndGet(); throw new AssertionError("must not infer"); },
                f.reader(),10,AiRuntimeMetrics.disabled());
        worker.start();
        try { waitFor(() -> f.get(fetching).getState()==JobState.SUCCEEDED); } finally { worker.close(); }
        assertEquals(0,providerCalls.get()); assertEquals(1,f.reads.get());
        assertEquals(2,f.countFiles());
    }

    @Test public void staleDispatchedWorkBecomesUnknownAndTerminalRaceWins() throws Exception {
        JobRecord waiting=waiting("stale-waiting"); ageJob(waiting,10000);
        AtomicInteger providerCalls=new AtomicInteger();
        JobWorker worker=jobWorker(request -> { providerCalls.incrementAndGet(); return f.result(false); },
                f.reader(),10,AiRuntimeMetrics.disabled());
        worker.start();
        try { waitFor(() -> f.get(waiting).getState()==JobState.UNKNOWN); } finally { worker.close(); }
        assertEquals(0,providerCalls.get());
        assertEquals(UnknownOperationReason.PROVIDER_QUERY_UNAVAILABLE,f.get(waiting).getUnknownReason());

        JobRecord raced=waiting("terminal-race"); ageJob(raced,10000);
        JobRecord candidate=f.jobs.findUncertain(Instant.now().minusMillis(100),10).stream()
                .filter(j -> id(j).equals(id(raced))).findFirst().get();
        assertTrue(f.jobs.updateClaimed(id(raced),raced.getVersion(),raced.getDispatchToken(),
                new JobUpdate(JobState.FAILED,null,null,new JobError(ErrorCode.INTERNAL_ERROR,"local failure",true),Instant.now())));
        assertFalse(f.jobs.markUncertainUnknown(id(candidate),candidate.getVersion(),candidate.getDispatchToken(),
                Instant.now().minusMillis(100),Instant.now()));
        assertEquals(JobState.FAILED,f.get(raced).getState());
    }

    @Test public void pendingRestartDispatchesExactlyOnce() throws Exception {
        JobRecord pending=f.submit(f.input("a"),"pending-restart");
        JobWorker worker=jobWorker(f.provider(f.result(false)),f.reader(),1000,AiRuntimeMetrics.disabled());
        worker.start();
        try { waitFor(() -> f.get(pending).getState()==JobState.SUCCEEDED); } finally { worker.close(); }
        assertEquals(1,f.calls.get());
    }

    @Test public void ambiguousStreamStartNeverReplaysRemotePost() throws Exception {
        f.streamSource("a","ambiguous_source",true);
        StreamSession session=start("ambiguous-start",new StreamProviderFeatures(true,true,true,true),"ambiguous_source");
        StreamSession claimed=f.streamSessions.claimPending(sid(session),session.getVersion(),"old_start_token",Instant.now()).get();
        ageStream(claimed,10000);
        AtomicInteger starts=new AtomicInteger();
        StreamSessionProvider provider=streamProvider(starts,new AtomicInteger(),new AtomicInteger(),StreamSessionState.RUNNING);
        StreamSessionWorker worker=streamWorker(provider,10,AiRuntimeMetrics.disabled()); worker.start();
        try { waitFor(() -> stream(session).getState()==StreamSessionState.UNKNOWN); } finally { worker.close(); }
        assertEquals(0,starts.get());
        assertEquals(UnknownOperationReason.PROVIDER_RESPONSE_LOST,stream(session).getUnknownReason());
    }

    @Test public void uncertainStopQueriesConfirmationWithoutRepeatingStop() throws Exception {
        f.streamSource("a","stop_source",true);
        StreamSession pending=start("recover-stop",new StreamProviderFeatures(true,true,true,true),"stop_source");
        StreamSession claimed=f.streamSessions.claimPending(sid(pending),pending.getVersion(),"legacy_token",Instant.now()).get();
        assertTrue(f.streamSessions.updateClaimed(sid(claimed),claimed.getVersion(),claimed.getDispatchToken(),
                new StreamSessionUpdate(StreamSessionState.RUNNING,"provider-session",null,null,null,Instant.now())));
        StreamSession requested=new StopStreamSessionService(f.streamSessions,f.clock).stop(sid(pending),"a");
        f.sql.update("UPDATE ai_stream_session SET dispatch_token=?,updated_at=? WHERE session_id=?",
                "foreign_stop_attempt",System.currentTimeMillis()-10000,sid(requested));
        AtomicInteger starts=new AtomicInteger(),queries=new AtomicInteger(),stops=new AtomicInteger();
        StreamSessionWorker worker=streamWorker(streamProvider(starts,queries,stops,StreamSessionState.STOPPED),10,
                AiRuntimeMetrics.disabled()); worker.start();
        try { waitFor(() -> stream(pending).getState()==StreamSessionState.STOPPED); } finally { worker.close(); }
        assertEquals(0,starts.get()); assertEquals(1,queries.get()); assertEquals(0,stops.get());
    }

    @Test public void interruptedStreamSnapshotDoesNotAdvanceCursorOrPublishPartialAsset() throws Exception {
        f.streamSource("a","broken_snapshot_source",true);
        StreamSession running=manualRunning("broken-snapshot","broken_snapshot_source");
        ProviderArtifact snapshot=new ProviderArtifact("fixture:broken-snapshot",
                new ContentMetadata("snapshot.png","image/png",(long)f.png.length,null),Instant.now().plusSeconds(60));
        ProviderStreamEvent event=new ProviderStreamEvent("broken_event",25,Instant.now(),"person",null,snapshot);
        ProviderArtifactReader reader=(capability,artifact,limit) -> new InputStream() {
            int position;
            public int read() throws IOException {
                if (position>=10) throw new IOException("fixture interruption");
                return f.png[position++] & 255;
            }
        };
        try {
            new StreamEventCollector(f.streamEvents,reader,f.files,f.clock,10*1024*1024).collect(running,
                    new ProviderStreamEventPage(Collections.singletonList(event),"must-not-advance"));
            fail();
        } catch (ProviderException expected) { assertEquals(ErrorCode.ARTIFACT_TRANSFER,expected.getErrorCode()); }
        assertNull(stream(running).getCursor());
        assertTrue(f.streamEvents.listOwned(sid(running),"a",null,20).getItems().isEmpty());
        assertEquals(0,f.countFiles());
    }

    @Test public void invalidSnapshotBatchIsAtomicAndCannotCrossStreamSessions() throws Exception {
        f.close(); f=new DbFixture(20,2);
        f.streamSource("a","snapshot_batch_source",true);
        StreamSession first=manualRunning("snapshot-batch-a","snapshot_batch_source");
        StreamSession second=manualRunning("snapshot-batch-b","snapshot_batch_source");
        String providerEvent="snapshot_origin";
        String firstSnapshot=snapshotId(sid(first),providerEvent);
        f.files.collect(firstSnapshot,"a",new ContentMetadata("snapshot.png","image/png",(long)f.png.length,null),
                new ByteArrayInputStream(f.png),10*1024*1024);

        assertTrue(f.streamEvents.appendAndAdvance(sid(first),first.getVersion(),null,
                Collections.singletonList(new StreamEvent("first_event",providerEvent,1,f.clock.instant(),
                        "person",null,firstSnapshot)),"first-cursor",Instant.now()));

        List<StreamEvent> invalidBatch=Arrays.asList(event("valid_before_invalid","valid_provider",2),
                new StreamEvent("cross_session","foreign_provider",3,f.clock.instant(),"person",null,firstSnapshot));
        assertFalse(f.streamEvents.appendAndAdvance(sid(second),second.getVersion(),null,invalidBatch,
                "must-not-advance",Instant.now()));
        assertNull(stream(second).getCursor());
        assertTrue(f.streamEvents.listOwned(sid(second),"a",null,20).getItems().isEmpty());
        assertEquals(1,f.streamEvents.listOwned(sid(first),"a",null,20).getItems().size());
    }

    @Test public void boundedMetricsExposeQueuesDurationsErrorsAndEventDeduplication() throws Exception {
        f.close(); SimpleMeterRegistry registry=new SimpleMeterRegistry(); AiRuntimeMetrics metrics=new AiRuntimeMetrics(registry);
        f=new DbFixture(20,1,metrics);
        assertEquals(0.0,registry.get("wgai.ai.queue.size").tag("kind","job").gauge().value(),0.0);
        f.submit(f.input("a"),"metric-pending");
        assertEquals(1.0,registry.get("wgai.ai.queue.size").tag("kind","job").gauge().value(),0.0);
        metrics.record("image","dispatch","unknown",ErrorCode.PROVIDER_TIMEOUT,1000);
        assertEquals(1.0,registry.get("wgai.ai.operations").tag("kind","image").tag("stage","dispatch")
                .tag("outcome","unknown").counter().count(),0.0);
        assertEquals(1.0,registry.get("wgai.ai.errors").tag("error","PROVIDER_TIMEOUT").counter().count(),0.0);

        f.streamSource("a","metric_source",true);
        StreamSession running=manualRunning("metric-stream","metric_source");
        List<StreamEvent> first=Arrays.asList(event("m1","p1",0),event("m2","p2",100));
        assertTrue(f.streamEvents.appendAndAdvance(sid(running),running.getVersion(),null,first,"c1",Instant.now()));
        StreamSession current=stream(running);
        assertTrue(f.streamEvents.appendAndAdvance(sid(current),current.getVersion(),"c1",
                Arrays.asList(event("m1","p1",0),event("m3","p3",200)),"c2",Instant.now()));
        assertEquals(3.0,registry.get("wgai.ai.stream.events").tag("outcome","inserted").counter().count(),0.0);
        assertEquals(1.0,registry.get("wgai.ai.stream.events").tag("outcome","duplicate").counter().count(),0.0);
        registry.close();
    }

    private JobRecord waiting(String key) {
        JobRecord job=f.submit(f.input("a"),key);
        JobRecord claim=f.jobs.claimPending(id(job),job.getVersion(),"job_claim_"+key.replace('-','_'),Instant.now()).get();
        assertTrue(f.jobs.updateClaimed(id(claim),claim.getVersion(),claim.getDispatchToken(),
                new JobUpdate(JobState.WAITING,null,null,null,Instant.now())));
        return f.get(job);
    }
    private JobRecord fetching(String key) {
        JobRecord job=waiting(key); assertTrue(f.jobs.updateClaimed(id(job),job.getVersion(),job.getDispatchToken(),
                new JobUpdate(JobState.FETCHING_RESULT,f.result(true),null,null,Instant.now())));
        return f.get(job);
    }
    private void ageJob(JobRecord job,long millis) {
        f.sql.update("UPDATE ai_job SET updated_at=? WHERE request_id=?",System.currentTimeMillis()-millis,id(job));
    }
    private JobWorker jobWorker(InferenceProvider provider,ProviderArtifactReader reader,long lease,AiRuntimeMetrics metrics) {
        DefaultListableBeanFactory beans=new DefaultListableBeanFactory();
        beans.registerSingleton("provider",provider); beans.registerSingleton("reader",reader);
        return new JobWorker(f.jobs,f.jobs,beans.getBeanProvider(InferenceProvider.class),
                beans.getBeanProvider(VideoAnalysisProvider.class),beans.getBeanProvider(ProviderArtifactReader.class),
                f.files,f.clock,1,512L*1024*1024,f.capabilities,lease,metrics);
    }
    private StreamSession start(String key,StreamProviderFeatures features,String source) {
        return new StartStreamSessionService(f.streamSessions,f.streamSources,f.capabilities,c -> features,f.clock)
                .start("a",key,"video-stream-analysis.v1",source,f.streamParameters()).getSession();
    }
    private StreamSession manualRunning(String key,String source) {
        StreamSession session=start(key,new StreamProviderFeatures(true,true,true,true),source);
        StreamSession claim=f.streamSessions.claimPending(sid(session),session.getVersion(),"manual_token",Instant.now()).get();
        assertTrue(f.streamSessions.updateClaimed(sid(claim),claim.getVersion(),claim.getDispatchToken(),
                new StreamSessionUpdate(StreamSessionState.RUNNING,"provider-session",null,null,null,Instant.now())));
        return stream(session);
    }
    private void ageStream(StreamSession session,long millis) {
        f.sql.update("UPDATE ai_stream_session SET updated_at=? WHERE session_id=?",System.currentTimeMillis()-millis,sid(session));
    }
    private StreamSessionWorker streamWorker(StreamSessionProvider provider,long lease,AiRuntimeMetrics metrics) {
        DefaultListableBeanFactory beans=new DefaultListableBeanFactory(); beans.registerSingleton("provider",provider);
        beans.registerSingleton("reader",(ProviderArtifactReader)(snapshot,artifact,limit) -> new ByteArrayInputStream(f.png));
        return new StreamSessionWorker(f.streamSessions,f.streamSources,f.streamEvents,
                beans.getBeanProvider(StreamSessionProvider.class),beans.getBeanProvider(ProviderArtifactReader.class),
                f.files,f.clock,1,10*1024*1024,lease,metrics);
    }
    private StreamSessionProvider streamProvider(AtomicInteger starts,AtomicInteger queries,AtomicInteger stops,
            StreamSessionState queriedState) {
        return new StreamSessionProvider() {
            public ProviderStreamSession start(ProviderStreamStartRequest request) {
                starts.incrementAndGet(); return new ProviderStreamSession("provider-session",StreamSessionState.RUNNING,null,"fixture-v1");
            }
            public ProviderStreamSession getSession(String id) {
                queries.incrementAndGet(); return new ProviderStreamSession(id,queriedState,null,"fixture-v1");
            }
            public ProviderStreamEventPage getEvents(String id,String cursor,int limit) {
                return new ProviderStreamEventPage(Collections.emptyList(),cursor);
            }
            public StreamStopResult stop(String id) {
                stops.incrementAndGet(); return new StreamStopResult(id,StreamStopOutcome.CONFIRMED_STOPPED);
            }
        };
    }
    private StreamEvent event(String id,String provider,long offset) {
        return new StreamEvent(id,provider,offset,f.clock.instant(),"person",new BigDecimal("0.8"),null);
    }
    private String snapshotId(String session,String providerEvent) {
        return "out_"+UUID.nameUUIDFromBytes(
                (session+"\n"+providerEvent+"-snapshot").getBytes(StandardCharsets.UTF_8));
    }
    private JobRecord job(String id) { return f.jobs.findOwned(id,"a").get(); }
    private StreamSession stream(StreamSession session) { return f.streamSessions.findOwned(sid(session),"a").get(); }
    private String id(JobRecord job) { return job.getRequest().getRequestId(); }
    private String sid(StreamSession session) { return session.getRequest().getSessionId(); }
    private void waitFor(Check check) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (!check.done() && System.nanoTime()<deadline) Thread.sleep(20);
        assertTrue("timed out",check.done());
    }
    private interface Check { boolean done() throws Exception; }
}
