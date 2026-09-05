package org.jeecg.modules.ai.result.port;

import java.io.InputStream;
import org.jeecg.modules.ai.capability.domain.CapabilitySnapshot;
import org.jeecg.modules.ai.result.domain.ProviderArtifact;
import org.jeecg.modules.ai.job.domain.ProviderException;

/**
 * Open a bounded response stream from the snapshot-selected provider, not an arbitrary URL.
 * Reader validates reference/expiry and allowed origin/redirect policy. On successful return,
 * caller owns and closes the stream; reader closes partially opened resources on failure.
 * The returned stream closes the network response too. Read failures surface as IOException.
 * No inference call or repository updates are permitted here.
 */
public interface ProviderArtifactReader {
    InputStream open(CapabilitySnapshot snapshot, ProviderArtifact artifact, long maxBytes)
            throws ProviderException;
}
