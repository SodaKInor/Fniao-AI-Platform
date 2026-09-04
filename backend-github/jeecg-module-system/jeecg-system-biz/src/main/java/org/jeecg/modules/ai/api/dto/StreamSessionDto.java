package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import org.jeecg.modules.ai.domain.StreamSessionState;
import org.jeecg.modules.ai.domain.UnknownOperationReason;

/** Browser-safe local session view; providerSessionId is deliberately absent. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class StreamSessionDto {
    private String sessionId;
    private String streamSourceId;
    private String capabilityCode;
    private String capabilityVersion;
    private StreamParametersDto parameters;
    private StreamSessionState state;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;
    private UnknownOperationReason unknownReason;
    private ErrorDto error;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getStreamSourceId() {
        return streamSourceId;
    }

    public void setStreamSourceId(String streamSourceId) {
        this.streamSourceId = streamSourceId;
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

    public StreamParametersDto getParameters() {
        return parameters;
    }

    public void setParameters(StreamParametersDto parameters) {
        this.parameters = parameters;
    }

    public StreamSessionState getState() {
        return state;
    }

    public void setState(StreamSessionState state) {
        this.state = state;
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

    public UnknownOperationReason getUnknownReason() {
        return unknownReason;
    }

    public void setUnknownReason(UnknownOperationReason unknownReason) {
        this.unknownReason = unknownReason;
    }

    public ErrorDto getError() {
        return error;
    }

    public void setError(ErrorDto error) {
        this.error = error;
    }
}
