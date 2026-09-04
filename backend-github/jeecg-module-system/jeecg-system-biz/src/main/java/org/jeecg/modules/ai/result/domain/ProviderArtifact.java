package org.jeecg.modules.ai.result.domain;

import org.jeecg.modules.ai.asset.domain.ContentMetadata;

import java.time.Instant;

/**
 * Opaque provider artifact reference, interpreted only by the matching adapter/reader.
 * Never expose to the browser or treat as a local filesystem path. May expire before local retention.
 */
public final class ProviderArtifact {
    private final String reference;
    private final ContentMetadata metadata;
    private final Instant expiresAt;

    public ProviderArtifact(
            String reference,
            ContentMetadata metadata,
            Instant expiresAt) {
        this.reference = reference;
        this.metadata = metadata;
        this.expiresAt = expiresAt;
    }

    public String getReference() {
        return reference;
    }

    public ContentMetadata getMetadata() {
        return metadata;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
