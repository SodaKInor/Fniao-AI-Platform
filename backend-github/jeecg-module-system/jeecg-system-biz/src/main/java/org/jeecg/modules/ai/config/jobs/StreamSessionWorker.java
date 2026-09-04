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

/**
 * Bounded stream lifecycle worker. A persisted worker/action token plus a recovery lease prevents
 * a second process from replaying start/stop while the first process may still be in remote I/O.
 */
public final class StreamSessionWorker implements AutoCloseable {
    private final Logger log=LoggerFactory.getLogger(StreamSessionWorker.class);
    private final ScheduledExecutorService scanner=Executors.newSingleThreadScheduledExecutor(r -> thread(r,"ai-stream-scan"));
    private final ExecutorService workers;
    private final Semaphore idle;
    private final Set<String> inFlight=ConcurrentHashMap.newKeySet();
    private final MyBatisStreamSessionRepository sessions;
    private final StreamSourceRepository sources;
    private final StreamEventRepository events;
    private final ObjectProvider<StreamSessionProvider> providers;
    private final ObjectProvider<ProviderArtifactReader> readers;
    private final AssetService assets;
    private final Clock clock;
    private final long outputLimit;
    private final long recoveryLeaseMs;
    private final AiRuntimeMetrics metrics;
    private final String workerPrefix="w"+UUID.randomUUID().toString().replace("-","");

    public StreamSessionWorker(MyBatisStreamSessionRepository sessions,StreamSourceRepository sources,
            StreamEventRepository events,ObjectProvider<StreamSessionProvider> providers,
            ObjectProvider<ProviderArtifactReader> readers,AssetService assets,Clock clock,int parallelism,long outputLimit) {
        this(sessions,sources,events,providers,readers,assets,clock,parallelism,outputLimit,
                TimeUnit.HOURS.toMillis(1),AiRuntimeMetrics.disabled());
    }
    public StreamSessionWorker(MyBatisStreamSessionRepository sessions,StreamSourceRepository sources,
            StreamEventRepository events,ObjectProvider<StreamSessionProvider> providers,
            ObjectProvider<ProviderArtifactReader> readers,AssetService assets,Clock clock,int parallelism,long outputLimit,
            long recoveryLeaseMs,AiRuntimeMetrics metrics) {
        if (recoveryLeaseMs<1) throw new IllegalArgumentException("Invalid recovery lease");
        this.sessions=sessions; this.sources=sources; this.events=events; this.providers=providers;
        this.readers=readers; this.assets=assets; this.clock=clock; this.outputLimit=outputLimit;
        this.recoveryLeaseMs=recoveryLeaseMs; this.metrics=metrics==null ? AiRuntimeMetrics.disabled() : metrics;
        idle=new Semaphore(parallelism); workers=Executors.newFixedThreadPool(parallelism,r -> thread(r,"ai-stream-work"));
    }
    private static Thread thread(Runnable run,String name) { Thread thread=new Thread(run,name); thread.setDaemon(true); return thread; }
    public void start() { scanner.scheduleWithFixedDelay(this::scan,0,250,TimeUnit.MILLISECONDS); }

    private void scan() {
        try {
            StreamSessionProvider provider=providers.getIfAvailable(); ProviderArtifactReader reader=readers.getIfAvailable();
            if (idle.availablePermits()==0) return;
            long now=clock.millis();
            for (StreamSession candidate:sessions.findRecoverable(Math.min(100,Math.max(1,idle.availablePermits())))) {
                if (idle.availablePermits()==0) break;
                Work work=select(candidate,now);
                if (work==null || inFlight.contains(id(candidate))) continue;
                if (needsProvider(work) && provider==null) continue;
                if (needsReader(work) && reader==null) continue;
                claimAndSubmit(candidate,work,provider,reader);
            }
            if (provider==null || reader==null) return;
            for (StreamSession candidate:sessions.findPending(Math.min(100,Math.max(1,idle.availablePermits())))) {
                if (idle.availablePermits()==0) break;
                claimPendingAndSubmit(candidate,provider,reader);
            }
        } catch (RuntimeException e) { log.warn("AI stream scan unavailable ({})",e.getClass().getSimpleName()); }
    }

    private Work select(StreamSession session,long now) {
        long age=Math.max(0,now-session.getUpdatedAt().toEpochMilli());
        boolean mine=mine(session.getDispatchToken());
        if (session.getState()==StreamSessionState.STARTING) {
            if (session.getProviderSessionId()==null) return age>=recoveryLeaseMs ? Work.AMBIGUOUS_START : null;
            long due=mine ? session.getRequest().getParameters().getPollIntervalMillis() : recoveryLeaseMs;
            return age>=due ? Work.QUERY : null;
        }
        if (session.getState()==StreamSessionState.RUNNING) {
            long due=mine ? session.getRequest().getParameters().getPollIntervalMillis() : recoveryLeaseMs;
            return age>=due ? Work.QUERY : null;
        }
        if (session.getState()==StreamSessionState.STOP_REQUESTED) {
            if (session.getProviderSessionId()==null)
                return age>=recoveryLeaseMs ? Work.STOP_RECOVERY : null;
            if (mine && !stopAttempted(session.getDispatchToken())) return Work.STOP;
            return age>=recoveryLeaseMs ? Work.STOP_RECOVERY : null;
        }
        return null;
    }

    private void claimAndSubmit(StreamSession candidate,Work work,StreamSessionProvider provider,ProviderArtifactReader reader) {
        if (!idle.tryAcquire()) return;
        Optional<StreamSession> claimed=sessions.claimRecoverable(id(candidate),candidate.getVersion(),candidate.getDispatchToken(),
                token(work),clock.instant());
        if (!claimed.isPresent()) { idle.release(); return; }
        submit(claimed.get(),work,provider,reader);
    }
    private void claimPendingAndSubmit(StreamSession candidate,StreamSessionProvider provider,ProviderArtifactReader reader) {
        if (!idle.tryAcquire()) return;
        Optional<StreamSession> claimed=sessions.claimPending(id(candidate),candidate.getVersion(),token(Work.START),clock.instant());
        if (!claimed.isPresent()) { idle.release(); return; }
        submit(claimed.get(),Work.START,provider,reader);
    }
    private void submit(StreamSession session,Work work,StreamSessionProvider provider,ProviderArtifactReader reader) {
        String id=id(session); inFlight.add(id);
        try { workers.execute(() -> process(session,work,provider,reader)); }
        catch (RejectedExecutionException e) { inFlight.remove(id); idle.release(); }
    }

    private void process(StreamSession session,Work work,StreamSessionProvider provider,ProviderArtifactReader reader) {
        long started=System.nanoTime();
        try {
            switch (work) {
                case START: start(session,provider,reader); break;
                case QUERY: reconcile(session,provider,reader); break;
                case STOP: stop(session,provider); break;
                case AMBIGUOUS_START:
                    unknown(session,UnknownOperationReason.PROVIDER_RESPONSE_LOST,ErrorCode.RESULT_UNKNOWN); break;
                case STOP_RECOVERY: recoverStop(session,provider); break;
                default: throw new IllegalStateException("Unsupported stream work");
            }
        } catch (RuntimeException e) {
            log.warn("AI stream session {} requires inspection ({})",id(session),e.getClass().getSimpleName());
        } finally {
            record(session,stage(work),started); inFlight.remove(id(session)); idle.release();
        }
    }

    private void start(StreamSession session,StreamSessionProvider provider,ProviderArtifactReader reader) {
        StreamSource source=sources.findOwned(session.getRequest().getStreamSourceId(),session.getRequest().getOwnerId()).orElse(null);
        if (source==null || !source.isEnabled() || source.getProviderSourceRef()==null) {
            fail(session,ErrorCode.CAPABILITY_UNAVAILABLE,"Stream source is unavailable"); return;
        }
        try {
            ProviderStreamSession result=provider.start(new ProviderStreamStartRequest(id(session),
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
        long started=System.nanoTime(); ErrorCode error=null; String outcome="running";
        try {
            ProviderStreamEventPage page=provider.getEvents(session.getProviderSessionId(),session.getCursor(),
                    session.getRequest().getParameters().getMaxEventsPerPoll());
            if (!new StreamEventCollector(events,reader,assets,clock,outputLimit).collect(session,page)) outcome="race_lost";
        } catch (ProviderException e) {
            error=e.getErrorCode(); outcome="failed";
            log.warn("AI stream events {} unavailable ({})",id(session),e.getErrorCode());
        } finally { metrics.record("stream","events",outcome,error,System.nanoTime()-started); }
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
    private void recoverStop(StreamSession session,StreamSessionProvider provider) {
        if (provider==null || session.getProviderSessionId()==null
                || !session.getRequest().getProviderFeatures().isSessionQuery()) {
            unknown(session,UnknownOperationReason.STOP_CONFIRMATION_UNKNOWN,ErrorCode.RESULT_UNKNOWN); return;
        }
        try {
            ProviderStreamSession result=provider.getSession(session.getProviderSessionId());
            if (result!=null && session.getProviderSessionId().equals(result.getProviderSessionId())
                    && result.getState()==StreamSessionState.STOPPED) {
                move(session,StreamSessionState.STOPPED,session.getProviderSessionId(),session.getCursor(),null,null); return;
            }
            unknown(session,UnknownOperationReason.STOP_CONFIRMATION_UNKNOWN,ErrorCode.RESULT_UNKNOWN);
        } catch (ProviderException e) {
            unknown(session,UnknownOperationReason.STOP_CONFIRMATION_UNKNOWN,e.getErrorCode());
        }
    }

    private StreamSession owned(StreamSession value) { return sessions.findOwned(id(value),
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
        if (!sessions.updateClaimed(id(old),old.getVersion(),old.getDispatchToken(),
                new StreamSessionUpdate(state,providerId,cursor,reason,error,clock.instant())))
            throw new IllegalStateException("Stream claim no longer current");
    }
    private void record(StreamSession original,String stage,long started) {
        StreamSession current=sessions.findOwned(id(original),original.getRequest().getOwnerId()).orElse(original);
        ErrorCode error=current.getError()==null ? null : current.getError().getCode();
        long elapsed=System.nanoTime()-started;
        metrics.record("stream",stage,current.getState().name().toLowerCase(Locale.ROOT),error,elapsed);
        log.info("AI stream session {} stage {} outcome={} durationMs={}",id(current),stage,current.getState(),
                TimeUnit.NANOSECONDS.toMillis(elapsed));
    }
    private boolean needsProvider(Work work) { return work==Work.START || work==Work.QUERY || work==Work.STOP; }
    private boolean needsReader(Work work) { return work==Work.START || work==Work.QUERY; }
    private boolean mine(String token) { return token!=null && token.startsWith(workerPrefix+"_"); }
    private boolean stopAttempted(String token) { return token!=null && token.contains("_stop_"); }
    private String token(Work work) {
        String action=work==Work.START ? "start" : work==Work.STOP ? "stop" : work==Work.QUERY ? "query"
                : work==Work.STOP_RECOVERY ? "stop_recovery" : "recover";
        return workerPrefix+"_"+action+"_"+UUID.randomUUID().toString().replace("-","");
    }
    private String stage(Work work) {
        return work==Work.START ? "start" : work==Work.QUERY ? "query" : work==Work.STOP ? "stop"
                : work==Work.STOP_RECOVERY ? "stop_recovery" : "stale_reconcile";
    }
    private String id(StreamSession session) { return session.getRequest().getSessionId(); }
    public void close() { scanner.shutdownNow(); workers.shutdown(); }
    private enum Work { START,QUERY,STOP,AMBIGUOUS_START,STOP_RECOVERY }
}
