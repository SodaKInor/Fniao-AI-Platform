package org.jeecg.modules.ai.port;

import org.jeecg.modules.ai.domain.ProviderException;
import org.jeecg.modules.ai.domain.VideoProviderRequest;
import org.jeecg.modules.ai.domain.VideoProviderResult;

/** Upload-video provider boundary. Implementations must disable transparent POST retries. */
public interface VideoAnalysisProvider {
    VideoProviderResult analyze(VideoProviderRequest request) throws ProviderException;
}
