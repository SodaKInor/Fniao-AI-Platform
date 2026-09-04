package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Bounded polling preferences; contains no provider or RTSP configuration. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class StreamParametersDto {
    private Integer maxEventsPerPoll;
    private Long pollIntervalMillis;
    private Boolean includeSnapshots;

    public Integer getMaxEventsPerPoll() {
        return maxEventsPerPoll;
    }

    public void setMaxEventsPerPoll(Integer maxEventsPerPoll) {
        this.maxEventsPerPoll = maxEventsPerPoll;
    }

    public Long getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    public void setPollIntervalMillis(Long pollIntervalMillis) {
        this.pollIntervalMillis = pollIntervalMillis;
    }

    public Boolean getIncludeSnapshots() {
        return includeSnapshots;
    }

    public void setIncludeSnapshots(Boolean includeSnapshots) {
        this.includeSnapshots = includeSnapshots;
    }
}
