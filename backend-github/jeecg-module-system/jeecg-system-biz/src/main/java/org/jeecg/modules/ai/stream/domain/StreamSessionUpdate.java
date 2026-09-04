package org.jeecg.modules.ai.stream.domain;

import org.jeecg.modules.ai.job.domain.JobError;
import org.jeecg.modules.ai.job.domain.UnknownOperationReason;

import java.time.Instant;

/** Complete replacement at a version boundary; STOPPED requires provider confirmation. */
public final class StreamSessionUpdate {
    private final StreamSessionState state;
    private final String providerSessionId;
    private final String cursor;
    private final UnknownOperationReason unknownReason;
    private final JobError error;
    private final Instant updatedAt;

    public StreamSessionUpdate(
            StreamSessionState state,
            String providerSessionId,
            String cursor,
            UnknownOperationReason unknownReason,
            JobError error,
            Instant updatedAt) {
        this.state = state;
        this.providerSessionId = providerSessionId;
        this.cursor = cursor;
        this.unknownReason = unknownReason;
        this.error = error;
        this.updatedAt = updatedAt;
    }

    public StreamSessionState getState() {
        return state;
    }

    public String getProviderSessionId() {
        return providerSessionId;
    }

    public String getCursor() {
        return cursor;
    }

    public UnknownOperationReason getUnknownReason() {
        return unknownReason;
    }

    public JobError getError() {
        return error;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
