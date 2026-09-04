package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;

/** One deduplicated stream event and optional authorized snapshot asset ID. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class StreamEventDto {
    private String eventId;
    private Long offsetMillis;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant occurredAt;
    private String eventType;
    private BigDecimal score;
    private String snapshotAssetId;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getOffsetMillis() {
        return offsetMillis;
    }

    public void setOffsetMillis(Long offsetMillis) {
        this.offsetMillis = offsetMillis;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getSnapshotAssetId() {
        return snapshotAssetId;
    }

    public void setSnapshotAssetId(String snapshotAssetId) {
        this.snapshotAssetId = snapshotAssetId;
    }
}
