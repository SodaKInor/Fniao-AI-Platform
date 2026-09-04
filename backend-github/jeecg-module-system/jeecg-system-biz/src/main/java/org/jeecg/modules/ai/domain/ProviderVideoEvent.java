package org.jeecg.modules.ai.domain;

import java.math.BigDecimal;

/** Strict provider event before optional snapshot collection. */
public final class ProviderVideoEvent {
    private final String providerEventId;
    private final long offsetMillis;
    private final String eventType;
    private final BigDecimal score;
    private final ProviderArtifact snapshot;

    public ProviderVideoEvent(
            String providerEventId,
            long offsetMillis,
            String eventType,
            BigDecimal score,
            ProviderArtifact snapshot) {
        this.providerEventId = providerEventId;
        this.offsetMillis = offsetMillis;
        this.eventType = eventType;
        this.score = score;
        this.snapshot = snapshot;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public long getOffsetMillis() {
        return offsetMillis;
    }

    public String getEventType() {
        return eventType;
    }

    public BigDecimal getScore() {
        return score;
    }

    public ProviderArtifact getSnapshot() {
        return snapshot;
    }
}
