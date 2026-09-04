package org.jeecg.modules.ai.config.jobs;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.*;
import org.springframework.beans.factory.ObjectProvider;
import org.jeecg.modules.ai.application.assets.AssetService;
import org.jeecg.modules.ai.application.streams.StreamEventCollector;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;
import org.jeecg.modules.ai.persistence.repository.MyBatisStreamSessionRepository;

/** Bounded stream lifecycle worker; database claims prevent duplicate start or stop dispatch. */
public final class StreamSessionWorker implements AutoCloseable {
    private final Logger log=LoggerFactory.getLogger(StreamSessionWorker.class);
    private final ScheduledExecutorService scanner=Executors.newSingleThreadScheduledExecutor(r -> thread(r,"ai-stream-scan"));
    private final ExecutorService workers;
    private final Semaphore idle;
    private final MyBatisStreamSessionRepository sessions;
    private final StreamSourceRepository sources;
    private final StreamEventRepository events;
    private final ObjectProvider<StreamSessionProvider> providers;
    private final ObjectProvider<ProviderArtifactReader> readers;
    private final AssetService assets;
    private final Clock clock;
    private final long outputLimit;

    public StreamSessionWorker(MyBatisStreamSessionRepository sessions,StreamSourceRepository sources,
            StreamEventRepository events,ObjectProvider<StreamSessionProvider> providers,
            ObjectProvider<ProviderArtifactReader> readers,AssetService assets,Clock clock,int parallelism,long outputLimit) {
        this.sessions=sessions; this.sources=sources; this.events=events; this.providers=providers;
        this.readers=readers; this.assets=assets; this.clock=clock; this.outputLimit=outputLimit;
        idle=new Semaphore(parallelism); workers=Executors.newFixedThreadPool(parallelism,r -> thread(r,"ai-stream-work"));
    }
    private static Thread thread(Runnable run,String name) { Thread thread=new Thread(run,name); thread.setDaemon(true); return thread; }
    public void start() { scanner.scheduleWithFixedDelay(this::scan,0,250,TimeUnit.MILLISECONDS); }

    private void scan() {
        try {
            StreamSessionProvider provider=providers.getIfAvailable(); ProviderArtifactReader reader=readers.getIfAvailable();
            if (provider==null || reader==null || idle.availablePermits()==0) return;
            for (StreamSession candidate:sessions.findRecoverable(Math.min(100,Math.max(1,idle.availablePermits())))) {
                if (idle.availablePermits()==0) break;
                if (candidate.getState()==StreamSessionState.RUNNING
                        && clock.millis()-candidate.getUpdatedAt().toEpochMilli()
                        < candidate.getRequest().getParameters().getPollIntervalMillis()) continue;
                Optional<StreamSession> claimed=sessions.claimRecoverable(candidate.getRequest().getSessionId(),
                        candidate.getVersion(),candidate.getDispatchToken(),UUID.randomUUID().toString(),clock.instant());
                if (claimed.isPresent()) submit(claimed.get(),provider,reader);
            }
            for (StreamSession candidate:sessions.findPending(Math.min(100,Math.max(1,idle.availablePermits())))) {
                if (idle.availablePermits()==0) break;
                Optional<StreamSession> claimed=sessions.claimPending(candidate.getRequest().getSessionId(),candidate.getVersion(),
                        UUID.randomUUID().toString(),clock.instant());
                if (claimed.isPresent()) submit(claimed.get(),provider,reader);
            }
        } catch (RuntimeException e) { log.warn("AI stream scan unavailable ({})",e.getClass().getSimpleName()); }
    }
    private void submit(StreamSession session,StreamSessionProvider provider,ProviderArtifactReader reader) {
        if (!idle.tryAcquire()) return;
        try { workers.execute(() -> process(session,provider,reader)); }
        catch (RejectedExecutionException e) { idle.release(); }
    }

    private void process(StreamSession session,StreamSessionProvider provider,ProviderArtifactReader reader) {
        try {
            if (session.getState()==StreamSessionState.STARTING && session.getProviderSessionId()==null)
                start(session,provider,reader);
            else if (session.getState()==StreamSessionState.STOP_REQUESTED) stop(session,provider);
            else reconcile(session,provider,reader);
        } catch (RuntimeException e) {
            log.warn("AI stream session {} requires inspection ({})",session.getRequest().getSessionId(),e.getClass().getSimpleName());
        } finally { idle.release(); }
    }

    private void start(StreamSession session,StreamSessionProvider provider,ProviderArtifactReader reader) {
        StreamSource source=sources.findOwned(session.getRequest().getStreamSourceId(),session.getRequest().getOwnerId()).orElse(null);
        if (source==null || !source.isEnabled() || source.getProviderSourceRef()==null) {
            fail(session,ErrorCode.CAPABILITY_UNAVAILABLE,"Stream source is unavailable"); return;
        }
        try {
            ProviderStreamSession result=provider.start(new ProviderStreamStartRequest(session.getRequest().getSessionId(),
                    session.getRequest().getCapability(),source.getProviderSourceRef(),session.getRequest().getParameters()));
            if (result==null || result.getProviderSessionId()==null
                    || !(result.getState()==StreamSessionState.STARTING || result.getState()==StreamSessionState.RUNNING)) {
                unknown(session,UnknownOperationReason.PROVIDER_STATE_UNRECOGNIZED,ErrorCode.PROVIDER_PROTOCOL); return;
            }
            move(session,result.getState(),result.getProviderSessionId(),result.getCursor(),null,null);
            StreamSession current=owned(session); if (current.getState()==StreamSessionState.RUNNING) poll(current,provider,reader);
        } catch (ProviderException e) {
            if (e.getCertainty()==ExecutionCertainty.UNKNOWN)
                unknown(session,UnknownOperationReason.PROVIDER_RESPONSE_LOST,e.getErrorCode());
            else fail(session,e.getErrorCode(),"Provider stream start did not begin");
        }
    }

    private void reconcile(StreamSession session,StreamSessionProvider provider,ProviderArtifactReader reader) {
        if (!session.getRequest().getProviderFeatures().isSessionQuery() || session.getProviderSessionId()==null) {
            unknown(session,UnknownOperationReason.PROVIDER_QUERY_UNAVAILABLE,ErrorCode.RESULT_UNKNOWN); return;
        }
        try {
            ProviderStreamSession result=provider.getSession(session.getProviderSessionId());
            if (result==null || !session.getProviderSessionId().equals(result.getProviderSessionId())) {
                unknown(session,UnknownOperationReason.PROVIDER_STATE_UNRECOGNIZED,ErrorCode.PROVIDER_PROTOCOL); return;
            }
            StreamSessionState state=result.getState();
            if (!(state==StreamSessionState.STARTING || state==StreamSessionState.RUNNING || state==StreamSessionState.STOPPED)) {
                unknown(session,UnknownOperationReason.PROVIDER_STATE_UNRECOGNIZED,ErrorCode.PROVIDER_PROTOCOL); return;
            }
            move(session,state,result.getProviderSessionId(),result.getCursor()==null ? session.getCursor() : result.getCursor(),null,null);
            StreamSession current=owned(session); if (current.getState()==StreamSessionState.RUNNING) poll(current,provider,reader);
        } catch (ProviderException e) {
            unknown(session,UnknownOperationReason.PROVIDER_QUERY_UNAVAILABLE,e.getErrorCode());
        }
    }

    private void poll(StreamSession session,StreamSessionProvider provider,ProviderArtifactReader reader) {
        try {
            ProviderStreamEventPage page=provider.getEvents(session.getProviderSessionId(),session.getCursor(),
                    session.getRequest().getParameters().getMaxEventsPerPoll());
            new StreamEventCollector(events,reader,assets,clock,outputLimit).collect(session,page);
        } catch (ProviderException e) {
            log.warn("AI stream events {} unavailable ({})",session.getRequest().getSessionId(),e.getErrorCode());
        }
    }

    private void stop(StreamSession session,StreamSessionProvider provider) {
        try {
            StreamStopResult result=provider.stop(session.getProviderSessionId());
            if (result!=null && session.getProviderSessionId().equals(result.getProviderSessionId())
                    && result.getOutcome()==StreamStopOutcome.CONFIRMED_STOPPED)
                move(session,StreamSessionState.STOPPED,session.getProviderSessionId(),session.getCursor(),null,null);
            else unknown(session,UnknownOperationReason.STOP_CONFIRMATION_UNKNOWN,ErrorCode.CANCEL_NOT_SUPPORTED);
        } catch (ProviderException e) {
            unknown(session,UnknownOperationReason.STOP_CONFIRMATION_UNKNOWN,e.getErrorCode());
        }
    }
    private StreamSession owned(StreamSession value) { return sessions.findOwned(value.getRequest().getSessionId(),
            value.getRequest().getOwnerId()).orElseThrow(() -> new IllegalStateException("Stream session disappeared")); }
    private void fail(StreamSession session,ErrorCode code,String message) {
        move(session,StreamSessionState.FAILED,session.getProviderSessionId(),session.getCursor(),null,
                new JobError(code,message,false));
    }
    private void unknown(StreamSession session,UnknownOperationReason reason,ErrorCode code) {
        move(session,StreamSessionState.UNKNOWN,session.getProviderSessionId(),session.getCursor(),reason,
                new JobError(code,"Provider stream state is not confirmed",false));
    }
    private void move(StreamSession old,StreamSessionState state,String providerId,String cursor,
            UnknownOperationReason reason,JobError error) {
        if (!sessions.updateClaimed(old.getRequest().getSessionId(),old.getVersion(),old.getDispatchToken(),
                new StreamSessionUpdate(state,providerId,cursor,reason,error,clock.instant())))
            throw new IllegalStateException("Stream claim no longer current");
    }
    public void close() { scanner.shutdownNow(); workers.shutdown(); }
}
