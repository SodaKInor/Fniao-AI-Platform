package org.jeecg.modules.ai.client;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import org.jeecg.modules.ai.client.draft.*;
import org.jeecg.modules.ai.config.provider.*;
import org.jeecg.modules.ai.domain.*;

public final class ClientTestInputs {
    private ClientTestInputs() { }
    public static final Path EXAMPLES = Paths.get("/workspace/backend-github/integrations/ai-contracts/examples");
    public static final String REQUEST_ID = "mock_job_0001";

    public static String example(String name) throws IOException {
        return new String(Files.readAllBytes(EXAMPLES.resolve(name)), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static CapabilitySnapshot binding(boolean mock) {
        return new CapabilitySnapshot("image-detection.v1", "mock-v1", mock ? "mock" : "fixture",
                mock ? "mock-v1" : "sync-draft-v0.1", "image-detection.v1", null, new ProviderFeatures(false, false, false));
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
