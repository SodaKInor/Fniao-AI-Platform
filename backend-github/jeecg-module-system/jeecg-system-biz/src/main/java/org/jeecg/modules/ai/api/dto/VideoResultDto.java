package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jeecg.modules.ai.domain.ResultType;

/** Minimum result is events plus snapshots; annotatedVideo is optional. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class VideoResultDto {
    private ResultType resultType;
    private Boolean simulated;
    private List<VideoEventDto> events;
    private List<AssetDto> snapshots;
    private AssetDto annotatedVideo;

    public ResultType getResultType() {
        return resultType;
    }

    public void setResultType(ResultType resultType) {
        this.resultType = resultType;
    }

    public Boolean getSimulated() {
        return simulated;
    }

    public void setSimulated(Boolean simulated) {
        this.simulated = simulated;
    }

    public List<VideoEventDto> getEvents() {
        return events;
    }

    public void setEvents(List<VideoEventDto> events) {
        this.events = events;
    }

    public List<AssetDto> getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(List<AssetDto> snapshots) {
        this.snapshots = snapshots;
    }

    public AssetDto getAnnotatedVideo() {
        return annotatedVideo;
    }

    public void setAnnotatedVideo(AssetDto annotatedVideo) {
        this.annotatedVideo = annotatedVideo;
    }
}
