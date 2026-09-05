package org.jeecg.modules.ai.image.port;

import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.image.domain.ProviderRequest;
import org.jeecg.modules.ai.image.domain.ProviderResult;

/**
 * Synchronous provider boundary. Open input at most once; close on every outcome.
 * No repository access and no inference replay, including SDK/HTTP automatic retries.
 * Return a normalized complete response or a classified ProviderException; no deferred local job IDs.
 * Only the application, after claiming dispatch, may call this port.
 */
public interface InferenceProvider {
    ProviderResult infer(ProviderRequest request) throws ProviderException;
}
