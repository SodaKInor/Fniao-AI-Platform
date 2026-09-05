package org.jeecg.modules.ai.capability.domain;

/**
 * Confirmed file-job features only. All three MUST be false for an unconfirmed provider draft.
 * Application code gates optional query/cancel control on this immutable capability snapshot.
 */
public final class ProviderFeatures {
    private final boolean query;
    private final boolean cancel;
    private final boolean deduplication;

    public ProviderFeatures(
            boolean query,
            boolean cancel,
            boolean deduplication) {
        this.query = query;
        this.cancel = cancel;
        this.deduplication = deduplication;
    }

    public boolean isQuery() {
        return query;
    }

    public boolean isCancel() {
        return cancel;
    }

    public boolean isDeduplication() {
        return deduplication;
    }
}
