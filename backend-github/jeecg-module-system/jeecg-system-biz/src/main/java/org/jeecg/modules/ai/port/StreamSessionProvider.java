package org.jeecg.modules.ai.port;

import org.jeecg.modules.ai.domain.ProviderException;
import org.jeecg.modules.ai.domain.ProviderStreamEventPage;
import org.jeecg.modules.ai.domain.ProviderStreamSession;
import org.jeecg.modules.ai.domain.ProviderStreamStartRequest;
import org.jeecg.modules.ai.domain.StreamStopResult;

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
