package org.jeecg.modules.ai.assetsjobs;

import org.jeecg.modules.ai.asset.domain.Asset;
import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.asset.port.AssetRepository;
import org.jeecg.modules.ai.image.domain.BoundingBox;
import org.jeecg.modules.ai.image.domain.Detection;
import org.jeecg.modules.ai.image.domain.DetectionData;
import org.jeecg.modules.ai.image.domain.DetectionParameters;
import org.jeecg.modules.ai.image.domain.ProviderResult;
import org.jeecg.modules.ai.image.port.InferenceProvider;
import org.jeecg.modules.ai.job.application.AiRequestException;
import org.jeecg.modules.ai.job.application.DispatchJobService;
import org.jeecg.modules.ai.job.application.JobQueryService;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.job.domain.IdempotencyConflictException;
import org.jeecg.modules.ai.job.domain.JobRecord;
import org.jeecg.modules.ai.job.domain.JobRequest;
import org.jeecg.modules.ai.job.domain.JobState;
import org.jeecg.modules.ai.job.domain.JobSubmission;
import org.jeecg.modules.ai.operations.config.JobWorker;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.result.domain.ProviderArtifact;
import org.jeecg.modules.ai.result.port.ProviderArtifactReader;

import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.jeecg.modules.ai.asset.application.AssetService;

public class WorkflowTest {
    private DbFixture f;
    @Before public void before() throws Exception { f=new DbFixture(); }
    @After public void after() throws Exception { f.close(); }

    @Test public void completeResultAndHistorySurviveProviderOffline() throws Exception {
        Asset input=f.input("a"); JobRecord job=f.submit(input,"success-key");
        f.dispatcher(f.provider(f.result(true)),f.reader()).dispatch(job);
        JobRecord result=f.get(job); assertEquals(JobState.SUCCEEDED,result.getState());
        assertEquals(1,f.calls.get()); assertEquals(1,f.reads.get()); assertEquals(1,f.closes.get());
        assertEquals(2,f.countFiles());
        assertTrue(result.getProviderResult()!=null);
        Asset output=f.query.resultAssets(result).get(0);
        assertEquals(Duration.ofDays(7),Duration.between(input.getCreatedAt(),input.getExpiresAt()));
        assertEquals(Duration.ofDays(30),Duration.between(output.getCreatedAt(),output.getExpiresAt()));
        try (InputStream stream=f.files.open(output.getAssetId(),"a")) { assertEquals(f.png.length,read(stream)); }
        for (int i=0;i<5;i++) assertEquals(JobState.SUCCEEDED,f.get(job).getState());
        assertEquals(1,f.calls.get()); assertEquals(1,f.reads.get());
        assertFalse(f.jobs.claimPending(job.getRequest().getRequestId(),result.getVersion(),"late",Instant.now()).isPresent());
    }

    @Test public void emptyResultIsSuccessWithoutFiles() throws Exception {
        JobRecord job=f.submit(f.input("a"),"empty-key");
        f.dispatcher(f.provider(f.result(false)),f.reader()).dispatch(job);
        assertEquals(JobState.SUCCEEDED,f.get(job).getState());
        assertTrue(f.get(job).getResult().getArtifactIds().isEmpty()); assertEquals(0,f.reads.get());
    }

    @Test public void shortWaitDoesNotCancelOrReplaySynchronousCall() throws Exception {
        JobRecord job=f.submit(f.input("a"),"waiting-key");
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            Future<?> result=worker.submit(() -> f.dispatcher(request -> {
                f.calls.incrementAndGet(); started.countDown();
                try { if (!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("test timed out"); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                return f.result(false);
            },f.reader()).dispatch(job));
            assertTrue(started.await(3,TimeUnit.SECONDS)); long start=System.nanoTime();
            JobRecord waiting=f.query.await(job.getRequest().getRequestId(),"a",100);
            assertEquals(JobState.WAITING,waiting.getState()); assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<500);
            assertFalse(result.isDone()); assertEquals(1,f.calls.get());
            assertEquals(job.getRequest().getRequestId(),f.submit(f.files.owned(job.getRequest().getInputAssetId(),"a"),"waiting-key").getRequest().getRequestId());
            release.countDown(); result.get(3,TimeUnit.SECONDS); assertEquals(JobState.SUCCEEDED,f.get(job).getState());
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    @Test public void responseLostBecomesUnknownAndExplicitRetryHasNewIdentity() throws Exception {
        Asset input=f.input("a"); JobRecord job=f.submit(input,"unknown-key");
        InferenceProvider lost=request -> { f.calls.incrementAndGet(); throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,ExecutionCertainty.UNKNOWN,"lost"); };
        DispatchJobService dispatcher=f.dispatcher(lost,f.reader()); dispatcher.dispatch(job);
        assertEquals(JobState.UNKNOWN,f.get(job).getState()); assertEquals(1,f.calls.get());
        dispatcher.dispatch(job); assertEquals(1,f.calls.get()); assertTrue(f.jobs.findPending(10).isEmpty());
        assertEquals(job.getRequest().getRequestId(),f.submit(input,"unknown-key").getRequest().getRequestId());
        JobRecord retry=f.submit.submit("a","new-retry-key","image-detection.v1",input.getAssetId(),f.parameters(),job.getRequest().getRequestId()).getJob();
        assertNotEquals(job.getRequest().getRequestId(),retry.getRequest().getRequestId());
        assertEquals(job.getRequest().getRequestId(),retry.getRequest().getRetryOfRequestId());
        assertEquals(JobState.UNKNOWN,f.get(job).getState());
        try { f.submit.submit("b","other-retry","image-detection.v1",input.getAssetId(),f.parameters(),job.getRequest().getRequestId()); fail(); }
        catch (AiRequestException expected) { assertEquals(ErrorCode.NOT_FOUND,expected.getCode()); }
    }

    @Test public void canonicalDuplicatesPersistPreciseParametersAndIgnoreLaterDisable() throws Exception {
        Asset input=f.input("a"); JobRecord original=f.submit(input,"canonical-key");
        JobSubmission same=f.submit.submit("a","canonical-key","image-detection.v1",input.getAssetId(),
                new DetectionParameters(new BigDecimal("0.5"),10,true),null);
        assertFalse(same.isCreated()); assertEquals(original.getRequest().getRequestId(),same.getJob().getRequest().getRequestId());
        try { f.submit.submit("a","canonical-key","image-detection.v1",input.getAssetId(),new DetectionParameters(BigDecimal.ONE,10,true),null); fail(); }
        catch (IdempotencyConflictException expected) { }
        f.sql.update("DELETE FROM ai_capability_binding");
        assertFalse(f.submit.submit("a","canonical-key","image-detection.v1",input.getAssetId(),f.parameters(),null).isCreated());
        try { f.submit(input,"disabled-key"); fail(); } catch (AiRequestException expected) { assertEquals(ErrorCode.CAPABILITY_UNAVAILABLE,expected.getCode()); }
        DetectionParameters precise=new DetectionParameters(new BigDecimal("0.100000000000000001"),10,true);
        JobRequest q=f.request("a","precision-key");
        JobRequest roundTrip=f.codec.request(f.codec.write(new JobRequest(q.getRequestId(),"a",q.getIdempotencyKey(),"digest","input",precise,q.getCapability(),null,true,q.getCreatedAt())));
        assertEquals(0,precise.getThreshold().compareTo(roundTrip.getParameters().getThreshold()));
    }

    @Test public void interruptedDownloadRetriesOnlySameArtifactAndCleansPartialFile() throws Exception {
        JobRecord job=f.submit(f.input("a"),"interrupt-key"); AtomicInteger attempts=new AtomicInteger(), closed=new AtomicInteger();
        ProviderArtifactReader reader=(snapshot,artifact,limit) -> {
            if (attempts.incrementAndGet()>1) return new ByteArrayInputStream(f.png);
            return new InputStream() {
                int position;
                public int read() throws IOException { if (position++>10) throw new IOException("interrupted"); return 1; }
                public void close() { closed.incrementAndGet(); }
            };
        };
        f.dispatcher(f.provider(f.result(true)),reader).dispatch(job);
        assertEquals(JobState.SUCCEEDED,f.get(job).getState()); assertEquals(1,f.calls.get()); assertEquals(2,attempts.get());
        assertEquals(1,closed.get()); assertEquals(2,f.countFiles());
    }

    @Test public void badHashCannotPublishOrSucceed() throws Exception {
        JobRecord job=f.submit(f.input("a"),"bad-hash-key");
        ProviderArtifact artifact=new ProviderArtifact("fixture:image",new ContentMetadata("out.png","image/png",(long)f.png.length,String.join("",Collections.nCopies(64,"0"))),Instant.now().plusSeconds(60));
        ProviderResult result=new ProviderResult(null,true,f.result(false).getData(),Collections.singletonList(artifact));
        f.dispatcher(f.provider(result),f.reader()).dispatch(job);
        assertEquals(JobState.FAILED,f.get(job).getState()); assertEquals(ErrorCode.ARTIFACT_TRANSFER,f.get(job).getError().getCode());
        assertEquals(3,f.reads.get()); assertEquals(3,f.closes.get()); assertEquals(1,f.calls.get()); assertEquals(1,f.countFiles());
        assertNotNull(f.get(job).getProviderResult()); assertNull(f.get(job).getResult());
    }

    @Test public void expiredAndRejectedReferencesNeverSucceed() throws Exception {
        JobRecord job=f.submit(f.input("a"),"expired-key");
        ProviderArtifact expired=new ProviderArtifact("fixture:expired",f.result(true).getArtifacts().get(0).getMetadata(),Instant.now().minusSeconds(1));
        f.dispatcher(f.provider(new ProviderResult(null,true,f.result(false).getData(),Collections.singletonList(expired))),f.reader()).dispatch(job);
        assertEquals(ErrorCode.ARTIFACT_EXPIRED,f.get(job).getError().getCode()); assertEquals(0,f.reads.get());
        JobRecord rejected=f.submit(f.input("a"),"rejected-key"); AtomicInteger attempts=new AtomicInteger();
        f.dispatcher(f.provider(f.result(true)),(snapshot,artifact,limit) -> {
            attempts.incrementAndGet(); throw new ProviderException(ErrorCode.ARTIFACT_TRANSFER,ExecutionCertainty.NOT_STARTED,"Origin or redirect denied by reader");
        }).dispatch(rejected);
        assertEquals(JobState.FAILED,f.get(rejected).getState()); assertEquals(3,attempts.get()); assertEquals(2,f.calls.get());
    }

    @Test public void metadataFailureCleansOrphanAndLostAcknowledgementPreservesCommittedFile() throws Exception {
        AtomicInteger attempts=new AtomicInteger();
        AssetRepository unreliable=new AssetRepository() {
            public Optional<Asset> findOwned(String id,String owner) { return f.assets.findOwned(id,owner); }
            public void insert(Asset asset) {
                int n=attempts.incrementAndGet(); if (n==1) throw new IllegalStateException("before commit");
                f.assets.insert(asset); if (n==2) throw new IllegalStateException("after commit");
            }
        };
        AssetService service=new AssetService(unreliable,f.store,f.clock,10*1024*1024,Duration.ofDays(7),Duration.ofDays(30));
        ContentMetadata metadata=new ContentMetadata("output.png","image/png",(long)f.png.length,null);
        try { service.collect("stable-output","a",metadata,new ByteArrayInputStream(f.png),10*1024*1024); fail(); }
        catch (IllegalStateException expected) { assertEquals(0,f.countFiles()); }
        Asset saved=service.collect("stable-output","a",metadata,new ByteArrayInputStream(f.png),10*1024*1024);
        assertEquals(1,f.countFiles()); assertTrue(f.assets.findOwned(saved.getAssetId(),"a").isPresent());
        assertEquals(saved.getStored().getStorageKey(),service.collect("stable-output","a",metadata,new ByteArrayInputStream(f.png),10*1024*1024).getStored().getStorageKey());
        assertEquals(1,f.countFiles());
    }

    @Test public void crossOwnerAndOversizedInputsAreRejectedBeforeProvider() throws Exception {
        Asset input=f.input("a");
        try { f.submit.submit("b","foreign-key","image-detection.v1",input.getAssetId(),f.parameters(),null); fail(); }
        catch (AiRequestException expected) { assertEquals(ErrorCode.NOT_FOUND,expected.getCode()); }
        try { f.files.open(input.getAssetId(),"b"); fail(); } catch (AiRequestException expected) { assertEquals(ErrorCode.NOT_FOUND,expected.getCode()); }
        try { f.files.upload("a",new ContentMetadata("large.png","image/png",11L*1024*1024,null),new ByteArrayInputStream(f.png)); fail(); }
        catch (AiRequestException expected) { assertEquals(ErrorCode.LIMIT_EXCEEDED,expected.getCode()); }
        assertEquals(0,f.calls.get()); assertTrue(f.jobs.findPending(10).isEmpty());
    }
    @Test public void realBackgroundWorkerUsesOneSlotAndContinuesAfterResponse() throws Exception {
        org.springframework.beans.factory.support.DefaultListableBeanFactory beans=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        AtomicInteger active=new AtomicInteger(), peak=new AtomicInteger();
        InferenceProvider provider=request -> {
            f.calls.incrementAndGet(); int current=active.incrementAndGet(); peak.updateAndGet(old -> Math.max(old,current));
            try { Thread.sleep(60); return f.result(false); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            finally { active.decrementAndGet(); }
        };
        beans.registerSingleton("provider",provider); beans.registerSingleton("reader",f.reader());
        org.jeecg.modules.ai.operations.config.JobWorker worker=new org.jeecg.modules.ai.operations.config.JobWorker(f.jobs,
                beans.getBeanProvider(InferenceProvider.class),beans.getBeanProvider(ProviderArtifactReader.class),f.files,f.clock,1,10*1024*1024,f.capabilities);
        List<JobRecord> pending=new ArrayList<>();
        for (int i=0;i<3;i++) pending.add(f.submit(f.input("a"),"worker-key-"+i));
        worker.start();
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while (pending.stream().anyMatch(j -> !JobQueryService.finished(f.get(j))) && System.nanoTime()<deadline) Thread.sleep(20);
            for (JobRecord job:pending) assertEquals(JobState.SUCCEEDED,f.get(job).getState());
            assertEquals(3,f.calls.get()); assertEquals(1,peak.get());
        } finally { worker.close(); }
    }

    @Test public void invalidNormalizedDetectionCannotPublishSuccess() throws Exception {
        String longLabel=String.join("",Collections.nCopies(121,"x"));
        List<Detection> invalid=Arrays.asList(new Detection(longLabel,0.8,new BoundingBox(0,0,1,1)),
                new Detection("label",Double.NaN,new BoundingBox(0,0,1,1)),
                new Detection("label",0.8,new BoundingBox(0.8,0,0.5,1)));
        int index=0;
        for (Detection detection:invalid) {
            JobRecord job=f.submit(f.input("a"),"invalid-output-"+(index++));
            ProviderResult response=new ProviderResult(null,true,new DetectionData("detection.v1",16,16,Collections.singletonList(detection)),Collections.emptyList());
            f.dispatcher(f.provider(response),f.reader()).dispatch(job);
            assertEquals(JobState.UNKNOWN,f.get(job).getState());
            assertEquals(ErrorCode.PROVIDER_PROTOCOL,f.get(job).getError().getCode()); assertNull(f.get(job).getResult());
        }
        assertEquals(3,f.calls.get()); assertEquals(0,f.reads.get());
    }

    private int read(InputStream input) throws IOException { int count=0; while (input.read()!=-1) count++; return count; }
}
