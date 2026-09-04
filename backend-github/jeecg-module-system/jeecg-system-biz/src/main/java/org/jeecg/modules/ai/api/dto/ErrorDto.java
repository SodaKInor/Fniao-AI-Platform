package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jeecg.modules.ai.domain.ErrorCode;
import org.jeecg.modules.ai.domain.UnknownOperationReason;

/**
 * Business API shape only; constraints and optional-field rules are in business.openapi.json.
 * Use existing Result<T>. Mappers validate values and omit absent optional response fields.
 * No provider wire payload, credentials or persistence entity may be attached.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorDto {
    private ErrorCode errorCode;
    private String message;
    private String requestId;
    private Boolean simulated;
    private UnknownOperationReason unknownReason;

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Boolean getSimulated() {
        return simulated;
    }

    public void setSimulated(Boolean simulated) {
        this.simulated = simulated;
    }

    public UnknownOperationReason getUnknownReason() {
        return unknownReason;
    }

    public void setUnknownReason(UnknownOperationReason unknownReason) {
        this.unknownReason = unknownReason;
    }
}
