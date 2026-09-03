package org.jeecg.modules.ai.domain;

/**
 * Expected transfer metadata. Size/hash may be null only when the source does not provide them.
 * Names are display labels, never filesystem paths; caller supplies an independent positive byte limit.
 */
public final class ContentMetadata {
    private final String fileName;
    private final String mediaType;
    private final Long sizeBytes;
    private final String sha256;

    public ContentMetadata(
            String fileName,
            String mediaType,
            Long sizeBytes,
            String sha256) {
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMediaType() {
        return mediaType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }
}
