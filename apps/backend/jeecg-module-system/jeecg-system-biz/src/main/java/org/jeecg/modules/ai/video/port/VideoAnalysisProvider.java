package org.jeecg.modules.ai.video.port;

import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.video.domain.VideoProviderRequest;
import org.jeecg.modules.ai.video.domain.VideoProviderResult;

/** Upload-video provider boundary. Implementations must disable transparent POST retries. */
public interface VideoAnalysisProvider {
    VideoProviderResult analyze(VideoProviderRequest request) throws ProviderException;
}
