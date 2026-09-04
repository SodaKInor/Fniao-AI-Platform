package org.jeecg.modules.ai.asset.domain;

/**
 * Fully written private file. Storage computes size/hash; storageKey never reaches API DTOs.
 */
public final class StoredArtifact {
    private final String storageKey;
    private final long sizeBytes;
    private final String sha256;

    public StoredArtifact(
            String storageKey,
            long sizeBytes,
            String sha256) {
        this.storageKey = storageKey;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }
}
