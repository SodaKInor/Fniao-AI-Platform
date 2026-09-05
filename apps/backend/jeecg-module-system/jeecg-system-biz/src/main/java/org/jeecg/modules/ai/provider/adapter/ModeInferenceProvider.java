package org.jeecg.modules.ai.provider.adapter;

import org.jeecg.modules.ai.image.domain.ProviderRequest;
import org.jeecg.modules.ai.image.domain.ProviderResult;
import org.jeecg.modules.ai.job.domain.ProviderException;

import java.util.function.Supplier;
import org.jeecg.modules.ai.image.port.InferenceProvider;

/** Remote dispatch exists only when configuration explicitly supplies an approved provider implementation. */
public final class ModeInferenceProvider implements InferenceProvider {
    private final Supplier<String> mode;
    private final Supplier<String> unavailableReason;
    private final InferenceProvider mock;
    private final InferenceProvider remote;

    public ModeInferenceProvider(Supplier<String> mode, Supplier<String> unavailableReason, InferenceProvider mock) {
        this(mode, unavailableReason, mock, null);
    }

    public ModeInferenceProvider(
            Supplier<String> mode,
            Supplier<String> unavailableReason,
            InferenceProvider mock,
            InferenceProvider remote) {
        this.mode = mode; this.unavailableReason = unavailableReason; this.mock = mock;
        this.remote = remote;
    }

    @Override public ProviderResult infer(ProviderRequest request) throws ProviderException {
        String reason = unavailableReason.get();
        if (!reason.isEmpty()) throw ProviderRequestChecks.unavailable(reason);
        if ("mock".equals(mode.get())) return mock.infer(request);
        if ("remote".equals(mode.get()) && remote != null) return remote.infer(request);
        throw ProviderRequestChecks.unavailable("真实服务协议尚未确认");
    }
}
