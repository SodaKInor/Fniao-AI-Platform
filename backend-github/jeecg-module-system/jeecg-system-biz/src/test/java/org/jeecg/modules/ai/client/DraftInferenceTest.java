package org.jeecg.modules.ai.client;

import java.net.ServerSocket;
import java.time.Clock;
import java.util.concurrent.*;
import org.jeecg.modules.ai.client.draft.*;
import org.jeecg.modules.ai.config.provider.*;
import org.jeecg.modules.ai.domain.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.jeecg.modules.ai.client.ClientTestInputs.*;

public class DraftInferenceTest {
    @Test public void multipartUsesIndependentCredentialAndClosesInputOnce() throws Exception {
        try (ProtocolFixture f = new ProtocolFixture(false)) {
            f.json(example("provider-success.json"));
            CountingSource input = new CountingSource();
            ProviderResult result = provider(properties(f.url())).infer(request(input, false, true, (long) input.bytes.length));
            assertEquals(1, f.calls.get()); assertEquals(1, input.opens.get()); assertEquals(1, input.closes.get());
            assertEquals("fixture-service-token", f.authorization.substring(7));
            assertTrue(f.requestBody.contains("name=\"metadata\""));
            assertTrue(f.requestBody.contains("\"max_detections\":10"));
            assertTrue(f.requestBody.contains("\"request_id\":\"" + REQUEST_ID + "\""));
            assertFalse(f.requestBody.contains("ownerId")); assertFalse(f.requestBody.contains("storageKey"));
            assertTrue(result.isSimulated()); assertEquals(1, result.getData().getDetections().size());
            assertEquals(1, result.getArtifacts().size());
        }
    }

    @Test public void validEmptyResponseIsSuccess() throws Exception {
        try (ProtocolFixture f = new ProtocolFixture(false)) {
            f.json(example("provider-empty.json"));
            ProviderResult result = provider(properties(f.url())).infer(request(new CountingSource(), false, false, null));
            assertTrue(result.getData().getDetections().isEmpty()); assertTrue(result.getArtifacts().isEmpty());
        }
    }

    @Test public void strictResponseFailuresStayUnknown() throws Exception {
        String valid = example("provider-success.json");
        String[] invalid = {valid.replace("0.1-draft", "0.2"), valid.replace(REQUEST_ID, "wrong-id"),
                valid.replace("\"simulated\": true", "\"simulated\":true,\"simulated\":true"),
                valid.replace("\"simulated\": true", "\"simulated\":true,\"unexpected\":1"),
                valid.replace("\"x\": 0.25", "\"x\": 0.75"), valid.replace("0.95", "1e999"), valid + "{}"};
        for (String body : invalid) {
            try (ProtocolFixture f = new ProtocolFixture(false)) {
                f.json(body);
                ProviderException error = failure(provider(properties(f.url())), new CountingSource());
                assertEquals(ErrorCode.PROVIDER_PROTOCOL, error.getErrorCode());
                assertEquals(ExecutionCertainty.UNKNOWN, error.getCertainty()); assertEquals(1, f.calls.get());
            }
        }
    }

    @Test public void httpFailuresNeverReplayIncludingRetryAfterZero() throws Exception {
        for (int status : new int[]{400, 401, 403, 408, 429, 500, 503, 202, 301, 307}) {
            try (ProtocolFixture f = new ProtocolFixture(false)) {
                f.status = status; f.json("fixture-service-token must not escape");
                CountingSource input = new CountingSource();
                ProviderException error = failure(provider(properties(f.url())), input);
                assertEquals("status " + status, 1, f.calls.get());
                assertEquals(1, input.opens.get()); assertEquals(1, input.closes.get());
                assertFalse(error.getMessage().contains("fixture-service-token")); assertNull(error.getCause());
                if (status == 401 || status == 403) {
                    assertEquals(ErrorCode.PROVIDER_AUTH, error.getErrorCode());
                    assertEquals(ExecutionCertainty.NOT_STARTED, error.getCertainty());
                } else assertEquals(ExecutionCertainty.UNKNOWN, error.getCertainty());
            }
        }
    }

    @Test public void connectionRefusalIsKnownNotStartedAndDoesNotOpenInput() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        CountingSource input = new CountingSource();
        ProviderException error = failure(provider(properties("http://127.0.0.1:" + port)), input);
        assertEquals(ErrorCode.PROVIDER_OFFLINE, error.getErrorCode());
        assertEquals(ExecutionCertainty.NOT_STARTED, error.getCertainty()); assertEquals(0, input.opens.get());
    }

    @Test public void responseLossAndHeaderOrBodyTimeoutNeverRetry() throws Exception {
        for (int scenario = 0; scenario < 3; scenario++) {
            try (ProtocolFixture f = new ProtocolFixture(false)) {
                f.json(example("provider-empty.json"));
                f.disconnect = scenario == 0; f.delayHeaders = scenario == 1 ? 300 : 0; f.delayBody = scenario == 2 ? 300 : 0;
                ProviderProperties p = properties(f.url()); p.setRequestTimeoutMs(100);
                ProviderException error = failure(provider(p), new CountingSource());
                assertEquals(ExecutionCertainty.UNKNOWN, error.getCertainty()); assertEquals(1, f.calls.get());
                if (scenario > 0) assertEquals(ErrorCode.PROVIDER_TIMEOUT, error.getErrorCode());
            }
        }
    }

    @Test public void inflightLimitRejectsWithoutOpeningSecondInput() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        try (ProtocolFixture f = new ProtocolFixture(false)) {
            f.json(example("provider-empty.json")); f.delayHeaders = 500;
            DraftHttpProvider provider = provider(properties(f.url()));
            Future<ProviderResult> first = workers.submit(() -> provider.infer(request(new CountingSource(), false, true, null)));
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (f.calls.get() == 0 && System.nanoTime() < until) Thread.sleep(5);
            assertEquals(1, f.calls.get());
            CountingSource second = new CountingSource();
            assertEquals(ErrorCode.LIMIT_EXCEEDED, failure(provider, second).getErrorCode());
            assertEquals(0, second.opens.get()); first.get(3, TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }
    }

    @Test public void streamingInputLimitClosesSourceEvenWithUnknownSize() throws Exception {
        try (ProtocolFixture f = new ProtocolFixture(false)) {
            ProviderProperties p = properties(f.url()); p.setUploadMaxBytes(32);
            CountingSource source = new CountingSource(new byte[100]);
            failure(provider(p), source);
            assertEquals(1, source.opens.get()); assertEquals(1, source.closes.get());
            CountingSource unopened = new CountingSource();
            try { provider(p).infer(request(unopened, false, true, 100L)); fail(); }
            catch (ProviderException expected) { assertEquals(ExecutionCertainty.NOT_STARTED, expected.getCertainty()); }
            assertEquals(0, unopened.opens.get());
        }
    }

    @Test public void uploadTransferBudgetIsIndependentOfTotalRequestBudget() throws Exception {
        try (ProtocolFixture f = new ProtocolFixture(false)) {
            f.delayInput = 1500;
            ProviderProperties p = properties(f.url()); p.setTransferTimeoutMs(100); p.setRequestTimeoutMs(3000);
            CountingSource input = new CountingSource(new byte[8 * 1024 * 1024]);
            long start = System.nanoTime();
            ProviderException error = failure(provider(p), input);
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1200);
            assertEquals(ErrorCode.PROVIDER_TIMEOUT, error.getErrorCode());
            assertEquals(ExecutionCertainty.UNKNOWN, error.getCertainty());
            assertEquals(1, input.opens.get()); assertEquals(1, input.closes.get());
        }
    }

    private ProviderException failure(DraftHttpProvider provider, CountingSource source) throws Exception {
        try { provider.infer(request(source, false, true, null)); fail("Expected provider failure"); return null; }
        catch (ProviderException error) { return error; }
    }
}
