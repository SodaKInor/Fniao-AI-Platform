package org.jeecg.modules.ai.assetsjobs;

import java.io.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import static org.junit.Assert.*;
import org.jeecg.modules.ai.application.jobs.*;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;

public class VideoWorkflowTest {
    private DbFixture f;
    @Before public void before() throws Exception { f=new DbFixture(); }
    @After public void after() throws Exception { f.close(); }

    @Test public void videoSubmissionIsIdempotentAndPersistsCompleteTimeline() throws Exception {
        Asset input=f.videoInput("a");
        JobRecord job=f.submit.submitVideo("a","video-success","video-file-analysis.v1",input.getAssetId(),
                f.videoParameters(),null).getJob();
        JobSubmission same=f.submit.submitVideo("a","video-success","video-file-analysis.v1",input.getAssetId(),
                new VideoParameters(new BigDecimal("0.5"),1000,20,true,true),null);
        assertFalse(same.isCreated()); assertEquals(job.getRequest().getRequestId(),same.getJob().getRequest().getRequestId());
        try {
            f.submit.submitVideo("a","video-success","video-file-analysis.v1",input.getAssetId(),
                    new VideoParameters(new BigDecimal("0.6"),1000,20,true,true),null); fail();
        } catch (IdempotencyConflictException expected) { }

        AtomicInteger calls=new AtomicInteger(); VideoAnalysisProvider provider=request -> {
            calls.incrementAndGet();
            assertEquals("WAITING",f.sql.queryForObject("SELECT state FROM ai_job WHERE request_id=?",String.class,request.getRequestId()));
            try (InputStream bytes=request.getInput().openStream()) { while (bytes.read()!=-1) { } }
            catch (IOException e) { throw new AssertionError(e); }
            return result(request.getRequestId());
        };
        dispatcher(provider,reader()).dispatch(job);
        JobRecord saved=f.get(job); assertEquals(JobState.SUCCEEDED,saved.getState());
        assertEquals(JobType.VIDEO_FILE_ANALYSIS,saved.getRequest().getJobType());
        assertEquals(2,saved.getVideoResult().getEvents().size());
        assertEquals(1200,saved.getVideoResult().getEvents().get(1).getOffsetMillis());
        assertEquals(1,saved.getVideoResult().getSnapshotAssetIds().size());
        assertNotNull(saved.getVideoResult().getAnnotatedVideoAssetId());
        assertEquals(2,f.query.resultAssets(saved).size()); assertEquals(1,calls.get());
        for (Asset artifact:f.query.resultAssets(saved)) f.files.owned(artifact.getAssetId(),"a");
        assertEquals(3,f.countFiles());
    }

    @Test public void responseLossIsUnknownAndNeverTransparentlyReplayed() throws Exception {
        JobRecord job=videoJob("video-unknown"); AtomicInteger calls=new AtomicInteger();
        VideoAnalysisProvider lost=request -> {
            calls.incrementAndGet();
            throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,ExecutionCertainty.UNKNOWN,"response lost");
        };
        DispatchJobService dispatcher=dispatcher(lost,reader()); dispatcher.dispatch(job); dispatcher.dispatch(job);
        JobRecord saved=f.get(job); assertEquals(JobState.UNKNOWN,saved.getState());
        assertEquals(UnknownOperationReason.PROVIDER_RESPONSE_LOST,saved.getUnknownReason());
        assertNull(saved.getVideoResult()); assertEquals(1,calls.get());
    }

    @Test public void fetchingRecoveryCollectsArtifactsWithoutInvokingInferenceAgain() throws Exception {
        JobRecord job=videoJob("video-recovery"); String token="recover-token";
        JobRecord claim=f.jobs.claimPending(job.getRequest().getRequestId(),job.getVersion(),token,Instant.now()).get();
        assertTrue(f.jobs.updateClaimed(job.getRequest().getRequestId(),claim.getVersion(),token,
                new JobUpdate(JobState.WAITING,null,null,null,null,null,null,Instant.now())));
        JobRecord waiting=f.get(job); VideoProviderResult checkpoint=result(job.getRequest().getRequestId());
        assertTrue(f.jobs.updateClaimed(job.getRequest().getRequestId(),waiting.getVersion(),token,
                new JobUpdate(JobState.FETCHING_RESULT,null,checkpoint,null,null,null,null,Instant.now())));
        JobRecord fetching=f.get(job); assertEquals(JobState.FETCHING_RESULT,fetching.getState());
        new CollectResultService(reader(),f.files,f.clock,512L*1024*1024,f.capabilities).recover(f.jobs,fetching);
        assertEquals(JobState.SUCCEEDED,f.get(job).getState()); assertEquals(0,f.calls.get());
        assertEquals(2,f.reads.get());
    }

    @Test public void onlyPendingVideoJobsCanBeCancelledLocally() throws Exception {
        CancelJobService cancel=new CancelJobService(f.jobs,f.clock); JobRecord pending=videoJob("video-cancel");
        assertEquals(JobState.CANCELLED,cancel.cancel(pending.getRequest().getRequestId(),"a").getState());
        assertEquals(JobState.CANCELLED,cancel.cancel(pending.getRequest().getRequestId(),"a").getState());
        JobRecord dispatched=videoJob("video-dispatched");
        assertTrue(f.jobs.claimPending(dispatched.getRequest().getRequestId(),0,"claimed",Instant.now()).isPresent());
        try { cancel.cancel(dispatched.getRequest().getRequestId(),"a"); fail(); }
        catch (AiRequestException expected) { assertEquals(ErrorCode.CANCEL_NOT_SUPPORTED,expected.getCode()); }
        assertEquals(JobState.DISPATCHING,f.get(dispatched).getState());
    }

    @Test public void invalidVideoContentAndProviderEventsCannotPublishSuccess() throws Exception {
        try {
            f.files.upload("a",new ContentMetadata("fake.mp4","video/mp4",(long)f.png.length,null),
                    new ByteArrayInputStream(f.png)); fail();
        } catch (AiRequestException expected) { assertEquals(ErrorCode.UNSUPPORTED_MEDIA,expected.getCode()); }
        JobRecord job=videoJob("video-invalid-event");
        VideoAnalysisProvider invalid=request -> new VideoProviderResult(request.getRequestId(),true,
                Collections.singletonList(new ProviderVideoEvent("bad.event",0,"",BigDecimal.ONE,null)),null);
        dispatcher(invalid,reader()).dispatch(job);
        assertEquals(JobState.UNKNOWN,f.get(job).getState());
        assertEquals(ErrorCode.PROVIDER_PROTOCOL,f.get(job).getError().getCode());
    }

    private JobRecord videoJob(String key) {
        Asset input=f.videoInput("a");
        return f.submit.submitVideo("a",key,"video-file-analysis.v1",input.getAssetId(),f.videoParameters(),null).getJob();
    }
    private DispatchJobService dispatcher(VideoAnalysisProvider provider,ProviderArtifactReader reader) {
        return new DispatchJobService(f.jobs,null,provider,f.files,
                new CollectResultService(reader,f.files,f.clock,512L*1024*1024,f.capabilities),f.clock);
    }
    private ProviderArtifactReader reader() {
        return (snapshot,artifact,limit) -> {
            f.reads.incrementAndGet(); byte[] value="video/mp4".equals(artifact.getMetadata().getMediaType()) ? f.mp4 : f.png;
            return new ByteArrayInputStream(value) { public void close() throws IOException { f.closes.incrementAndGet(); super.close(); } };
        };
    }
    private VideoProviderResult result(String requestId) {
        ProviderArtifact snapshot=new ProviderArtifact("fixture:snapshot",
                new ContentMetadata("snapshot.png","image/png",(long)f.png.length,null),f.clock.instant().plusSeconds(120));
        ProviderArtifact annotated=new ProviderArtifact("fixture:annotated",
                new ContentMetadata("annotated.mp4","video/mp4",(long)f.mp4.length,null),f.clock.instant().plusSeconds(120));
        List<ProviderVideoEvent> events=Arrays.asList(new ProviderVideoEvent("event_1",0,"person",new BigDecimal("0.9"),snapshot),
                new ProviderVideoEvent("event_2",1200,"vehicle",null,null));
        return new VideoProviderResult(requestId,true,events,annotated);
    }
}
