package org.jeecg.modules.ai.provider.adapter.draft;

import org.jeecg.modules.ai.capability.domain.CapabilitySnapshot;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks;
import org.jeecg.modules.ai.job.domain.ProviderException;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import okhttp3.*;

public final class DraftTransport {
    final DraftEndpoint endpoint;
    final String providerKey;
    final long inputLimit;
    final long outputLimit;
    final long videoInputLimit;
    final long videoOutputLimit;
    private final OkHttpClient client;
    private final Supplier<String> credential;
    private final Semaphore permits;
    final int transferMillis;

    public DraftTransport(OkHttpClient client, DraftEndpoint endpoint, String key, Supplier<String> credential,
            int maxInflight, int transferMillis, long inputLimit, long outputLimit) {
        this(client, endpoint, key, credential, maxInflight, transferMillis,
                inputLimit, outputLimit, inputLimit, outputLimit);
    }

    public DraftTransport(OkHttpClient client, DraftEndpoint endpoint, String key, Supplier<String> credential,
            int maxInflight, int transferMillis, long inputLimit, long outputLimit,
            long videoInputLimit, long videoOutputLimit) {
        this.client = client;
        this.endpoint = endpoint;
        this.providerKey = key;
        this.credential = credential;
        this.permits = new Semaphore(maxInflight);
        this.transferMillis = transferMillis;
        this.inputLimit = inputLimit;
        this.outputLimit = outputLimit;
        this.videoInputLimit = videoInputLimit;
        this.videoOutputLimit = videoOutputLimit;
    }

    void acquire() throws ProviderException {
        if (!permits.tryAcquire()) {
            throw new ProviderException(ErrorCode.LIMIT_EXCEEDED, ExecutionCertainty.NOT_STARTED, "Provider concurrency limit reached");
        }
    }

    void release() { permits.release(); }

    Response execute(Request.Builder request, AtomicBoolean sent, boolean artifact) throws IOException, ProviderException {
        String token;
        try { token = credential.get(); }
        catch (RuntimeException error) {
            throw new ProviderException(ErrorCode.CAPABILITY_UNAVAILABLE, ExecutionCertainty.NOT_STARTED, "Provider credential is unavailable");
        }
        OkHttpClient.Builder builder = client.newBuilder().eventListener(new EventListener() {
            @Override public void requestHeadersStart(Call call) { sent.set(true); }
        });
        if (artifact) builder.callTimeout(transferMillis, TimeUnit.MILLISECONDS).readTimeout(transferMillis, TimeUnit.MILLISECONDS);
        return builder.build().newCall(request.header("Authorization", "Bearer " + token).build()).execute();
    }

    void checkBinding(CapabilitySnapshot snapshot) throws ProviderException {
        if (!org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks.binding(snapshot, providerKey, "sync-draft-v0.1")) {
            throw org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks.unavailable("Provider binding does not match this adapter");
        }
    }

    void checkBinding(CapabilitySnapshot snapshot, String capabilityCode, String adapterId)
            throws ProviderException {
        if (!org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks.binding(
                snapshot, providerKey, adapterId, capabilityCode)) {
            throw org.jeecg.modules.ai.provider.adapter.ProviderRequestChecks.unavailable(
                    "Provider binding does not match this adapter");
        }
    }
}
