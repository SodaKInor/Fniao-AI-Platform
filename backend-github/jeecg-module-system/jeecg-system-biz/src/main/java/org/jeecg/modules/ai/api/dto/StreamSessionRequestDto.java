package org.jeecg.modules.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Only an opaque local streamSourceId may cross the browser boundary. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class StreamSessionRequestDto {
    private String capabilityCode;
    private String streamSourceId;
    private StreamParametersDto parameters;

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public void setCapabilityCode(String capabilityCode) {
        this.capabilityCode = capabilityCode;
    }

    public String getStreamSourceId() {
        return streamSourceId;
    }

    public void setStreamSourceId(String streamSourceId) {
        this.streamSourceId = streamSourceId;
    }

    public StreamParametersDto getParameters() {
        return parameters;
    }

    public void setParameters(StreamParametersDto parameters) {
        this.parameters = parameters;
    }
}
