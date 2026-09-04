package org.jeecg.modules.ai.domain;

/** Strict provider session status after adapter conversion. */
public final class ProviderStreamSession {
    private final String providerSessionId;
    private final StreamSessionState state;
    private final String cursor;
    private final String providerVersion;

    public ProviderStreamSession(
            String providerSessionId,
            StreamSessionState state,
            String cursor,
            String providerVersion) {
        this.providerSessionId = providerSessionId;
        this.state = state;
        this.cursor = cursor;
        this.providerVersion = providerVersion;
    }

    public String getProviderSessionId() {
        return providerSessionId;
    }

    public StreamSessionState getState() {
        return state;
    }

    public String getCursor() {
        return cursor;
    }

    public String getProviderVersion() {
        return providerVersion;
    }
}
