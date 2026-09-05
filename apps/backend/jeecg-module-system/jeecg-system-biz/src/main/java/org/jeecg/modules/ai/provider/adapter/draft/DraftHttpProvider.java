package org.jeecg.modules.ai.provider.adapter.draft;

import org.jeecg.modules.ai.image.domain.ProviderRequest;
import org.jeecg.modules.ai.image.domain.ProviderResult;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.job.domain.ProviderException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Request;
import okhttp3.Response;
import org.jeecg.modules.ai.provider.adapter.ProviderObservations;
import org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks;
import org.jeecg.modules.ai.image.port.InferenceProvider;

/** Unconfirmed wire adapter. Production mode routing never enables this draft. */
public final class DraftHttpProvider implements InferenceProvider {
    private final DraftTransport transport;
    private final ProviderObservations observations;

    public DraftHttpProvider(DraftTransport transport, ProviderObservations observations) {
        this.transport = transport;
        this.observations = observations;
    }

    @Override public ProviderResult infer(ProviderRequest request) throws ProviderException {
        ProviderRequestChecks.validate(request, transport.inputLimit);
        transport.checkBinding(request.getCapability());
        transport.acquire();
        AtomicBoolean sent = new AtomicBoolean();
        try {
            Request.Builder http = new Request.Builder().url(transport.endpoint.inference())
                    .post(new DraftRequestEncoder().encode(request, transport.inputLimit, transport.transferMillis));
            try (Response response = transport.execute(http, sent, false)) {
                checkStatus(response.code());
                if (response.body() == null || response.body().contentType() == null
                        || !"application".equals(response.body().contentType().type())
                        || !"json".equals(response.body().contentType().subtype())) {
                    throw failure(ErrorCode.PROVIDER_PROTOCOL, ExecutionCertainty.UNKNOWN, "Provider response media type is invalid");
                }
                ProviderResult result = new DraftResponseDecoder(transport.outputLimit).decode(response.body().byteStream(), request);
                observations.record(transport.providerKey, "");
                return result;
            }
        } catch (IOException error) {
            ErrorCode code = error instanceof InterruptedIOException ? ErrorCode.PROVIDER_TIMEOUT : ErrorCode.PROVIDER_OFFLINE;
            observations.record(transport.providerKey, "外部服务连接或传输失败");
            throw failure(code, sent.get() ? ExecutionCertainty.UNKNOWN : ExecutionCertainty.NOT_STARTED, "Provider connection or transfer failed");
        } catch (ProviderException error) {
            observations.record(transport.providerKey, error.getErrorCode() == ErrorCode.PROVIDER_AUTH ? "外部服务鉴权失败" : "外部协议或配置不可用");
            throw error;
        } finally { transport.release(); }
    }

    private void checkStatus(int status) throws ProviderException {
        if (status == 200) return;
        if (status == 401 || status == 403) {
            throw failure(ErrorCode.PROVIDER_AUTH, ExecutionCertainty.NOT_STARTED, "Provider credential rejected");
        }
        // No draft error body, including its unconfirmed boolean, proves execution failure.
        ErrorCode code = status == 400 || status == 429 ? ErrorCode.PROVIDER_REJECTED : ErrorCode.PROVIDER_PROTOCOL;
        throw failure(code, ExecutionCertainty.UNKNOWN, "Unexpected provider HTTP status: " + status);
    }

    private ProviderException failure(ErrorCode code, ExecutionCertainty certainty, String safeMessage) {
        return new ProviderException(code, certainty, safeMessage);
    }
}
