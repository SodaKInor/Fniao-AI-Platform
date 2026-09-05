package org.jeecg.modules.ai.provider.adapter.draft;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Request;
import okhttp3.Response;
import org.jeecg.modules.ai.provider.adapter.ProviderObservations;
import org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.stream.domain.ProviderStreamEventPage;
import org.jeecg.modules.ai.stream.domain.ProviderStreamSession;
import org.jeecg.modules.ai.stream.domain.ProviderStreamStartRequest;
import org.jeecg.modules.ai.stream.domain.StreamStopResult;
import org.jeecg.modules.ai.stream.port.StreamSessionProvider;

/** Real HTTP mechanics for the unconfirmed v0.2 stream draft; production gates remain closed. */
public final class DraftStreamHttpProvider implements StreamSessionProvider {
    private final DraftTransport transport;
    private final ProviderObservations observations;

    public DraftStreamHttpProvider(DraftTransport transport, ProviderObservations observations) {
        this.transport = transport;
        this.observations = observations;
    }

    @Override public ProviderStreamSession start(ProviderStreamStartRequest request) throws ProviderException {
        ProviderRequestChecks.validateStreamStart(request);
        transport.checkBinding(request.getCapability(), "video-stream-analysis.v1", "stream-draft-v0.2");
        return call(new Request.Builder().url(transport.endpoint.startStream(request.getProviderSourceRef()))
                .post(new DraftStreamRequestEncoder().encode(request)), "stream start",
                input -> decoder().session(input, null));
    }

    @Override public ProviderStreamSession getSession(String providerSessionId) throws ProviderException {
        ProviderRequestChecks.validateProviderSessionId(providerSessionId);
        return call(new Request.Builder().url(transport.endpoint.streamSession(providerSessionId)).get(),
                "stream session", input -> decoder().session(input, providerSessionId));
    }

    @Override public ProviderStreamEventPage getEvents(String providerSessionId, String cursor, int limit)
            throws ProviderException {
        ProviderRequestChecks.validateProviderSessionId(providerSessionId);
        ProviderRequestChecks.validateCursor(cursor, limit);
        return call(new Request.Builder().url(transport.endpoint.streamEvents(providerSessionId, cursor, limit)).get(),
                "stream events", input -> decoder().events(input, limit));
    }

    @Override public StreamStopResult stop(String providerSessionId) throws ProviderException {
        ProviderRequestChecks.validateProviderSessionId(providerSessionId);
        return call(new Request.Builder().url(transport.endpoint.stopStream(providerSessionId))
                .post(DraftRequestBodies.empty()), "stream stop",
                input -> decoder().stop(input, providerSessionId));
    }

    private DraftStreamResponseDecoder decoder() {
        return new DraftStreamResponseDecoder(transport.outputLimit, transport.videoOutputLimit);
    }

    private <T> T call(Request.Builder request, String operation, Decoder<T> decoder) throws ProviderException {
        transport.acquire();
        AtomicBoolean sent = new AtomicBoolean();
        try {
            try (Response response = transport.execute(request, sent, false)) {
                DraftHttpErrors.requireSuccess(response.code());
                requireJson(response, operation);
                T result = decoder.decode(response.body().byteStream());
                observations.record(transport.providerKey + ":stream", "");
                return result;
            }
        } catch (IOException error) {
            observations.record(transport.providerKey + ":stream", "外部流服务连接或传输失败");
            throw DraftHttpErrors.connection(error, sent.get());
        } catch (ProviderException error) {
            observations.record(transport.providerKey + ":stream",
                    error.getErrorCode() == ErrorCode.PROVIDER_AUTH
                            ? "外部流服务鉴权失败" : "外部流协议或配置不可用");
            throw error;
        } finally {
            transport.release();
        }
    }

    private void requireJson(Response response, String operation) throws ProviderException {
        if (response.body() == null || response.body().contentType() == null
                || !"application".equals(response.body().contentType().type())
                || !"json".equals(response.body().contentType().subtype())) {
            throw DraftHttpErrors.protocol(operation);
        }
    }

    private interface Decoder<T> {
        T decode(java.io.InputStream input) throws IOException, ProviderException;
    }
}
