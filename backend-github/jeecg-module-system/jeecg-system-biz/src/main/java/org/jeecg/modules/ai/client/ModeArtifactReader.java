package org.jeecg.modules.ai.client;

import java.io.InputStream;
import java.util.function.Function;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.ProviderArtifactReader;

/** Collection uses the saved binding; disabling new inference does not invalidate past mock output. */
public final class ModeArtifactReader implements ProviderArtifactReader {
    private final ProviderArtifactReader mock;
    private final ProviderArtifactReader remote;
    private final Function<String, String> remoteReason;
    private final long outputLimit;
    private final long videoOutputLimit;

    public ModeArtifactReader(ProviderArtifactReader mock, long outputLimit) {
        this(mock, null, ignored -> "真实成果下载协议尚未确认", outputLimit, outputLimit);
    }

    public ModeArtifactReader(
            ProviderArtifactReader mock,
            ProviderArtifactReader remote,
            Function<String, String> remoteReason,
            long outputLimit,
            long videoOutputLimit) {
        this.mock = mock;
        this.remote = remote;
        this.remoteReason = remoteReason;
        this.outputLimit = outputLimit;
        this.videoOutputLimit = videoOutputLimit;
    }

    @Override public InputStream open(CapabilitySnapshot snapshot, ProviderArtifact artifact, long maxBytes) throws ProviderException {
        if (ProviderRequestChecks.binding(snapshot, "mock", "mock-v1")) {
            return mock.open(snapshot, artifact, Math.min(maxBytes, outputLimit));
        }
        String capability = snapshot == null ? "" : snapshot.getCapabilityCode();
        String reason = remoteReason.apply(capability);
        if (!reason.isEmpty()) throw ProviderRequestChecks.unavailable(reason);
        if (remote == null) throw ProviderRequestChecks.unavailable("真实成果下载协议尚未确认");
        long configured = "video-file-analysis.v1".equals(capability) ? videoOutputLimit : outputLimit;
        return remote.open(snapshot, artifact, Math.min(maxBytes, configured));
    }
}
