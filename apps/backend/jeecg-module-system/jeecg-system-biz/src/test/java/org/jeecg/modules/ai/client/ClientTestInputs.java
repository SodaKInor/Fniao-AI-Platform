package org.jeecg.modules.ai.client;

import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.asset.domain.ContentSource;
import org.jeecg.modules.ai.capability.domain.CapabilitySnapshot;
import org.jeecg.modules.ai.capability.domain.ProviderFeatures;
import org.jeecg.modules.ai.image.domain.DetectionParameters;
import org.jeecg.modules.ai.image.domain.ProviderRequest;
import org.jeecg.modules.ai.provider.adapter.ProviderObservations;
import org.jeecg.modules.ai.provider.adapter.draft.DraftHttpProvider;
import org.jeecg.modules.ai.provider.adapter.draft.DraftStreamHttpProvider;
import org.jeecg.modules.ai.provider.adapter.draft.DraftVideoHttpProvider;
import org.jeecg.modules.ai.provider.config.DraftTransportFactory;
import org.jeecg.modules.ai.provider.config.ProviderProperties;
import org.jeecg.modules.ai.stream.domain.ProviderStreamStartRequest;
import org.jeecg.modules.ai.stream.domain.StreamParameters;
import org.jeecg.modules.ai.video.domain.VideoParameters;
import org.jeecg.modules.ai.video.domain.VideoProviderRequest;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClientTestInputs {
    private ClientTestInputs() { }
    public static final Path EXAMPLES = locateExamples();
    public static final String REQUEST_ID = "mock_job_0001";
    public static final String VIDEO_REQUEST_ID = "video-request-001";
    public static final String STREAM_SESSION_ID = "stream-session-001";

    private static Path locateExamples() {
        String configured = System.getProperty("ai.test.examples");
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        for (Path root = Paths.get("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path backendRelative = root.resolve("integrations/ai-contracts/examples");
            if (Files.isDirectory(backendRelative)) {
                return backendRelative;
            }
            Path repositoryRelative = root.resolve("apps/backend/integrations/ai-contracts/examples");
            if (Files.isDirectory(repositoryRelative)) {
                return repositoryRelative;
            }
        }
        throw new IllegalStateException("Cannot locate integrations/ai-contracts/examples");
    }

    public static String example(String name) throws IOException {
        return new String(Files.readAllBytes(EXAMPLES.resolve(name)), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static CapabilitySnapshot binding(boolean mock) {
        return new CapabilitySnapshot("image-detection.v1", "mock-v1", mock ? "mock" : "fixture",
                mock ? "mock-v1" : "sync-draft-v0.1", "image-detection.v1", null, new ProviderFeatures(false, false, false));
    }

    public static CapabilitySnapshot draftBinding(String capability, String adapter) {
        return new CapabilitySnapshot(capability, "draft-v1", "fixture", adapter,
                capability, null, new ProviderFeatures(false, false, false));
    }

    public static ProviderRequest request(CountingSource source, boolean mock, boolean annotate, Long size) {
        return new ProviderRequest(REQUEST_ID, binding(mock), new DetectionParameters(new BigDecimal("0.5"), 10, annotate),
                new ContentMetadata("input.png", "image/png", size, null), source);
    }

    public static ProviderProperties properties(String url) throws IOException {
        ProviderProperties p = new ProviderProperties();
        p.setBaseUrl(url); p.setApprovedOrigin(url); p.setProviderKey("fixture");
        Path secret = Files.createTempFile("03-client-token", ".txt");
        Files.write(secret, "fixture-service-token".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        secret.toFile().deleteOnExit(); p.setTokenFile(secret.toString());
        return p;
    }

    public static DraftHttpProvider provider(ProviderProperties p) {
        return new DraftHttpProvider(DraftTransportFactory.create(p, true), new ProviderObservations(Clock.systemUTC()));
    }

    public static DraftVideoHttpProvider videoProvider(ProviderProperties p) {
        return new DraftVideoHttpProvider(
                DraftTransportFactory.create(p, true), new ProviderObservations(Clock.systemUTC()));
    }

    public static DraftStreamHttpProvider streamProvider(ProviderProperties p) {
        return new DraftStreamHttpProvider(
                DraftTransportFactory.create(p, true), new ProviderObservations(Clock.systemUTC()));
    }

    public static VideoProviderRequest videoRequest(CountingSource source, boolean snapshots, boolean annotate, Long size) {
        return new VideoProviderRequest(VIDEO_REQUEST_ID,
                draftBinding("video-file-analysis.v1", "video-draft-v0.2"),
                new VideoParameters(new BigDecimal("0.5"), 1000, 100, snapshots, annotate),
                new ContentMetadata("input.mp4", "video/mp4", size, null), source);
    }

    public static ProviderStreamStartRequest streamRequest(boolean snapshots) {
        return new ProviderStreamStartRequest(STREAM_SESSION_ID,
                draftBinding("video-stream-analysis.v1", "stream-draft-v0.2"),
                "source-001", new StreamParameters(50, 2000, snapshots));
    }

    public static final class CountingSource implements ContentSource {
        public final AtomicInteger opens = new AtomicInteger();
        public final AtomicInteger closes = new AtomicInteger();
        public final byte[] bytes;
        public CountingSource() throws IOException { bytes = Files.readAllBytes(EXAMPLES.resolve("input.png")); }
        public CountingSource(byte[] bytes) { this.bytes = bytes; }
        @Override public InputStream openStream() {
            opens.incrementAndGet();
            return new ByteArrayInputStream(bytes) {
                @Override public void close() throws IOException { closes.incrementAndGet(); super.close(); }
            };
        }
    }
}
