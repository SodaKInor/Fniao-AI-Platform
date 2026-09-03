package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Business API shape only; constraints and optional-field rules are in business.openapi.json.
 * Use existing Result<T>. Mappers validate values and omit absent optional response fields.
 * No provider wire payload, credentials or persistence entity may be attached.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class CapabilityDto {
    private String code;
    private String version;
    private String displayName;
    private Boolean available;
    private Boolean simulated;
    private String unavailableReason;
    private List<String> inputMediaTypes;
    private Long maxInputBytes;
    private Long maxOutputBytes;
    private Long maxWaitMillis;
    private String parametersSchema;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public Boolean getSimulated() {
        return simulated;
    }

    public void setSimulated(Boolean simulated) {
        this.simulated = simulated;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }

    public List<String> getInputMediaTypes() {
        return inputMediaTypes;
    }

    public void setInputMediaTypes(List<String> inputMediaTypes) {
        this.inputMediaTypes = inputMediaTypes;
    }

    public Long getMaxInputBytes() {
        return maxInputBytes;
    }

    public void setMaxInputBytes(Long maxInputBytes) {
        this.maxInputBytes = maxInputBytes;
    }

    public Long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(Long maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public Long getMaxWaitMillis() {
        return maxWaitMillis;
    }

    public void setMaxWaitMillis(Long maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
    }

    public String getParametersSchema() {
        return parametersSchema;
    }

    public void setParametersSchema(String parametersSchema) {
        this.parametersSchema = parametersSchema;
    }
}
