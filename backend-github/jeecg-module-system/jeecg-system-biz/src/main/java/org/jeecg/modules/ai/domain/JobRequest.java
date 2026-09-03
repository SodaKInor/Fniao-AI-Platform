package org.jeecg.modules.ai.domain;

import java.time.Instant;

/**
 * Validated submission identity and immutable snapshot, persisted before HTTP acceptance.
 * Digest excludes the server-selected binding/version, includes retryOfRequestId; see SEMANTICS.md.
 * retryOfRequestId links a new explicit attempt; it never changes the original record.
 */
public final class JobRequest {
    private final String requestId;
    private final String ownerId;
    private final String idempotencyKey;
    private final String requestDigest;
    private final String inputAssetId;
    private final DetectionParameters parameters;
    private final CapabilitySnapshot capability;
    private final String retryOfRequestId;
    private final boolean simulated;
    private final Instant createdAt;

    public JobRequest(
            String requestId,
            String ownerId,
            String idempotencyKey,
            String requestDigest,
            String inputAssetId,
            DetectionParameters parameters,
            CapabilitySnapshot capability,
            String retryOfRequestId,
            boolean simulated,
            Instant createdAt) {
        this.requestId = requestId;
        this.ownerId = ownerId;
        this.idempotencyKey = idempotencyKey;
        this.requestDigest = requestDigest;
        this.inputAssetId = inputAssetId;
        this.parameters = parameters;
        this.capability = capability;
        this.retryOfRequestId = retryOfRequestId;
        this.simulated = simulated;
        this.createdAt = createdAt;
    }

    public String getRequestId() {
        return requestId;
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

    public String getInputAssetId() {
        return inputAssetId;
    }

    public DetectionParameters getParameters() {
        return parameters;
    }

    public CapabilitySnapshot getCapability() {
        return capability;
    }

    public String getRetryOfRequestId() {
        return retryOfRequestId;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
