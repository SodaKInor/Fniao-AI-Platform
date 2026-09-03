package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Business API shape only; constraints and optional-field rules are in business.openapi.json.
 * Use existing Result<T>. Mappers validate values and omit absent optional response fields.
 * No provider wire payload, credentials or persistence entity may be attached.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JobPageDto {
    private List<JobDto> items;
    private String nextCursor;

    public List<JobDto> getItems() {
        return items;
    }

    public void setItems(List<JobDto> items) {
        this.items = items;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}
