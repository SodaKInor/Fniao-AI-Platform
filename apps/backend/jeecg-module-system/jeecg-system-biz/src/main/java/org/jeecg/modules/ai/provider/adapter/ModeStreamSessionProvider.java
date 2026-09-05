package org.jeecg.modules.ai.provider.adapter;

import java.util.function.Supplier;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.stream.domain.ProviderStreamEventPage;
import org.jeecg.modules.ai.stream.domain.ProviderStreamSession;
import org.jeecg.modules.ai.stream.domain.ProviderStreamStartRequest;
import org.jeecg.modules.ai.stream.domain.StreamStopResult;
import org.jeecg.modules.ai.stream.port.StreamSessionProvider;

/** Per-operation stream gates keep unconfirmed query/event/stop methods independently disabled. */
public final class ModeStreamSessionProvider implements StreamSessionProvider {
    private final Supplier<String> startReason;
    private final Supplier<String> sessionReason;
    private final Supplier<String> eventReason;
    private final Supplier<String> stopReason;
    private final StreamSessionProvider remote;

    public ModeStreamSessionProvider(
            Supplier<String> startReason,
            Supplier<String> sessionReason,
            Supplier<String> eventReason,
            Supplier<String> stopReason,
            StreamSessionProvider remote) {
        this.startReason = startReason;
        this.sessionReason = sessionReason;
        this.eventReason = eventReason;
        this.stopReason = stopReason;
        this.remote = remote;
    }

    @Override public ProviderStreamSession start(ProviderStreamStartRequest request) throws ProviderException {
        require(startReason); return remote.start(request);
    }

    @Override public ProviderStreamSession getSession(String providerSessionId) throws ProviderException {
        require(sessionReason); return remote.getSession(providerSessionId);
    }

    @Override public ProviderStreamEventPage getEvents(String providerSessionId, String cursor, int limit)
            throws ProviderException {
        require(eventReason); return remote.getEvents(providerSessionId, cursor, limit);
    }

    @Override public StreamStopResult stop(String providerSessionId) throws ProviderException {
        require(stopReason); return remote.stop(providerSessionId);
    }

    private void require(Supplier<String> reasonSupplier) throws ProviderException {
        String reason = reasonSupplier.get();
        if (!reason.isEmpty()) throw ProviderRequestChecks.unavailable(reason);
        if (remote == null) throw ProviderRequestChecks.unavailable("真实实时流协议尚未确认");
    }
}
