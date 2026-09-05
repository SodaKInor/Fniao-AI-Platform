package org.jeecg.modules.ai.stream.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Browser-safe source view; provider identity and connection data are deliberately absent. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class StreamSourceDto {
    private String streamSourceId;
    private String displayName;
    private Boolean available;
    private String unavailableReason;

    public String getStreamSourceId() {
        return streamSourceId;
    }

    public void setStreamSourceId(String streamSourceId) {
        this.streamSourceId = streamSourceId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }
}
