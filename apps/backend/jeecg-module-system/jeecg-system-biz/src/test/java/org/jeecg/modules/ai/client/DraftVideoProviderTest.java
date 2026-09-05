package org.jeecg.modules.ai.client;

import java.io.InputStream;
import java.time.Clock;
import org.jeecg.modules.ai.provider.adapter.draft.DraftArtifactReader;
import org.jeecg.modules.ai.provider.adapter.draft.DraftVideoHttpProvider;
import org.jeecg.modules.ai.provider.config.DraftTransportFactory;
import org.jeecg.modules.ai.provider.config.ProviderProperties;
import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.result.domain.ProviderArtifact;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.video.domain.VideoProviderResult;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.jeecg.modules.ai.client.ClientTestInputs.*;

public class DraftVideoProviderTest {
    @Test public void multipartVideoSuccessIsStrictAndOneShot() throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            fixture.json(example("provider-video-success.json"));
            CountingSource input = new CountingSource(new byte[]{0, 1, 2, 3});
            VideoProviderResult result = videoProvider(properties(fixture.url()))
                    .analyze(videoRequest(input, true, false, 4L));
            assertEquals(1, fixture.calls.get());
            assertEquals("POST", fixture.method); assertEquals("/video-jobs", fixture.requestUri);
            assertEquals("fixture-service-token", fixture.authorization.substring(7));
            assertTrue(fixture.requestBody.contains("\"contract_version\":\"0.2-draft\""));
            assertTrue(fixture.requestBody.contains("\"sample_interval_ms\":1000"));
            assertFalse(fixture.requestBody.contains("ownerId")); assertFalse(fixture.requestBody.contains("gpu"));
            assertEquals(1, input.opens.get()); assertEquals(1, input.closes.get());
            assertEquals(1, result.getEvents().size()); assertNotNull(result.getEvents().get(0).getSnapshot());
            assertNull(result.getAnnotatedVideo());
        }
    }

    @Test public void emptyVideoEventsAreAValidSuccess() throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            fixture.json("{\"simulated\":false,\"events\":[]}");
            VideoProviderResult result = videoProvider(properties(fixture.url()))
                    .analyze(videoRequest(new CountingSource(), false, false, null));
            assertTrue(result.getEvents().isEmpty()); assertNull(result.getAnnotatedVideo());
        }
    }

    @Test public void extensionsMissingFieldsAndForbiddenArtifactsStayUnknown() throws Exception {
        String valid = example("provider-video-success.json");
        String[] invalid = {
                valid.replace("\"simulated\": true", "\"simulated\":true,\"vendor_extension\":1"),
                valid.replace("\"event_type\": \"person\",", ""),
                valid.replace("\"event_type\": \"person\",", "\"event_type\":\"person\",\"vendor\":1,"),
                valid.replace("image/png", "application/octet-stream")
        };
        for (String body : invalid) assertProtocolFailure(body, true, false);
        assertProtocolFailure(valid, false, false);
        String annotated = valid.replace("\n  ]\n}",
                "\n  ],\n  \"annotated_video\": {\"reference\":\"artifacts/out.mp4\",\"media_type\":\"video/mp4\"}\n}");
        assertProtocolFailure(annotated, true, false);
    }

    @Test public void responseLossAndHttpErrorsNeverReplayVideoPost() throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            fixture.disconnect = true;
            ProviderException error = failure(videoProvider(properties(fixture.url())), new CountingSource());
            assertEquals(ExecutionCertainty.UNKNOWN, error.getCertainty()); assertEquals(1, fixture.calls.get());
        }
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            fixture.status = 401; fixture.json("credential must not escape");
            ProviderException error = failure(videoProvider(properties(fixture.url())), new CountingSource());
            assertEquals(ErrorCode.PROVIDER_AUTH, error.getErrorCode()); assertNull(error.getCause());
            assertFalse(error.getMessage().contains("must not escape"));
            assertFalse(error.getMessage().contains("fixture-service-token")); assertEquals(1, fixture.calls.get());
        }
    }

    @Test public void declaredVideoLimitRejectsBeforeOpeningInput() throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            ProviderProperties properties = properties(fixture.url()); properties.setVideoUploadMaxBytes(3);
            CountingSource input = new CountingSource(new byte[4]);
            ProviderException error = failure(videoProvider(properties), input);
            assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode()); assertEquals(0, input.opens.get());
            assertEquals(0, fixture.calls.get());
        }
    }

    @Test public void v02ArtifactWithoutExpiryOrLengthUsesBoundedReader() throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            fixture.contentType = "image/png"; fixture.response = new byte[]{1, 2, 3};
            ProviderProperties properties = properties(fixture.url());
            DraftArtifactReader reader = new DraftArtifactReader(
                    DraftTransportFactory.create(properties, true), Clock.systemUTC());
            ProviderArtifact artifact = new ProviderArtifact("artifacts/snapshot-1.png",
                    new ContentMetadata("snapshot-1.png", "image/png", null, null), null);
            try (InputStream input = reader.open(
                    draftBinding("video-file-analysis.v1", "video-draft-v0.2"), artifact, 3)) {
                assertArrayEquals(fixture.response, ProtocolFixture.read(input));
            }
            assertEquals(1, fixture.calls.get());
        }
    }

    private void assertProtocolFailure(String body, boolean snapshots, boolean annotate) throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            fixture.json(body);
            try {
                videoProvider(properties(fixture.url()))
                        .analyze(videoRequest(new CountingSource(), snapshots, annotate, null));
                fail("Expected protocol rejection");
            } catch (ProviderException error) {
                assertEquals(ErrorCode.PROVIDER_PROTOCOL, error.getErrorCode());
                assertEquals(ExecutionCertainty.UNKNOWN, error.getCertainty());
                assertEquals(1, fixture.calls.get());
            }
        }
    }

    private ProviderException failure(DraftVideoHttpProvider provider, CountingSource input) throws Exception {
        try { provider.analyze(videoRequest(input, false, false, (long) input.bytes.length)); fail(); return null; }
        catch (ProviderException error) { return error; }
    }
}
