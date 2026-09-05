package org.jeecg.modules.ai.stream.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Stable cursor page; items may be empty without indicating failure. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class StreamEventPageDto {
    private String sessionId;
    private List<StreamEventDto> items;
    private String nextCursor;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<StreamEventDto> getItems() {
        return items;
    }

    public void setItems(List<StreamEventDto> items) {
        this.items = items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
