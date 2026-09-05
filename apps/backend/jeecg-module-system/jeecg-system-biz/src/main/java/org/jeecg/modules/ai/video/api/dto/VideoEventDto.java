package org.jeecg.modules.ai.video.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** One ordered video event with an optional local authorized snapshot ID. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class VideoEventDto {
    private String eventId;
    private Long offsetMillis;
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
