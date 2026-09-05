package org.jeecg.modules.ai.asset.domain;

import java.time.Instant;

/**
 * Durable private input or output metadata. Both asset record and stored bytes must exist.
 * Repository queries enforce owner scope; no anonymous URL alias.
 */
public final class Asset {
    private final String assetId;
    private final String ownerId;
    private final String fileName;
    private final String mediaType;
    private final StoredArtifact stored;
    private final Instant createdAt;
    private final Instant expiresAt;

    public Asset(
            String assetId,
            String ownerId,
            String fileName,
            String mediaType,
            StoredArtifact stored,
            Instant createdAt,
            Instant expiresAt) {
        this.assetId = assetId;
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.stored = stored;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getAssetId() {
        return assetId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMediaType() {
        return mediaType;
    }

    public StoredArtifact getStored() {
        return stored;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
