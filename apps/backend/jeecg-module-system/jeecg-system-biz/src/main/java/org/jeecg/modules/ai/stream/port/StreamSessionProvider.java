package org.jeecg.modules.ai.stream.port;

import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.stream.domain.ProviderStreamEventPage;
import org.jeecg.modules.ai.stream.domain.ProviderStreamSession;
import org.jeecg.modules.ai.stream.domain.ProviderStreamStartRequest;
import org.jeecg.modules.ai.stream.domain.StreamStopResult;

/** Optional methods are callable only when their real provider capability is confirmed. */
public interface StreamSessionProvider {
    ProviderStreamSession start(ProviderStreamStartRequest request) throws ProviderException;

    ProviderStreamSession getSession(String providerSessionId) throws ProviderException;

    ProviderStreamEventPage getEvents(
            String providerSessionId,
            String cursor,
            int limit) throws ProviderException;

    StreamStopResult stop(String providerSessionId) throws ProviderException;
}
