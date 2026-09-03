package org.jeecg.modules.ai.domain;

import java.time.Instant;

/**
 * Durable local state. Nullable token/checkpoint/result/error are state-dependent; see SEMANTICS.md.
 * version increases on every successful compare-and-set. Final records cannot be overwritten.
 */
public final class JobRecord {
    private final JobRequest request;
    private final JobState state;
    private final long version;
    private final String dispatchToken;
    private final ProviderResult providerResult;
    private final InferenceResult result;
    private final JobError error;
    private final Instant updatedAt;

    public JobRecord(
            JobRequest request,
            JobState state,
            long version,
            String dispatchToken,
            ProviderResult providerResult,
            InferenceResult result,
            JobError error,
            Instant updatedAt) {
        this.request = request;
        this.state = state;
        this.version = version;
        this.dispatchToken = dispatchToken;
        this.providerResult = providerResult;
        this.result = result;
        this.error = error;
        this.updatedAt = updatedAt;
    }

    public JobRequest getRequest() {
        return request;
    }

    public JobState getState() {
        return state;
    }

    public long getVersion() {
        return version;
    }

    public String getDispatchToken() {
        return dispatchToken;
    }

    public ProviderResult getProviderResult() {
        return providerResult;
    }

    public InferenceResult getResult() {
        return result;
    }

    public JobError getError() {
        return error;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
