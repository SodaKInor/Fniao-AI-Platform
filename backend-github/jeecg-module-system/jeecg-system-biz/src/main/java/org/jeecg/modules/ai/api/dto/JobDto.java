package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import org.jeecg.modules.ai.domain.JobState;

/**
 * Business API shape only; constraints and optional-field rules are in business.openapi.json.
 * Use existing Result<T>. Mappers validate values and omit absent optional response fields.
 * No provider wire payload, credentials or persistence entity may be attached.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class JobDto {
    private String requestId;
    private String capabilityCode;
    private String capabilityVersion;
    private String inputAssetId;
    private DetectionParametersDto parameters;
    private JobState state;
    private Boolean simulated;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;
    private String retryOfRequestId;
    private InferenceResultDto result;
    private ErrorDto error;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public void setCapabilityCode(String capabilityCode) {
        this.capabilityCode = capabilityCode;
    }

    public String getCapabilityVersion() {
        return capabilityVersion;
    }

    public void setCapabilityVersion(String capabilityVersion) {
        this.capabilityVersion = capabilityVersion;
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

    public JobState getState() {
        return state;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    public Boolean getSimulated() {
        return simulated;
    }

    public void setSimulated(Boolean simulated) {
        this.simulated = simulated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRetryOfRequestId() {
        return retryOfRequestId;
    }

    public void setRetryOfRequestId(String retryOfRequestId) {
        this.retryOfRequestId = retryOfRequestId;
    }

    public InferenceResultDto getResult() {
        return result;
    }

    public void setResult(InferenceResultDto result) {
        this.result = result;
    }

    public ErrorDto getError() {
        return error;
    }

    public void setError(ErrorDto error) {
        this.error = error;
    }
}
