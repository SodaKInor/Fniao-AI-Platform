package org.jeecg.modules.ai.domain;

import java.time.Instant;

/** Durable local stream state; provider identity and cursor never reach browser DTOs. */
public final class StreamSession {
    private final StreamSessionRequest request;
    private final StreamSessionState state;
    private final long version;
    private final String dispatchToken;
    private final String providerSessionId;
    private final String cursor;
    private final UnknownOperationReason unknownReason;
    private final JobError error;
    private final Instant updatedAt;

    public StreamSession(
            StreamSessionRequest request,
            StreamSessionState state,
            long version,
            String dispatchToken,
            String providerSessionId,
            String cursor,
            UnknownOperationReason unknownReason,
            JobError error,
            Instant updatedAt) {
        this.request = request;
        this.state = state;
        this.version = version;
        this.dispatchToken = dispatchToken;
        this.providerSessionId = providerSessionId;
        this.cursor = cursor;
        this.unknownReason = unknownReason;
        this.error = error;
        this.updatedAt = updatedAt;
    }

    public StreamSessionRequest getRequest() {
        return request;
    }

    public StreamSessionState getState() {
        return state;
    }

    public long getVersion() {
        return version;
    }

    public String getDispatchToken() {
        return dispatchToken;
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
