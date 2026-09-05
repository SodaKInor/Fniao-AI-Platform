package org.jeecg.modules.ai.video.domain;

import java.math.BigDecimal;

/** Durable video event; snapshotAssetId is a local authorized asset, never a provider URL. */
public final class VideoEvent {
    private final String eventId;
    private final long offsetMillis;
    private final String eventType;
    private final BigDecimal score;
    private final String snapshotAssetId;

    public VideoEvent(
            String eventId,
            long offsetMillis,
            String eventType,
            BigDecimal score,
            String snapshotAssetId) {
        this.eventId = eventId;
        this.offsetMillis = offsetMillis;
        this.eventType = eventType;
        this.score = score;
        this.snapshotAssetId = snapshotAssetId;
    }

    public String getEventId() {
        return eventId;
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

    public String getSnapshotAssetId() {
        return snapshotAssetId;
    }
}
