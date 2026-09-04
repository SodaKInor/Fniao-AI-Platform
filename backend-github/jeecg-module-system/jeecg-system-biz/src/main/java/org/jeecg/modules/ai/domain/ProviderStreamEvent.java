package org.jeecg.modules.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Strict provider event before deduplication and local snapshot persistence. */
public final class ProviderStreamEvent {
    private final String providerEventId;
    private final long offsetMillis;
    private final Instant occurredAt;
    private final String eventType;
    private final BigDecimal score;
    private final ProviderArtifact snapshot;

    public ProviderStreamEvent(
            String providerEventId,
            long offsetMillis,
            Instant occurredAt,
            String eventType,
            BigDecimal score,
            ProviderArtifact snapshot) {
        this.providerEventId = providerEventId;
        this.offsetMillis = offsetMillis;
        this.occurredAt = occurredAt;
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

    public Instant getOccurredAt() {
        return occurredAt;
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
