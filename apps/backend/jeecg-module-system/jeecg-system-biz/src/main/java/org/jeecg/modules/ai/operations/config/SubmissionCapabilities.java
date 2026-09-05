package org.jeecg.modules.ai.operations.config;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jeecg.modules.ai.capability.application.CapabilityQueryService;
import org.jeecg.modules.ai.capability.domain.Capability;
import org.jeecg.modules.ai.capability.port.CapabilityRepository;

/** Admission-only view: old jobs and result collection retain their stored binding. */
public final class SubmissionCapabilities implements CapabilityRepository {
    private final CapabilityRepository bindings;
    private final Supplier<CapabilityQueryService> policies;

    public SubmissionCapabilities(CapabilityRepository bindings, Supplier<CapabilityQueryService> policies) {
        this.bindings = bindings;
        this.policies = policies;
    }

    @Override public Optional<Capability> find(String code) {
        CapabilityQueryService policy = policies.get();
        return policy == null ? Optional.empty() : bindings.find(code).map(c -> policy.effective(c, true));
    }

    @Override public List<Capability> list() {
        CapabilityQueryService policy = policies.get();
        if (policy == null) return Collections.emptyList();
        return bindings.list().stream().map(c -> policy.effective(c, true)).collect(Collectors.toList());
    }
}
