package org.jeecg.modules.ai.image.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Business API shape only; constraints and optional-field rules are in business.openapi.json.
 * Use existing Result<T>. Mappers validate values and omit absent optional response fields.
 * No provider wire payload, credentials or persistence entity may be attached.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InferenceRequestDto {
    private String capabilityCode;
    private String inputAssetId;
    private DetectionParametersDto parameters;
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

    public DetectionParametersDto getParameters() {
        return parameters;
    }

    public void setParameters(DetectionParametersDto parameters) {
        this.parameters = parameters;
    }

    public String getRetryOfRequestId() {
        return retryOfRequestId;
    }

    public void setRetryOfRequestId(String retryOfRequestId) {
        this.retryOfRequestId = retryOfRequestId;
    }
}
