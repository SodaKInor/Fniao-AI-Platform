package org.jeecg.modules.ai.asset.port;

import java.io.IOException;
import java.io.InputStream;
import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.asset.domain.StoredArtifact;

/**
 * Private byte storage only; no ownership or job-state decisions.
 * write borrows input: caller closes it. Bound actual bytes, check declared length/hash when present,
 * compute actual SHA-256 and publish only a complete file; clean partial writes on every failure.
 * open returns a stream the caller must close; storageKey must be validated and sandboxed.
 * delete is idempotent for orphan cleanup and never triggers provider/model deletion.
 * Application persists metadata and completes jobs only after write returns successfully.
 */
public interface ArtifactStore {
    StoredArtifact write(ContentMetadata expected, InputStream input, long maxBytes) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
