package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Business API shape only; constraints and optional-field rules are in business.openapi.json.
 * Use existing Result<T>. Mappers validate values and omit absent optional response fields.
 * No provider wire payload, credentials or persistence entity may be attached.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class DetectionDto {
    private String label;
    private Double score;
    private BoundingBoxDto box;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public BoundingBoxDto getBox() {
        return box;
    }

    public void setBox(BoundingBoxDto box) {
        this.box = box;
    }
}
