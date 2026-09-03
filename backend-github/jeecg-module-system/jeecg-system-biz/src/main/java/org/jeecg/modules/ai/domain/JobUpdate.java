package org.jeecg.modules.ai.domain;

import java.time.Instant;

/**
 * Complete replacement of mutable state at a version boundary, not a partial merge.
 * Caller preserves the checkpoint when continuing collection; absent values clear their fields.
 */
public final class JobUpdate {
    private final JobState state;
    private final ProviderResult providerResult;
    private final InferenceResult result;
    private final JobError error;
    private final Instant updatedAt;

    public JobUpdate(
            JobState state,
            ProviderResult providerResult,
            InferenceResult result,
            JobError error,
            Instant updatedAt) {
        this.state = state;
        this.providerResult = providerResult;
        this.result = result;
        this.error = error;
        this.updatedAt = updatedAt;
    }

    public JobState getState() {
        return state;
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
