package org.jeecg.modules.ai.capability.application;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jeecg.modules.ai.capability.domain.Capability;
import org.jeecg.modules.ai.capability.port.CapabilityRepository;

/** Only local repository reads and supplied availability facts; no provider or persistence imports. */
public final class CapabilityQueryService {
    private final Supplier<CapabilityRepository> repository;
    private final Function<Capability, String> availability;
    private final long inputLimit;
    private final long outputLimit;

    public CapabilityQueryService(Supplier<CapabilityRepository> repository, Function<Capability, String> availability,
            long inputLimit, long outputLimit) {
        this.repository = repository; this.availability = availability;
        this.inputLimit = inputLimit; this.outputLimit = outputLimit;
    }

    public List<Capability> list(boolean mayInfer) {
        CapabilityRepository source = repository.get();
        if (source == null) throw new IllegalStateException("Capability repository is not ready");
        List<Capability> result = new ArrayList<>();
        for (Capability capability : source.list()) {
            result.add(effective(capability, mayInfer));
        }
        return result;
    }

    /** Shared policy for capability display and admission of a new submission. */
    public Capability effective(Capability capability, boolean mayInfer) {
        String reason = reason(capability, mayInfer);
        return new Capability(capability.getSnapshot(), capability.getDisplayName(), capability.isEnabled(),
                    reason.isEmpty(), capability.isSimulated(), reason, capability.getInputMediaTypes(),
                    Math.max(1, Math.min(capability.getMaxInputBytes(), inputLimit)),
                    Math.max(1, Math.min(capability.getMaxOutputBytes(), outputLimit)),
                    Math.max(0, Math.min(capability.getMaxWaitMillis(), 1500)));
    }

    private String reason(Capability capability, boolean mayInfer) {
        if (!capability.isEnabled()) return "能力已停用";
        if (!mayInfer) return "当前用户没有 AI 执行权限";
        String configured = availability.apply(capability);
        if (!configured.isEmpty()) return configured;
        if (!capability.isAvailable()) {
            return capability.getUnavailableReason() == null || capability.getUnavailableReason().isEmpty()
                    ? "能力尚未就绪" : capability.getUnavailableReason();
        }
        return "";
    }
}
