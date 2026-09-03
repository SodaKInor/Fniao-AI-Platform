package org.jeecg.modules.ai.client;

import java.io.InputStream;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.ProviderArtifactReader;

/** Collection uses the saved binding; disabling new inference does not invalidate past mock output. */
public final class ModeArtifactReader implements ProviderArtifactReader {
    private final ProviderArtifactReader mock;
    private final long outputLimit;

    public ModeArtifactReader(ProviderArtifactReader mock, long outputLimit) { this.mock = mock; this.outputLimit = outputLimit; }

    @Override public InputStream open(CapabilitySnapshot snapshot, ProviderArtifact artifact, long maxBytes) throws ProviderException {
        if (ProviderRequestChecks.binding(snapshot, "mock", "mock-v1")) {
            return mock.open(snapshot, artifact, Math.min(maxBytes, outputLimit));
        }
        throw ProviderRequestChecks.unavailable("真实成果下载协议尚未确认");
    }
}
