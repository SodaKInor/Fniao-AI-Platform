package org.jeecg.modules.ai.operations.config;

import org.jeecg.modules.ai.capability.port.CapabilityRepository;
import org.jeecg.modules.ai.image.port.InferenceProvider;
import org.jeecg.modules.ai.job.application.DispatchJobService;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.JobRecord;
import org.jeecg.modules.ai.job.domain.JobType;
import org.jeecg.modules.ai.job.domain.UnknownOperationReason;
import org.jeecg.modules.ai.job.port.JobRepository;
import org.jeecg.modules.ai.result.application.CollectResultService;
import org.jeecg.modules.ai.result.port.ProviderArtifactReader;
import org.jeecg.modules.ai.video.port.VideoAnalysisProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.jeecg.modules.ai.asset.application.AssetService;
import org.jeecg.modules.ai.job.persistence.repository.MyBatisJobRepository;

/** Database is the bounded durable queue. Only idle worker slots request dispatch candidates. */
public final class JobWorker implements AutoCloseable {
    private final Logger log=LoggerFactory.getLogger(JobWorker.class);
    private final ScheduledExecutorService scanner=Executors.newSingleThreadScheduledExecutor(r -> thread(r,"ai-04a-scan"));
    private final ExecutorService workers;
    private final Semaphore idle;
    private final JobRepository jobs;
    private final ObjectProvider<InferenceProvider> providers;
    private final ObjectProvider<VideoAnalysisProvider> videoProviders;
    private final ObjectProvider<ProviderArtifactReader> readers;
    private final AssetService assets;
    private final Clock clock;
    private final long maxOutput;
    private final CapabilityRepository capabilities;
    private final MyBatisJobRepository recoveryJobs;
    private final long recoveryLeaseMs;
    private final AiRuntimeMetrics metrics;
    private boolean scanFailed;

    public JobWorker(JobRepository jobs,ObjectProvider<InferenceProvider> providers,ObjectProvider<ProviderArtifactReader> readers,
                     AssetService assets,Clock clock,int parallelism,long maxOutput,CapabilityRepository capabilities) {
        this(jobs,null,providers,null,readers,assets,clock,parallelism,maxOutput,capabilities,
                TimeUnit.HOURS.toMillis(1),AiRuntimeMetrics.disabled());
    }
    public JobWorker(JobRepository jobs,MyBatisJobRepository recoveryJobs,ObjectProvider<InferenceProvider> providers,
                     ObjectProvider<VideoAnalysisProvider> videoProviders,ObjectProvider<ProviderArtifactReader> readers,
                     AssetService assets,Clock clock,int parallelism,long maxOutput,CapabilityRepository capabilities) {
        this(jobs,recoveryJobs,providers,videoProviders,readers,assets,clock,parallelism,maxOutput,capabilities,
                TimeUnit.HOURS.toMillis(1),AiRuntimeMetrics.disabled());
    }
    public JobWorker(JobRepository jobs,MyBatisJobRepository recoveryJobs,ObjectProvider<InferenceProvider> providers,
                     ObjectProvider<VideoAnalysisProvider> videoProviders,ObjectProvider<ProviderArtifactReader> readers,
                     AssetService assets,Clock clock,int parallelism,long maxOutput,CapabilityRepository capabilities,
                     long recoveryLeaseMs,AiRuntimeMetrics metrics) {
        if (recoveryLeaseMs<1) throw new IllegalArgumentException("Invalid recovery lease");
        this.jobs=jobs; this.recoveryJobs=recoveryJobs; this.providers=providers; this.videoProviders=videoProviders;
        this.readers=readers; this.assets=assets; this.clock=clock; this.maxOutput=maxOutput; this.capabilities=capabilities;
        this.recoveryLeaseMs=recoveryLeaseMs; this.metrics=metrics==null ? AiRuntimeMetrics.disabled() : metrics;
        idle=new Semaphore(parallelism);
        workers=new ThreadPoolExecutor(parallelism,parallelism,0,TimeUnit.MILLISECONDS,new SynchronousQueue<>(),
                r -> thread(r,"ai-04a-dispatch"),new ThreadPoolExecutor.AbortPolicy());
    }
    private static Thread thread(Runnable runnable,String name) { Thread thread=new Thread(runnable,name); thread.setDaemon(true); return thread; }
    public void start() { scanner.scheduleWithFixedDelay(this::scan,0,100,TimeUnit.MILLISECONDS); }
    private void scan() {
        try {
            recoverUncertain();
            InferenceProvider provider=providers.getIfAvailable();
            VideoAnalysisProvider video=videoProviders==null ? null : videoProviders.getIfAvailable();
            ProviderArtifactReader reader=readers.getIfAvailable();
            if (reader==null || idle.availablePermits()==0) return;
            recover(reader);
            for (JobRecord job:jobs.findPending(Math.min(100,idle.availablePermits()))) {
                if (!idle.tryAcquire()) break;
                try { workers.execute(() -> dispatch(job,provider,video,reader)); }
                catch (RejectedExecutionException e) { idle.release(); }
            }
            scanFailed=false;
        } catch (RuntimeException e) {
            if (!scanFailed) log.warn("AI pending scan unavailable ({})",e.getClass().getSimpleName());
            scanFailed=true;
        }
    }
    private void recover(ProviderArtifactReader reader) {
        if (recoveryJobs==null || idle.availablePermits()==0) return;
        Instant staleBefore=clock.instant().minusMillis(recoveryLeaseMs);
        for (JobRecord candidate:recoveryJobs.findFetchingResult(staleBefore,Math.min(100,idle.availablePermits()))) {
            if (!idle.tryAcquire()) break;
            java.util.Optional<JobRecord> claimed=recoveryJobs.claimFetchingResult(candidate.getRequest().getRequestId(),
                    candidate.getVersion(),java.util.UUID.randomUUID().toString(),staleBefore,clock.instant());
            if (!claimed.isPresent()) { idle.release(); continue; }
            try { workers.execute(() -> collect(claimed.get(),reader)); }
            catch (RejectedExecutionException e) { idle.release(); }
        }
    }
    private void recoverUncertain() {
        if (recoveryJobs==null) return;
        Instant now=clock.instant(),staleBefore=now.minusMillis(recoveryLeaseMs);
        for (JobRecord candidate:recoveryJobs.findUncertain(staleBefore,100)) {
            long started=System.nanoTime();
            boolean changed=recoveryJobs.markUncertainUnknown(candidate.getRequest().getRequestId(),candidate.getVersion(),
                    candidate.getDispatchToken(),staleBefore,now);
            String outcome=changed ? "unknown" : "race_lost";
            metrics.record(kind(candidate),"stale_reconcile",outcome,
                    changed ? ErrorCode.RESULT_UNKNOWN : null,System.nanoTime()-started);
            if (changed) log.warn("AI request {} stage stale_reconcile outcome=UNKNOWN reason={} durationMs={}",
                    candidate.getRequest().getRequestId(),UnknownOperationReason.PROVIDER_QUERY_UNAVAILABLE,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        }
    }
    private void collect(JobRecord job,ProviderArtifactReader reader) {
        long started=System.nanoTime();
        try { new CollectResultService(reader,assets,clock,maxOutput,capabilities).recover(jobs,job); }
        catch (RuntimeException e) { log.warn("AI result {} requires inspection ({})",
                job.getRequest().getRequestId(),e.getClass().getSimpleName()); }
        finally { record(job,"result_recovery",started); idle.release(); }
    }
    private void dispatch(JobRecord job,InferenceProvider provider,VideoAnalysisProvider video,ProviderArtifactReader reader) {
        long started=System.nanoTime();
        try {
            CollectResultService collector=new CollectResultService(reader,assets,clock,maxOutput,capabilities);
            new DispatchJobService(jobs,provider,video,assets,collector,clock).dispatch(job);
        } catch (RuntimeException e) {
            log.warn("AI request {} requires inspection ({})",job.getRequest().getRequestId(),e.getClass().getSimpleName());
        } finally { record(job,"dispatch",started); idle.release(); }
    }
    private void record(JobRecord original,String stage,long started) {
        JobRecord current=jobs.findOwned(original.getRequest().getRequestId(),original.getRequest().getOwnerId()).orElse(original);
        String outcome=current.getState().name().toLowerCase(java.util.Locale.ROOT);
        ErrorCode error=current.getError()==null ? null : current.getError().getCode();
        long elapsed=System.nanoTime()-started;
        metrics.record(kind(current),stage,outcome,error,elapsed);
        log.info("AI request {} stage {} outcome={} durationMs={}",current.getRequest().getRequestId(),stage,
                current.getState(),TimeUnit.NANOSECONDS.toMillis(elapsed));
    }
    private String kind(JobRecord job) {
        return job.getRequest().getJobType()==JobType.VIDEO_FILE_ANALYSIS ? "video" : "image";
    }
    public void close() { scanner.shutdownNow(); workers.shutdown(); }
}
