package org.jeecg.modules.ai.api.mapper.capabilities;

import java.util.List;
import java.util.stream.Collectors;
import org.jeecg.modules.ai.api.dto.CapabilityDto;
import org.jeecg.modules.ai.domain.Capability;
import org.springframework.stereotype.Component;

@Component("aiCapabilityDtoMapper")
public final class CapabilityMapper {
    public List<CapabilityDto> map(List<Capability> capabilities) {
        return capabilities.stream().map(this::map).collect(Collectors.toList());
    }

    private CapabilityDto map(Capability source) {
        CapabilityDto dto = new CapabilityDto();
        String capabilityCode = source.getSnapshot().getCapabilityCode();
        dto.setCode(capabilityCode);
        dto.setVersion(source.getSnapshot().getCapabilityVersion());
        dto.setDisplayName(source.getDisplayName());
        dto.setAvailable(source.isAvailable());
        dto.setSimulated(source.isSimulated());
        dto.setUnavailableReason(source.getUnavailableReason());
        dto.setInputMediaTypes(source.getInputMediaTypes());
        dto.setMaxInputBytes(source.getMaxInputBytes());
        dto.setMaxOutputBytes(source.getMaxOutputBytes());
        dto.setMaxWaitMillis(source.getMaxWaitMillis());
        dto.setParametersSchema(parametersSchema(capabilityCode));
        return dto;
    }

    private String parametersSchema(String capabilityCode) {
        if ("image-detection.v1".equals(capabilityCode)) return "detection.v1";
        if ("video-file-analysis.v1".equals(capabilityCode)) return "video-analysis.v1";
        if ("video-stream-analysis.v1".equals(capabilityCode)) return "stream-analysis.v1";
        return null;
    }
}
