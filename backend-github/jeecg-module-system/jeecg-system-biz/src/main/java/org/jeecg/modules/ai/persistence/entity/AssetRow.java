package org.jeecg.modules.ai.persistence.entity;

/** Database shape; never returned through the API. */
public class AssetRow {
    public String assetId;
    public String ownerId;
    public String fileName;
    public String mediaType;
    public String storageKey;
    public long sizeBytes;
    public String sha256;
    public long createdAt;
    public long expiresAt;
}
