package org.jeecg.modules.ai.domain;

/** Claimed upload-video request; provider owns and closes the opened input stream. */
public final class VideoProviderRequest {
    private final String requestId;
    private final CapabilitySnapshot capability;
    private final VideoParameters parameters;
    private final ContentMetadata inputMetadata;
    private final ContentSource input;

    public VideoProviderRequest(
            String requestId,
            CapabilitySnapshot capability,
            VideoParameters parameters,
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

    public VideoParameters getParameters() {
        return parameters;
    }

    public ContentMetadata getInputMetadata() {
        return inputMetadata;
    }

    public ContentSource getInput() {
        return input;
    }
}
