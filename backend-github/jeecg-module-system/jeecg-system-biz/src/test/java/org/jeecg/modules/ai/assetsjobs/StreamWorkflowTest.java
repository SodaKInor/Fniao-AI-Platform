package org.jeecg.modules.ai.assetsjobs;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.jeecg.modules.ai.application.jobs.AiRequestException;
import org.jeecg.modules.ai.application.streams.*;
import org.jeecg.modules.ai.config.jobs.StreamSessionWorker;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;

public class StreamWorkflowTest {
    private DbFixture f;
    @Before public void before() throws Exception { f=new DbFixture(); }
    @After public void after() throws Exception { f.close(); }

    @Test public void sourceOwnershipIdempotencyAndPendingStopAreDurable() throws Exception {
        f.streamSource("a","source_a",true);
        StartStreamSessionService start=start(new StreamProviderFeatures(true,true,true,true));
        StreamSessionSubmission created=start.start("a","stream-key-a","video-stream-analysis.v1","source_a",f.streamParameters());
        StreamSessionSubmission same=start.start("a","stream-key-a","video-stream-analysis.v1","source_a",
                new StreamParameters(20,250,true));
        assertTrue(created.isCreated()); assertFalse(same.isCreated());
        assertEquals(created.getSession().getRequest().getSessionId(),same.getSession().getRequest().getSessionId());
        try { start.start("a","stream-key-a","video-stream-analysis.v1","source_a",new StreamParameters(10,250,true)); fail(); }
        catch (IdempotencyConflictException expected) { }
        try { start.start("b","stream-key-b","video-stream-analysis.v1","source_a",f.streamParameters()); fail(); }
        catch (AiRequestException expected) { assertEquals(ErrorCode.NOT_FOUND,expected.getCode()); }
        assertTrue(f.streamSources.listOwned("b").isEmpty());
        StopStreamSessionService stop=new StopStreamSessionService(f.streamSessions,f.clock);
        StreamSession stopped=stop.stop(created.getSession().getRequest().getSessionId(),"a");
        assertEquals(StreamSessionState.STOPPED,stopped.getState());
        assertEquals(StreamSessionState.STOPPED,stop.stop(stopped.getRequest().getSessionId(),"a").getState());
    }

    @Test public void eventsAreDeduplicatedPagedAndRejectedAfterTerminalState() throws Exception {
        StreamSession running=running("event-key",new StreamProviderFeatures(true,true,true,true));
        List<StreamEvent> first=Arrays.asList(event("local_1","provider_1",0,null),event("local_2","provider_2",100,null));
        assertTrue(f.streamEvents.appendAndAdvance(id(running),running.getVersion(),null,first,"cursor-1",Instant.now()));
        StreamSession advanced=owned(running); assertEquals("cursor-1",advanced.getCursor());
        assertTrue(f.streamEvents.appendAndAdvance(id(running),advanced.getVersion(),"cursor-1",
                Arrays.asList(event("local_1","provider_1",0,null),event("local_3","provider_3",200,null)),"cursor-2",Instant.now()));
        StreamEventPage page=f.streamEvents.listOwned(id(running),"a",null,2);
        assertEquals(2,page.getItems().size()); assertNotNull(page.getNextCursor());
        StreamEventPage tail=f.streamEvents.listOwned(id(running),"a",page.getNextCursor(),2);
        assertEquals(1,tail.getItems().size()); assertEquals("provider_3",tail.getItems().get(0).getProviderEventId());
        assertTrue(f.streamEvents.listOwned(id(running),"b",null,20).getItems().isEmpty());
        StreamSession current=owned(running);
        assertTrue(f.streamSessions.updateClaimed(id(current),current.getVersion(),current.getDispatchToken(),
                new StreamSessionUpdate(StreamSessionState.STOPPED,"provider-session","cursor-2",null,null,Instant.now())));
        StreamSession stopped=owned(running);
        assertFalse(f.streamEvents.appendAndAdvance(id(stopped),stopped.getVersion(),"cursor-2",
                Collections.singletonList(event("late","late_provider",300,null)),"cursor-3",Instant.now()));
        assertEquals(3,f.streamEvents.listOwned(id(running),"a",null,20).getItems().size());
    }

    @Test public void workerStartsOnceStoresSnapshotAndRequiresConfirmedStop() throws Exception {
        f.streamSource("a","source_worker",true); StreamSession session=start(all()).start("a","stream-worker",
                "video-stream-analysis.v1","source_worker",f.streamParameters()).getSession();
        AtomicInteger starts=new AtomicInteger(),stops=new AtomicInteger(); AtomicBoolean emitted=new AtomicBoolean();
        StreamSessionProvider provider=new StreamSessionProvider() {
            public ProviderStreamSession start(ProviderStreamStartRequest request) {
                starts.incrementAndGet(); assertEquals("provider-source-fixture",request.getProviderSourceRef());
                return new ProviderStreamSession("provider-session",StreamSessionState.RUNNING,null,"fixture-v1");
            }
            public ProviderStreamSession getSession(String providerId) {
                return new ProviderStreamSession(providerId,StreamSessionState.RUNNING,null,"fixture-v1");
            }
            public ProviderStreamEventPage getEvents(String providerId,String cursor,int limit) {
                if (!emitted.compareAndSet(false,true)) return new ProviderStreamEventPage(Collections.emptyList(),cursor);
                ProviderArtifact snapshot=new ProviderArtifact("fixture:snapshot",
                        new ContentMetadata("snapshot.png","image/png",(long)f.png.length,null),f.clock.instant().plusSeconds(60));
                ProviderStreamEvent event=new ProviderStreamEvent("provider_event",15,f.clock.instant(),"person",null,snapshot);
                return new ProviderStreamEventPage(Collections.singletonList(event),"event-cursor");
            }
            public StreamStopResult stop(String providerId) {
                stops.incrementAndGet(); return new StreamStopResult(providerId,StreamStopOutcome.CONFIRMED_STOPPED);
            }
        };
        StreamSessionWorker worker=worker(provider); worker.start();
        try {
            waitFor(() -> !f.streamEvents.listOwned(id(session),"a",null,20).getItems().isEmpty());
            assertEquals(1,starts.get()); StreamEvent event=f.streamEvents.listOwned(id(session),"a",null,20).getItems().get(0);
            assertNotNull(event.getSnapshotAssetId()); f.files.owned(event.getSnapshotAssetId(),"a");
            new StopStreamSessionService(f.streamSessions,f.clock).stop(id(session),"a");
            waitFor(() -> owned(session).getState()==StreamSessionState.STOPPED);
            assertEquals(1,stops.get()); assertEquals(1,starts.get());
        } finally { worker.close(); }
    }

    @Test public void lostStartBecomesUnknownAndRecoveryQueriesInsteadOfRestarting() throws Exception {
        f.streamSource("a","source_lost",true); StreamSession lost=start(all()).start("a","stream-lost",
                "video-stream-analysis.v1","source_lost",f.streamParameters()).getSession();
        AtomicInteger starts=new AtomicInteger();
        StreamSessionProvider losing=provider(() -> { starts.incrementAndGet();
            throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,ExecutionCertainty.UNKNOWN,"lost"); });
        StreamSessionWorker worker=worker(losing); worker.start();
        try { waitFor(() -> owned(lost).getState()==StreamSessionState.UNKNOWN); }
        finally { worker.close(); }
        assertEquals(1,starts.get());
        assertEquals(UnknownOperationReason.PROVIDER_RESPONSE_LOST,owned(lost).getUnknownReason());

        f.sql.update("DELETE FROM ai_stream_session");
        StreamSession recoverable=running("stream-recover",all()); AtomicInteger queries=new AtomicInteger();
        StreamSessionProvider querying=new StreamSessionProvider() {
            public ProviderStreamSession start(ProviderStreamStartRequest request) { throw new AssertionError("must not restart"); }
            public ProviderStreamSession getSession(String providerId) {
                queries.incrementAndGet(); return new ProviderStreamSession(providerId,StreamSessionState.RUNNING,null,"fixture-v1");
            }
            public ProviderStreamEventPage getEvents(String providerId,String cursor,int limit) {
                return new ProviderStreamEventPage(Collections.emptyList(),cursor);
            }
            public StreamStopResult stop(String providerId) { throw new AssertionError("unexpected stop"); }
        };
        StreamSessionWorker recovery=worker(querying); recovery.start();
        try { waitFor(() -> queries.get()>0); } finally { recovery.close(); }
        assertEquals(StreamSessionState.RUNNING,owned(recoverable).getState());
    }

    @Test public void unconfirmedFeaturesKeepStartAndRemoteStopDisabled() throws Exception {
        f.streamSource("a","source_disabled",true);
        try { start(new StreamProviderFeatures(false,false,false,false)).start("a","disabled-key",
                "video-stream-analysis.v1","source_disabled",f.streamParameters()); fail(); }
        catch (AiRequestException expected) { assertEquals(ErrorCode.CAPABILITY_UNAVAILABLE,expected.getCode()); }
        StreamSession running=running("no-stop-key",new StreamProviderFeatures(true,true,false,true));
        try { new StopStreamSessionService(f.streamSessions,f.clock).stop(id(running),"a"); fail(); }
        catch (AiRequestException expected) { assertEquals(ErrorCode.CANCEL_NOT_SUPPORTED,expected.getCode()); }
        assertEquals(StreamSessionState.RUNNING,owned(running).getState());
    }

    private StartStreamSessionService start(StreamProviderFeatures features) {
        return new StartStreamSessionService(f.streamSessions,f.streamSources,f.capabilities,c -> features,f.clock);
    }
    private StreamProviderFeatures all() { return new StreamProviderFeatures(true,true,true,true); }
    private StreamSession running(String key,StreamProviderFeatures features) {
        if (!f.streamSources.findOwned("source_manual","a").isPresent()) f.streamSource("a","source_manual",true);
        StreamSession session=start(features).start("a",key,"video-stream-analysis.v1","source_manual",f.streamParameters()).getSession();
        StreamSession claimed=f.streamSessions.claimPending(id(session),0,"stream-token",Instant.now()).get();
        assertTrue(f.streamSessions.updateClaimed(id(session),claimed.getVersion(),claimed.getDispatchToken(),
                new StreamSessionUpdate(StreamSessionState.RUNNING,"provider-session",null,null,null,Instant.now())));
        return owned(session);
    }
    private StreamSessionWorker worker(StreamSessionProvider provider) {
        DefaultListableBeanFactory beans=new DefaultListableBeanFactory(); beans.registerSingleton("provider",provider);
        beans.registerSingleton("reader",(ProviderArtifactReader)(snapshot,artifact,limit) -> new ByteArrayInputStream(f.png));
        return new StreamSessionWorker(f.streamSessions,f.streamSources,f.streamEvents,
                beans.getBeanProvider(StreamSessionProvider.class),beans.getBeanProvider(ProviderArtifactReader.class),
                f.files,f.clock,1,10*1024*1024,10,org.jeecg.modules.ai.config.jobs.AiRuntimeMetrics.disabled());
    }
    private StreamSessionProvider provider(ThrowingStart start) {
        return new StreamSessionProvider() {
            public ProviderStreamSession start(ProviderStreamStartRequest request) throws ProviderException { return start.call(); }
            public ProviderStreamSession getSession(String id) { throw new AssertionError("unexpected query"); }
            public ProviderStreamEventPage getEvents(String id,String cursor,int limit) { throw new AssertionError("unexpected events"); }
            public StreamStopResult stop(String id) { throw new AssertionError("unexpected stop"); }
        };
    }
    private StreamEvent event(String id,String provider,long offset,String snapshot) {
        return new StreamEvent(id,provider,offset,f.clock.instant(),"person",new BigDecimal("0.8"),snapshot);
    }
    private StreamSession owned(StreamSession session) { return f.streamSessions.findOwned(id(session),"a").get(); }
    private String id(StreamSession session) { return session.getRequest().getSessionId(); }
    private void waitFor(Check check) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (!check.done() && System.nanoTime()<deadline) Thread.sleep(20);
        assertTrue("timed out",check.done());
    }
    private interface Check { boolean done() throws Exception; }
    private interface ThrowingStart { ProviderStreamSession call() throws ProviderException; }
}
