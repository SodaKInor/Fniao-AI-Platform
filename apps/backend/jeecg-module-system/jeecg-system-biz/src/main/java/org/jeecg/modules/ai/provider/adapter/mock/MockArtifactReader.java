package org.jeecg.modules.ai.provider.adapter.mock;

import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.capability.domain.CapabilitySnapshot;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.result.domain.ProviderArtifact;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.util.Base64;
import org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks;
import org.jeecg.modules.ai.result.port.ProviderArtifactReader;

/** Stable synthetic image; never reads a provider URL or an input storage path. */
public final class MockArtifactReader implements ProviderArtifactReader {
    static final String REFERENCE = "/artifacts/mock-annotated-v1";
    static final String HASH = "6a6d02559b8c8014f8840fe7faeb3c550fca2bce0303355ae07f0722089dca4d";
    private static final byte[] IMAGE = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAFklEQVR4nGOQ2yJHEmIY1TCqYfhqAACYkfABU1D2KwAAAABJRU5ErkJggg==");
    private final Clock clock;

    public MockArtifactReader(Clock clock) { this.clock = clock; }

    static ProviderArtifact descriptor(Clock clock) {
        return new ProviderArtifact(REFERENCE, new ContentMetadata("simulated.png", "image/png", (long) IMAGE.length, HASH),
                clock.instant().plusSeconds(3600));
    }

    @Override public InputStream open(CapabilitySnapshot snapshot, ProviderArtifact artifact, long maxBytes) throws ProviderException {
        if (!ProviderRequestChecks.binding(snapshot, "mock", "mock-v1") || artifact == null
                || !REFERENCE.equals(artifact.getReference()) || artifact.getMetadata() == null
                || !HASH.equals(artifact.getMetadata().getSha256()) || maxBytes < IMAGE.length
                || !Long.valueOf(IMAGE.length).equals(artifact.getMetadata().getSizeBytes())
                || !"image/png".equals(artifact.getMetadata().getMediaType())) {
            throw ProviderRequestChecks.unavailable("Invalid simulated artifact binding");
        }
        if (artifact.getExpiresAt() == null || !artifact.getExpiresAt().isAfter(clock.instant())) {
            throw new ProviderException(ErrorCode.ARTIFACT_EXPIRED, ExecutionCertainty.UNKNOWN, "Simulated artifact expired");
        }
        return new ByteArrayInputStream(IMAGE);
    }
}
