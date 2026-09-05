package org.jeecg.modules.ai.video.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Business submission contains only local asset identity and bounded parameters. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class VideoJobRequestDto {
    private String capabilityCode;
    private String inputAssetId;
    private VideoParametersDto parameters;
    private String retryOfRequestId;

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public void setCapabilityCode(String capabilityCode) {
        this.capabilityCode = capabilityCode;
    }

    public String getInputAssetId() {
        return inputAssetId;
    }

    public void setInputAssetId(String inputAssetId) {
        this.inputAssetId = inputAssetId;
    }

    public VideoParametersDto getParameters() {
        return parameters;
    }

    public void setParameters(VideoParametersDto parameters) {
        this.parameters = parameters;
    }

    public String getRetryOfRequestId() {
        return retryOfRequestId;
    }

    public void setRetryOfRequestId(String retryOfRequestId) {
        this.retryOfRequestId = retryOfRequestId;
    }
}
