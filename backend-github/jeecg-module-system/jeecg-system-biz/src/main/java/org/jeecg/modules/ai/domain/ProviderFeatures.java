package org.jeecg.modules.ai.domain;

/**
 * Confirmed features only. All three MUST be false for the unconfirmed synchronous draft.
 * No optional operation is exposed in the initial provider ports; additions require contract review.
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
