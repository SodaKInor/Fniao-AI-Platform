package org.jeecg.modules.ai.domain;

/** Provider start request with a configured source reference, never a browser-supplied URL. */
public final class ProviderStreamStartRequest {
    private final String sessionId;
    private final CapabilitySnapshot capability;
    private final String providerSourceRef;
    private final StreamParameters parameters;

    public ProviderStreamStartRequest(
            String sessionId,
            CapabilitySnapshot capability,
            String providerSourceRef,
            StreamParameters parameters) {
        this.sessionId = sessionId;
        this.capability = capability;
        this.providerSourceRef = providerSourceRef;
        this.parameters = parameters;
    }

    public String getSessionId() {
        return sessionId;
    }

    public CapabilitySnapshot getCapability() {
        return capability;
    }

    public String getProviderSourceRef() {
        return providerSourceRef;
    }

    public StreamParameters getParameters() {
        return parameters;
    }
}
