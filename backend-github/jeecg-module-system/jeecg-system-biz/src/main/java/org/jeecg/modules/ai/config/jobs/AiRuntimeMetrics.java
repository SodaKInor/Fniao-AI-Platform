package org.jeecg.modules.ai.config.jobs;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jeecg.modules.ai.domain.ErrorCode;
import org.jeecg.modules.ai.persistence.repository.MyBatisJobRepository;
import org.jeecg.modules.ai.persistence.repository.MyBatisStreamSessionRepository;

/** Low-cardinality operational metrics. Request/session IDs belong in logs, never metric tags. */
public final class AiRuntimeMetrics {
    private final MeterRegistry registry;
    private final AtomicBoolean jobsBound=new AtomicBoolean();
    private final AtomicBoolean streamsBound=new AtomicBoolean();

    public AiRuntimeMetrics(MeterRegistry registry) { this.registry=registry; }
    public static AiRuntimeMetrics disabled() { return new AiRuntimeMetrics(null); }

    public void bindJobs(MyBatisJobRepository jobs) {
        if (registry==null || !jobsBound.compareAndSet(false,true)) return;
        Gauge.builder("wgai.ai.queue.size",jobs,r -> r.pendingCount()).tag("kind","job").register(registry);
        Gauge.builder("wgai.ai.inflight.size",jobs,r -> r.activeCount()).tag("kind","job").register(registry);
    }
    public void bindStreams(MyBatisStreamSessionRepository sessions) {
        if (registry==null || !streamsBound.compareAndSet(false,true)) return;
        Gauge.builder("wgai.ai.queue.size",sessions,r -> r.pendingCount()).tag("kind","stream").register(registry);
        Gauge.builder("wgai.ai.inflight.size",sessions,r -> r.activeCount()).tag("kind","stream").register(registry);
    }
    public void record(String kind,String stage,String outcome,ErrorCode error,long elapsedNanos) {
        if (registry==null) return;
        String safeKind=kind(kind),safeStage=stage(stage),safeOutcome=outcome(outcome);
        registry.timer("wgai.ai.operation.duration","kind",safeKind,"stage",safeStage,"outcome",safeOutcome)
                .record(Math.max(0,elapsedNanos),TimeUnit.NANOSECONDS);
        registry.counter("wgai.ai.operations","kind",safeKind,"stage",safeStage,"outcome",safeOutcome).increment();
        if (error!=null) registry.counter("wgai.ai.errors","kind",safeKind,"stage",safeStage,
                "error",error.name()).increment();
    }
    public void streamEvents(String outcome,int count) {
        if (registry!=null && count>0)
            registry.counter("wgai.ai.stream.events","outcome",eventOutcome(outcome)).increment(count);
    }
    private String kind(String value) {
        return "video".equals(value) || "stream".equals(value) ? value : "image";
    }
    private String stage(String value) {
        switch (value) {
            case "dispatch": case "result_recovery": case "stale_reconcile": case "start":
            case "query": case "events": case "stop": case "stop_recovery": return value;
            default: return "other";
        }
    }
    private String outcome(String value) {
        switch (value) {
            case "pending": case "running": case "succeeded": case "failed": case "unknown":
            case "stopped": case "race_lost": case "rejected": return value;
            default: return "other";
        }
    }
    private String eventOutcome(String value) { return "duplicate".equals(value) ? "duplicate" : "inserted"; }
}
