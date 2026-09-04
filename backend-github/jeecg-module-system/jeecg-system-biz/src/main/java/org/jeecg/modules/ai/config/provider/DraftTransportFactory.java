package org.jeecg.modules.ai.config.provider;

import java.util.concurrent.TimeUnit;
import okhttp3.Authenticator;
import okhttp3.OkHttpClient;
import org.jeecg.modules.ai.client.draft.DraftEndpoint;
import org.jeecg.modules.ai.client.draft.DraftTransport;

/** Explicit construction for protocol fixtures and the isolated development stub; never a production fallback. */
public final class DraftTransportFactory {
    private DraftTransportFactory() { }

    public static DraftTransport create(ProviderProperties properties, boolean loopbackFixture) {
        if (!properties.validLimits() || properties.getProviderKey() == null || properties.getProviderKey().isEmpty()) {
            throw new IllegalArgumentException("Invalid provider configuration");
        }
        DraftEndpoint endpoint = new DraftEndpoint(properties.getBaseUrl(), properties.getApprovedOrigin(),
                properties.getApiPath(), properties.getVideoApiPath(), properties.getStreamSourcesPath(),
                properties.getStreamSessionsPath(), loopbackFixture);
        OkHttpClient.Builder http = new OkHttpClient.Builder()
                .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
                .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
                .proxy(java.net.Proxy.NO_PROXY)
                .addNetworkInterceptor(chain -> {
                    okhttp3.Response response = chain.proceed(chain.request());
                    if (chain.request().body() == null && (response.code() == 408 || response.code() == 503 || response.code() == 421)) {
                        response.close();
                        throw new java.io.IOException("Artifact HTTP follow-up refused");
                    }
                    return response;
                })
                .connectTimeout(properties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getTransferTimeoutMs(), TimeUnit.MILLISECONDS)
                .callTimeout(properties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        ProviderTrust.configure(http, properties.getCaFile());
        ProviderCredentials.read(properties.getTokenFile());
        return new DraftTransport(http.build(), endpoint, properties.getProviderKey(),
                () -> ProviderCredentials.read(properties.getTokenFile()), properties.getMaxInflight(),
                properties.getTransferTimeoutMs(), properties.getUploadMaxBytes(), properties.getOutputMaxBytes(),
                properties.getVideoUploadMaxBytes(), properties.getVideoOutputMaxBytes());
    }
}
