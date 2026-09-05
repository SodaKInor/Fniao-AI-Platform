package org.jeecg.modules.ai.job.domain;

import org.jeecg.modules.ai.image.domain.InferenceResult;
import org.jeecg.modules.ai.image.domain.ProviderResult;
import org.jeecg.modules.ai.video.domain.VideoProviderResult;
import org.jeecg.modules.ai.video.domain.VideoResult;

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
    private final VideoProviderResult videoProviderResult;
    private final InferenceResult result;
    private final VideoResult videoResult;
    private final JobError error;
    private final UnknownOperationReason unknownReason;
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
        this(
                request,
                state,
                version,
                dispatchToken,
                providerResult,
                null,
                result,
                null,
                error,
                null,
                updatedAt);
    }

    public JobRecord(
            JobRequest request,
            JobState state,
            long version,
            String dispatchToken,
            ProviderResult providerResult,
            VideoProviderResult videoProviderResult,
            InferenceResult result,
            VideoResult videoResult,
            JobError error,
            UnknownOperationReason unknownReason,
            Instant updatedAt) {
        this.request = request;
        this.state = state;
        this.version = version;
        this.dispatchToken = dispatchToken;
        this.providerResult = providerResult;
        this.videoProviderResult = videoProviderResult;
        this.result = result;
        this.videoResult = videoResult;
        this.error = error;
        this.unknownReason = unknownReason;
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
