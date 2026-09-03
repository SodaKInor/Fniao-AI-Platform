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

/** Database is the bounded durable queue. Only idle worker slots request dispatch candidates. */
public final class JobWorker implements AutoCloseable {
    private final Logger log=LoggerFactory.getLogger(JobWorker.class);
    private final ScheduledExecutorService scanner=Executors.newSingleThreadScheduledExecutor(r -> thread(r,"ai-04a-scan"));
    private final ExecutorService workers;
    private final Semaphore idle;
    private final JobRepository jobs;
    private final ObjectProvider<InferenceProvider> providers;
    private final ObjectProvider<ProviderArtifactReader> readers;
    private final AssetService assets;
    private final Clock clock;
    private final long maxOutput;
    private final CapabilityRepository capabilities;
    private boolean scanFailed;

    public JobWorker(JobRepository jobs,ObjectProvider<InferenceProvider> providers,ObjectProvider<ProviderArtifactReader> readers,
                     AssetService assets,Clock clock,int parallelism,long maxOutput,CapabilityRepository capabilities) {
        this.jobs=jobs; this.providers=providers; this.readers=readers; this.assets=assets; this.clock=clock; this.maxOutput=maxOutput; this.capabilities=capabilities;
        idle=new Semaphore(parallelism);
        workers=new ThreadPoolExecutor(parallelism,parallelism,0,TimeUnit.MILLISECONDS,new SynchronousQueue<>(),
                r -> thread(r,"ai-04a-dispatch"),new ThreadPoolExecutor.AbortPolicy());
    }
    private static Thread thread(Runnable runnable,String name) { Thread thread=new Thread(runnable,name); thread.setDaemon(true); return thread; }
    public void start() { scanner.scheduleWithFixedDelay(this::scan,0,100,TimeUnit.MILLISECONDS); }
    private void scan() {
        try {
            InferenceProvider provider=providers.getIfAvailable(); ProviderArtifactReader reader=readers.getIfAvailable();
            if (provider==null || reader==null || idle.availablePermits()==0) return;
            for (JobRecord job:jobs.findPending(Math.min(100,idle.availablePermits()))) {
                if (!idle.tryAcquire()) break;
                try { workers.execute(() -> dispatch(job,provider,reader)); }
                catch (RejectedExecutionException e) { idle.release(); }
            }
            scanFailed=false;
        } catch (RuntimeException e) {
            if (!scanFailed) log.warn("AI pending scan unavailable ({})",e.getClass().getSimpleName());
            scanFailed=true;
        }
    }
    private void dispatch(JobRecord job,InferenceProvider provider,ProviderArtifactReader reader) {
        try {
            CollectResultService collector=new CollectResultService(reader,assets,clock,maxOutput,capabilities);
            new DispatchJobService(jobs,provider,assets,collector,clock).dispatch(job);
        } catch (RuntimeException e) {
            log.warn("AI request {} requires inspection ({})",job.getRequest().getRequestId(),e.getClass().getSimpleName());
        } finally { idle.release(); }
    }
    public void close() { scanner.shutdownNow(); workers.shutdown(); }
}
