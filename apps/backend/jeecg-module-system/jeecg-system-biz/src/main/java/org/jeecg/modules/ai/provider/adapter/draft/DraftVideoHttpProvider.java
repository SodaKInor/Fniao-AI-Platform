package org.jeecg.modules.ai.provider.adapter.draft;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Request;
import okhttp3.Response;
import org.jeecg.modules.ai.provider.adapter.ProviderObservations;
import org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.video.domain.VideoProviderRequest;
import org.jeecg.modules.ai.video.domain.VideoProviderResult;
import org.jeecg.modules.ai.video.port.VideoAnalysisProvider;

/** Real HTTP mechanics for the unconfirmed v0.2 video draft; runtime registration remains disabled. */
public final class DraftVideoHttpProvider implements VideoAnalysisProvider {
    private final DraftTransport transport;
    private final ProviderObservations observations;

    public DraftVideoHttpProvider(DraftTransport transport, ProviderObservations observations) {
        this.transport = transport;
        this.observations = observations;
    }

    @Override public VideoProviderResult analyze(VideoProviderRequest request) throws ProviderException {
        ProviderRequestChecks.validateVideo(request, transport.videoInputLimit);
        transport.checkBinding(request.getCapability(), "video-file-analysis.v1", "video-draft-v0.2");
        transport.acquire();
        AtomicBoolean sent = new AtomicBoolean();
        try {
            Request.Builder http = new Request.Builder().url(transport.endpoint.videoJob())
                    .post(new DraftVideoRequestEncoder().encode(
                            request, transport.videoInputLimit, transport.transferMillis));
            try (Response response = transport.execute(http, sent, false)) {
                DraftHttpErrors.requireSuccess(response.code());
                requireJson(response);
                VideoProviderResult result = new DraftVideoResponseDecoder(
                        transport.outputLimit, transport.videoOutputLimit)
                        .decode(response.body().byteStream(), request);
                observations.record(transport.providerKey + ":video", "");
                return result;
            }
        } catch (IOException error) {
            observations.record(transport.providerKey + ":video", "外部视频服务连接或传输失败");
            throw DraftHttpErrors.connection(error, sent.get());
        } catch (ProviderException error) {
            observations.record(transport.providerKey + ":video",
                    error.getErrorCode() == ErrorCode.PROVIDER_AUTH
                            ? "外部视频服务鉴权失败" : "外部视频协议或配置不可用");
            throw error;
        } finally {
            transport.release();
        }
    }

    private void requireJson(Response response) throws ProviderException {
        if (response.body() == null || response.body().contentType() == null
                || !"application".equals(response.body().contentType().type())
                || !"json".equals(response.body().contentType().subtype())) {
            throw DraftHttpErrors.protocol("video");
        }
    }
}
