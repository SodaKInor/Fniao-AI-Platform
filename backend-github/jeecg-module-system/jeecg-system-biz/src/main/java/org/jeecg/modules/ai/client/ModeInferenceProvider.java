package org.jeecg.modules.ai.client;

import java.util.function.Supplier;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.InferenceProvider;

/** There is deliberately no configuration flag that promotes an unconfirmed wire draft. */
public final class ModeInferenceProvider implements InferenceProvider {
    private final Supplier<String> mode;
    private final Supplier<String> unavailableReason;
    private final InferenceProvider mock;

    public ModeInferenceProvider(Supplier<String> mode, Supplier<String> unavailableReason, InferenceProvider mock) {
        this.mode = mode; this.unavailableReason = unavailableReason; this.mock = mock;
    }

    @Override public ProviderResult infer(ProviderRequest request) throws ProviderException {
        String reason = unavailableReason.get();
        if (!reason.isEmpty()) throw ProviderRequestChecks.unavailable(reason);
        if ("mock".equals(mode.get())) return mock.infer(request);
        throw ProviderRequestChecks.unavailable("真实服务协议尚未确认");
    }
}
