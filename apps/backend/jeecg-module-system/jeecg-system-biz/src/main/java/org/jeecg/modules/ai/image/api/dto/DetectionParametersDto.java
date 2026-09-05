package org.jeecg.modules.ai.image.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Business API shape only; constraints and optional-field rules are in business.openapi.json.
 * Use existing Result<T>. Mappers validate values and omit absent optional response fields.
 * No provider wire payload, credentials or persistence entity may be attached.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class DetectionParametersDto {
    private BigDecimal threshold;
    private Integer maxDetections;
    private Boolean annotate;

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public Integer getMaxDetections() {
        return maxDetections;
    }

    public void setMaxDetections(Integer maxDetections) {
        this.maxDetections = maxDetections;
    }

    public Boolean getAnnotate() {
        return annotate;
    }

    public void setAnnotate(Boolean annotate) {
        this.annotate = annotate;
    }
}
