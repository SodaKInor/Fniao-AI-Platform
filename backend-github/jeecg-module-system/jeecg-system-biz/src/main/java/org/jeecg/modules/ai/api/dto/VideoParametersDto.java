package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/** Strict video-file-analysis.v1 parameters; unknown JSON fields are rejected by the API converter. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class VideoParametersDto {
    private BigDecimal threshold;
    private Long sampleIntervalMillis;
    private Integer maxEvents;
    private Boolean includeSnapshots;
    private Boolean annotate;

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public Long getSampleIntervalMillis() {
        return sampleIntervalMillis;
    }

    public void setSampleIntervalMillis(Long sampleIntervalMillis) {
        this.sampleIntervalMillis = sampleIntervalMillis;
    }

    public Integer getMaxEvents() {
        return maxEvents;
    }

    public void setMaxEvents(Integer maxEvents) {
        this.maxEvents = maxEvents;
    }

    public Boolean getIncludeSnapshots() {
        return includeSnapshots;
    }

    public void setIncludeSnapshots(Boolean includeSnapshots) {
        this.includeSnapshots = includeSnapshots;
    }

    public Boolean getAnnotate() {
        return annotate;
    }

    public void setAnnotate(Boolean annotate) {
        this.annotate = annotate;
    }
}
