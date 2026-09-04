package org.jeecg.modules.ai.domain;

import java.time.Instant;

/** Immutable local stream identity persisted before any provider start call. */
public final class StreamSessionRequest {
    private final String sessionId;
    private final String ownerId;
    private final String idempotencyKey;
    private final String requestDigest;
    private final String streamSourceId;
    private final CapabilitySnapshot capability;
    private final StreamProviderFeatures providerFeatures;
    private final StreamParameters parameters;
    private final Instant createdAt;

    public StreamSessionRequest(
            String sessionId,
            String ownerId,
            String idempotencyKey,
            String requestDigest,
            String streamSourceId,
            CapabilitySnapshot capability,
            StreamProviderFeatures providerFeatures,
            StreamParameters parameters,
            Instant createdAt) {
        this.sessionId = sessionId;
        this.ownerId = ownerId;
        this.idempotencyKey = idempotencyKey;
        this.requestDigest = requestDigest;
        this.streamSourceId = streamSourceId;
        this.capability = capability;
        this.providerFeatures = providerFeatures;
        this.parameters = parameters;
        this.createdAt = createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public String getStreamSourceId() {
        return streamSourceId;
    }

    public CapabilitySnapshot getCapability() {
        return capability;
    }

    public StreamProviderFeatures getProviderFeatures() {
        return providerFeatures;
    }

    public StreamParameters getParameters() {
        return parameters;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
