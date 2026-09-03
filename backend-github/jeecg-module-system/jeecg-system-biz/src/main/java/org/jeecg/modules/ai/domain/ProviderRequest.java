package org.jeecg.modules.ai.domain;

/**
 * One already-claimed local request. InferenceProvider opens input at most once and closes it.
 * requestId is correlation only; it does not promise remote idempotency. No user-supplied endpoint.
 */
public final class ProviderRequest {
    private final String requestId;
    private final CapabilitySnapshot capability;
    private final DetectionParameters parameters;
    private final ContentMetadata inputMetadata;
    private final ContentSource input;

    public ProviderRequest(
            String requestId,
            CapabilitySnapshot capability,
            DetectionParameters parameters,
            ContentMetadata inputMetadata,
            ContentSource input) {
        this.requestId = requestId;
        this.capability = capability;
        this.parameters = parameters;
        this.inputMetadata = inputMetadata;
        this.input = input;
    }

    public String getRequestId() {
        return requestId;
    }

    public CapabilitySnapshot getCapability() {
        return capability;
    }

    public DetectionParameters getParameters() {
        return parameters;
    }

    public ContentMetadata getInputMetadata() {
        return inputMetadata;
    }

    public ContentSource getInput() {
        return input;
    }
}
