package org.jeecg.modules.ai.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Persisted, deduplicated event with an optional local snapshot asset. */
public final class StreamEvent {
    private final String eventId;
    private final String providerEventId;
    private final long offsetMillis;
    private final Instant occurredAt;
    private final String eventType;
    private final BigDecimal score;
    private final String snapshotAssetId;

    public StreamEvent(
            String eventId,
            String providerEventId,
            long offsetMillis,
            Instant occurredAt,
            String eventType,
            BigDecimal score,
            String snapshotAssetId) {
        this.eventId = eventId;
        this.providerEventId = providerEventId;
        this.offsetMillis = offsetMillis;
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.score = score;
        this.snapshotAssetId = snapshotAssetId;
    }

    public String getEventId() {
        return eventId;
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

    public String getSnapshotAssetId() {
        return snapshotAssetId;
    }
}
