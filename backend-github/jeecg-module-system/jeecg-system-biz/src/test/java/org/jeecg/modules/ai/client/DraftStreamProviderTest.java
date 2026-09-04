package org.jeecg.modules.ai.client;

import org.jeecg.modules.ai.provider.adapter.draft.DraftStreamHttpProvider;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.stream.domain.ProviderStreamEventPage;
import org.jeecg.modules.ai.stream.domain.ProviderStreamSession;
import org.jeecg.modules.ai.stream.domain.StreamSessionState;
import org.jeecg.modules.ai.stream.domain.StreamStopOutcome;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.jeecg.modules.ai.client.ClientTestInputs.*;

public class DraftStreamProviderTest {
    private static final String PROVIDER_ID = "provider-stream-session-001";

    @Test public void startUsesRegisteredSourcePathAndStrictMetadata() throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            fixture.json(example("provider-stream-running.json"));
            ProviderStreamSession result = streamProvider(properties(fixture.url())).start(streamRequest(true));
            assertEquals(1, fixture.calls.get()); assertEquals("POST", fixture.method);
            assertEquals("/stream-sources/source-001/sessions", fixture.requestUri);
            assertTrue(fixture.requestBody.contains("\"request_id\":\"stream-session-001\""));
            assertTrue(fixture.requestBody.contains("\"include_snapshots\":true"));
            assertFalse(fixture.requestBody.contains("rtsp")); assertFalse(fixture.requestBody.contains("gpu"));
            assertEquals(PROVIDER_ID, result.getProviderSessionId());
            assertEquals(StreamSessionState.RUNNING, result.getState());
        }
    }

    @Test public void queryEventsEmptyPageAndConfirmedStopUseExactResources() throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            DraftStreamHttpProvider provider = streamProvider(properties(fixture.url()));
            fixture.json(example("provider-stream-running.json"));
            assertEquals(StreamSessionState.RUNNING, provider.getSession(PROVIDER_ID).getState());
            assertEquals("GET", fixture.method); assertEquals("/stream-sessions/" + PROVIDER_ID, fixture.requestUri);

            fixture.json(example("provider-stream-events.json"));
            ProviderStreamEventPage page = provider.getEvents(PROVIDER_ID, "cursor before", 50);
            assertEquals(1, page.getItems().size()); assertNotNull(page.getItems().get(0).getSnapshot());
            assertTrue(fixture.requestUri.startsWith("/stream-sessions/" + PROVIDER_ID + "/events?"));
            assertTrue(fixture.requestUri.contains("cursor=cursor%20before"));

            fixture.json("{\"items\":[]}");
            assertTrue(provider.getEvents(PROVIDER_ID, null, 50).getItems().isEmpty());

            fixture.json(example("provider-stream-stop.json"));
            assertEquals(StreamStopOutcome.CONFIRMED_STOPPED, provider.stop(PROVIDER_ID).getOutcome());
            assertEquals("POST", fixture.method);
            assertEquals("/stream-sessions/" + PROVIDER_ID + "/stop", fixture.requestUri);
            assertEquals(4, fixture.calls.get());
        }
    }

    @Test public void unknownMissingAndIllegalSessionFieldsAreRejected() throws Exception {
        String valid = example("provider-stream-running.json");
        String[] invalid = {
                valid.replace("\"state\": \"RUNNING\"", "\"state\":\"RUNNING\",\"vendor\":1"),
                valid.replace("\"state\": \"RUNNING\",", ""),
                valid.replace("RUNNING", "UNKNOWN"),
                valid.replace(PROVIDER_ID, "wrong-provider-session")
        };
        for (String body : invalid) {
            try (ProtocolFixture fixture = new ProtocolFixture(false)) {
                fixture.json(body);
                ProviderException error = queryFailure(streamProvider(properties(fixture.url())));
                assertEquals(ErrorCode.PROVIDER_PROTOCOL, error.getErrorCode());
                assertEquals(ExecutionCertainty.UNKNOWN, error.getCertainty());
                assertEquals(1, fixture.calls.get());
            }
        }
    }

    @Test public void duplicateOutOfOrderAndExtendedEventsAreRejected() throws Exception {
        String valid = example("provider-stream-events.json");
        String duplicate = "{\"items\":["
                + "{\"event_id\":\"same\",\"offset_ms\":1,\"occurred_at\":\"2026-09-03T12:20:02Z\",\"event_type\":\"person\"},"
                + "{\"event_id\":\"same\",\"offset_ms\":2,\"occurred_at\":\"2026-09-03T12:20:03Z\",\"event_type\":\"person\"}]}";
        String[] invalid = {
                valid.replace("\"event_type\": \"person\",", "\"event_type\":\"person\",\"vendor\":1,"),
                valid.replace("\"occurred_at\": \"2026-09-03T12:20:02.500Z\",", ""),
                valid.replace("0.93", "2.0"),
                duplicate
        };
        for (String body : invalid) {
            try (ProtocolFixture fixture = new ProtocolFixture(false)) {
                fixture.json(body);
                try { streamProvider(properties(fixture.url())).getEvents(PROVIDER_ID, null, 50); fail(); }
                catch (ProviderException error) {
                    assertEquals(ErrorCode.PROVIDER_PROTOCOL, error.getErrorCode());
                    assertEquals(ExecutionCertainty.UNKNOWN, error.getCertainty());
                }
                assertEquals(1, fixture.calls.get());
            }
        }
    }

    @Test public void startAndStopResponseLossStayUnknownAndNeverRetry() throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            fixture.disconnect = true;
            try { streamProvider(properties(fixture.url())).start(streamRequest(false)); fail(); }
            catch (ProviderException error) { assertEquals(ExecutionCertainty.UNKNOWN, error.getCertainty()); }
            assertEquals(1, fixture.calls.get());
        }
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            fixture.disconnect = true;
            try { streamProvider(properties(fixture.url())).stop(PROVIDER_ID); fail(); }
            catch (ProviderException error) { assertEquals(ExecutionCertainty.UNKNOWN, error.getCertainty()); }
            assertEquals(1, fixture.calls.get());
        }
    }

    @Test public void invalidIdsCursorAndLimitMakeNoRequest() throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            DraftStreamHttpProvider provider = streamProvider(properties(fixture.url()));
            for (String id : new String[]{"", "../session", "https://gpu.invalid"}) {
                try { provider.getSession(id); fail(); }
                catch (ProviderException error) { assertEquals(ExecutionCertainty.NOT_STARTED, error.getCertainty()); }
            }
            try { provider.getEvents(PROVIDER_ID, "line\nbreak", 50); fail(); }
            catch (ProviderException error) { assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode()); }
            try { provider.getEvents(PROVIDER_ID, null, 201); fail(); }
            catch (ProviderException error) { assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode()); }
            assertEquals(0, fixture.calls.get());
        }
    }

    private ProviderException queryFailure(DraftStreamHttpProvider provider) throws Exception {
        try { provider.getSession(PROVIDER_ID); fail(); return null; }
        catch (ProviderException error) { return error; }
    }
}
