package org.jeecg.modules.ai.job.domain;

import org.jeecg.modules.ai.image.domain.InferenceResult;
import org.jeecg.modules.ai.image.domain.ProviderResult;
import org.jeecg.modules.ai.video.domain.VideoProviderResult;
import org.jeecg.modules.ai.video.domain.VideoResult;

import java.time.Instant;

/**
 * Complete replacement of mutable state at a version boundary, not a partial merge.
 * Caller preserves the checkpoint when continuing collection; absent values clear their fields.
 */
public final class JobUpdate {
    private final JobState state;
    private final ProviderResult providerResult;
    private final VideoProviderResult videoProviderResult;
    private final InferenceResult result;
    private final VideoResult videoResult;
    private final JobError error;
    private final UnknownOperationReason unknownReason;
    private final Instant updatedAt;

    public JobUpdate(
            JobState state,
            ProviderResult providerResult,
            InferenceResult result,
            JobError error,
            Instant updatedAt) {
        this(state, providerResult, null, result, null, error, null, updatedAt);
    }

    public JobUpdate(
            JobState state,
            ProviderResult providerResult,
            VideoProviderResult videoProviderResult,
            InferenceResult result,
            VideoResult videoResult,
            JobError error,
            UnknownOperationReason unknownReason,
            Instant updatedAt) {
        this.state = state;
        this.providerResult = providerResult;
        this.videoProviderResult = videoProviderResult;
        this.result = result;
        this.videoResult = videoResult;
        this.error = error;
        this.unknownReason = unknownReason;
        this.updatedAt = updatedAt;
    }

    public JobState getState() {
        return state;
    }

    public ProviderResult getProviderResult() {
        return providerResult;
    }

    public VideoProviderResult getVideoProviderResult() {
        return videoProviderResult;
    }

    public InferenceResult getResult() {
        return result;
    }

    public VideoResult getVideoResult() {
        return videoResult;
    }

    public JobError getError() {
        return error;
    }

    public UnknownOperationReason getUnknownReason() {
        return unknownReason;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
