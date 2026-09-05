package org.jeecg.modules.ai.provider.adapter;

import java.util.function.Supplier;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.video.domain.VideoProviderRequest;
import org.jeecg.modules.ai.video.domain.VideoProviderResult;
import org.jeecg.modules.ai.video.port.VideoAnalysisProvider;

/** Runtime video gate; the current unconfirmed draft is never promoted by a configuration flag. */
public final class ModeVideoAnalysisProvider implements VideoAnalysisProvider {
    private final Supplier<String> unavailableReason;
    private final VideoAnalysisProvider remote;

    public ModeVideoAnalysisProvider(Supplier<String> unavailableReason, VideoAnalysisProvider remote) {
        this.unavailableReason = unavailableReason;
        this.remote = remote;
    }

    @Override public VideoProviderResult analyze(VideoProviderRequest request) throws ProviderException {
        String reason = unavailableReason.get();
        if (!reason.isEmpty()) throw ProviderRequestChecks.unavailable(reason);
        if (remote == null) throw ProviderRequestChecks.unavailable("真实上传视频协议尚未确认");
        return remote.analyze(request);
    }
}
