package org.jeecg.modules.ai.config.jobs;

import java.time.Clock;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.jeecg.modules.ai.application.jobs.*;
import org.jeecg.modules.ai.application.assets.AssetService;
import org.jeecg.modules.ai.domain.JobRecord;
import org.jeecg.modules.ai.port.*;
import org.jeecg.modules.ai.persistence.repository.MyBatisJobRepository;

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
    private boolean scanFailed;

    public JobWorker(JobRepository jobs,ObjectProvider<InferenceProvider> providers,ObjectProvider<ProviderArtifactReader> readers,
                     AssetService assets,Clock clock,int parallelism,long maxOutput,CapabilityRepository capabilities) {
        this(jobs,null,providers,null,readers,assets,clock,parallelism,maxOutput,capabilities);
    }
    public JobWorker(JobRepository jobs,MyBatisJobRepository recoveryJobs,ObjectProvider<InferenceProvider> providers,
                     ObjectProvider<VideoAnalysisProvider> videoProviders,ObjectProvider<ProviderArtifactReader> readers,
                     AssetService assets,Clock clock,int parallelism,long maxOutput,CapabilityRepository capabilities) {
        this.jobs=jobs; this.recoveryJobs=recoveryJobs; this.providers=providers; this.videoProviders=videoProviders;
        this.readers=readers; this.assets=assets; this.clock=clock; this.maxOutput=maxOutput; this.capabilities=capabilities;
        idle=new Semaphore(parallelism);
        workers=new ThreadPoolExecutor(parallelism,parallelism,0,TimeUnit.MILLISECONDS,new SynchronousQueue<>(),
                r -> thread(r,"ai-04a-dispatch"),new ThreadPoolExecutor.AbortPolicy());
    }
    private static Thread thread(Runnable runnable,String name) { Thread thread=new Thread(runnable,name); thread.setDaemon(true); return thread; }
    public void start() { scanner.scheduleWithFixedDelay(this::scan,0,100,TimeUnit.MILLISECONDS); }
    private void scan() {
        try {
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
        for (JobRecord candidate:recoveryJobs.findFetchingResult(Math.min(100,idle.availablePermits()))) {
            if (!idle.tryAcquire()) break;
            java.util.Optional<JobRecord> claimed=recoveryJobs.claimFetchingResult(candidate.getRequest().getRequestId(),
                    candidate.getVersion(),java.util.UUID.randomUUID().toString(),clock.instant());
            if (!claimed.isPresent()) { idle.release(); continue; }
            try { workers.execute(() -> collect(claimed.get(),reader)); }
            catch (RejectedExecutionException e) { idle.release(); }
        }
    }
    private void collect(JobRecord job,ProviderArtifactReader reader) {
        try { new CollectResultService(reader,assets,clock,maxOutput,capabilities).recover(jobs,job); }
        catch (RuntimeException e) { log.warn("AI result {} requires inspection ({})",
                job.getRequest().getRequestId(),e.getClass().getSimpleName()); }
        finally { idle.release(); }
    }
    private void dispatch(JobRecord job,InferenceProvider provider,VideoAnalysisProvider video,ProviderArtifactReader reader) {
        try {
            CollectResultService collector=new CollectResultService(reader,assets,clock,maxOutput,capabilities);
            new DispatchJobService(jobs,provider,video,assets,collector,clock).dispatch(job);
        } catch (RuntimeException e) {
            log.warn("AI request {} requires inspection ({})",job.getRequest().getRequestId(),e.getClass().getSimpleName());
        } finally { idle.release(); }
    }
    public void close() { scanner.shutdownNow(); workers.shutdown(); }
}
