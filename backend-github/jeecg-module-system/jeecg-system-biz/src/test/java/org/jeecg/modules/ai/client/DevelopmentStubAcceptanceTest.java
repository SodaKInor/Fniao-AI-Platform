package org.jeecg.modules.ai.client;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import org.jeecg.modules.ai.config.provider.ProviderAvailability;
import org.jeecg.modules.ai.config.provider.ProviderConfiguration;
import org.jeecg.modules.ai.config.provider.ProviderProperties;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Opt-in wire acceptance for the independent Node development stub. Ordinary test runs skip it. */
public class DevelopmentStubAcceptanceTest {
    @Test public void imageVideoStreamAndArtifactsCrossTheRealHttpBoundary() throws Exception {
        String url = System.getenv("WGAI_STUB_URL");
        String tokenFile = System.getenv("WGAI_STUB_TOKEN_FILE");
        Assume.assumeTrue(url != null && tokenFile != null);

        ProviderProperties properties = new ProviderProperties();
        properties.setMode("remote");
        properties.setDevelopmentStub(true);
        properties.setProviderKey("stub");
        properties.setBaseUrl(url);
        properties.setApprovedOrigin(url);
        properties.setTokenFile(tokenFile);
        properties.setRequestTimeoutMs(5000);
        properties.setTransferTimeoutMs(5000);

        ProviderConfiguration configuration = new ProviderConfiguration();
        ProviderAvailability availability = new ProviderAvailability(properties,
                new ProviderObservations(Clock.systemUTC()));
        assertEquals("", availability.modeReason());

        byte[] imageBytes = Files.readAllBytes(Paths.get(
                "/workspace/backend-github/integrations/ai-contracts/examples/input.png"));
        InferenceProvider imageProvider = configuration.inferenceProvider(properties, availability);
        ProviderResult image = imageProvider.infer(new ProviderRequest(
                "stub-image-acceptance", binding("image-detection.v1", "sync-draft-v0.1"),
                new DetectionParameters(new BigDecimal("0.5"), 10, true),
                new ContentMetadata("input.png", "image/png", (long) imageBytes.length, null),
                new ClientTestInputs.CountingSource(imageBytes)));
        assertTrue(image.isSimulated());
        assertEquals("synthetic-square", image.getData().getDetections().get(0).getLabel());

        ProviderArtifactReader artifacts = configuration.providerArtifactReader(properties);
        try (InputStream input = artifacts.open(binding("image-detection.v1", "sync-draft-v0.1"),
                image.getArtifacts().get(0), 1024)) {
            assertEquals(79, ProtocolFixture.read(input).length);
        }

        VideoAnalysisProvider videoProvider = configuration.videoAnalysisProvider(properties, availability);
        VideoProviderResult video = videoProvider.analyze(new VideoProviderRequest(
                "stub-video-acceptance", binding("video-file-analysis.v1", "video-draft-v0.2"),
                new VideoParameters(new BigDecimal("0.5"), 1000, 100, true, false),
                new ContentMetadata("input.mp4", "video/mp4", 4L, null),
                new ClientTestInputs.CountingSource(new byte[]{0, 1, 2, 3})));
        assertEquals(1, video.getEvents().size());
        assertEquals(1250, video.getEvents().get(0).getOffsetMillis());

        StreamSessionProvider streams = configuration.streamSessionProvider(properties, availability);
        ProviderStreamSession session = streams.start(new ProviderStreamStartRequest(
                "stub-stream-acceptance", binding("video-stream-analysis.v1", "stream-draft-v0.2"),
                "synthetic-camera-01", new StreamParameters(50, 2000, true)));
        assertEquals(StreamSessionState.RUNNING, session.getState());
        ProviderStreamEventPage events = streams.getEvents(session.getProviderSessionId(), null, 50);
        assertEquals(1, events.getItems().size());
        assertEquals("synthetic-person", events.getItems().get(0).getEventType());
        assertEquals(StreamStopOutcome.CONFIRMED_STOPPED,
                streams.stop(session.getProviderSessionId()).getOutcome());
    }

    private CapabilitySnapshot binding(String capability, String adapter) {
        return new CapabilitySnapshot(capability, "1.1.0", "stub", adapter, capability,
                "stub-simulated-v1", new ProviderFeatures(false, false, false));
    }
}
