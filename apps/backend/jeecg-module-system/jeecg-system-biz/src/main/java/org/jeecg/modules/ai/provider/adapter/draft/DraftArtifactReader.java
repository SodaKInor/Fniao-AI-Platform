package org.jeecg.modules.ai.provider.adapter.draft;

import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.capability.domain.CapabilitySnapshot;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.result.domain.ProviderArtifact;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Request;
import okhttp3.Response;
import org.jeecg.modules.ai.provider.adapter.TransferInputStream;
import org.jeecg.modules.ai.result.port.ProviderArtifactReader;

public final class DraftArtifactReader implements ProviderArtifactReader {
    private final DraftTransport transport;
    private final Clock clock;

    public DraftArtifactReader(DraftTransport transport, Clock clock) { this.transport = transport; this.clock = clock; }

    @Override public InputStream open(CapabilitySnapshot snapshot, ProviderArtifact artifact, long maxBytes) throws ProviderException {
        checkBinding(snapshot);
        validate(artifact, maxBytes);
        transport.acquire();
        Response response = null;
        try {
            Request.Builder request = new Request.Builder().url(transport.endpoint.artifact(artifact.getReference())).get();
            response = transport.execute(request, new AtomicBoolean(), true);
            if (response.code() != 200 || response.body() == null || response.body().contentType() == null
                    || !artifact.getMetadata().getMediaType().equals(response.body().contentType().type() + "/" + response.body().contentType().subtype())
                    || (response.body().contentLength() >= 0
                    && artifact.getMetadata().getSizeBytes() != null
                    && response.body().contentLength() != artifact.getMetadata().getSizeBytes())
                    || response.body().contentLength() > Math.min(maxBytes, configuredLimit(artifact))) {
                throw new IOException("Invalid artifact response");
            }
            return ownedStream(response, artifact, Math.min(maxBytes, configuredLimit(artifact)));
        } catch (IOException | RuntimeException | ProviderException error) {
            if (response != null) response.close();
            transport.release();
            throw new ProviderException(ErrorCode.ARTIFACT_TRANSFER, ExecutionCertainty.UNKNOWN, "Provider artifact could not be opened");
        }
    }

    private void validate(ProviderArtifact artifact, long maxBytes) throws ProviderException {
        if (artifact == null || (artifact.getExpiresAt() != null && !artifact.getExpiresAt().isAfter(clock.instant()))) {
            throw new ProviderException(ErrorCode.ARTIFACT_EXPIRED, ExecutionCertainty.UNKNOWN, "Provider artifact has expired");
        }
        ContentMetadata m = artifact.getMetadata();
        if (maxBytes <= 0 || m == null
                || (m.getSizeBytes() != null && (m.getSizeBytes() < 1
                || m.getSizeBytes() > Math.min(maxBytes, configuredLimit(artifact))))
                || !("image/png".equals(m.getMediaType()) || "image/jpeg".equals(m.getMediaType())
                || "video/mp4".equals(m.getMediaType()))) {
            throw new ProviderException(ErrorCode.ARTIFACT_TRANSFER, ExecutionCertainty.UNKNOWN, "Invalid artifact metadata or limit");
        }
        try { transport.endpoint.artifact(artifact.getReference()); }
        catch (IllegalArgumentException error) {
            throw new ProviderException(ErrorCode.ARTIFACT_TRANSFER, ExecutionCertainty.UNKNOWN, "Unapproved artifact reference");
        }
    }

    private InputStream ownedStream(Response response, ProviderArtifact artifact, long limit) {
        InputStream bounded = new TransferInputStream(response.body().byteStream(), limit, artifact.getMetadata().getSizeBytes());
        return new FilterInputStream(bounded) {
            private final AtomicBoolean closed = new AtomicBoolean();
            @Override public int read() throws IOException {
                try { return in.read(); } catch (IOException error) { close(); throw new IOException("Artifact transfer interrupted"); }
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                try { return in.read(bytes, offset, length); } catch (IOException error) { close(); throw new IOException("Artifact transfer interrupted"); }
            }
            @Override public void close() {
                if (closed.compareAndSet(false, true)) {
                    try { response.close(); } finally { transport.release(); }
                }
            }
        };
    }

    private void checkBinding(CapabilitySnapshot snapshot) throws ProviderException {
        String capability = snapshot == null ? "" : snapshot.getCapabilityCode();
        if ("image-detection.v1".equals(capability)) {
            transport.checkBinding(snapshot);
        } else if ("video-file-analysis.v1".equals(capability)) {
            transport.checkBinding(snapshot, capability, "video-draft-v0.2");
        } else if ("video-stream-analysis.v1".equals(capability)) {
            transport.checkBinding(snapshot, capability, "stream-draft-v0.2");
        } else {
            throw org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks.unavailable(
                    "Provider artifact binding is unsupported");
        }
    }

    private long configuredLimit(ProviderArtifact artifact) {
        return "video/mp4".equals(artifact.getMetadata().getMediaType())
                ? transport.videoOutputLimit : transport.outputLimit;
    }
}
