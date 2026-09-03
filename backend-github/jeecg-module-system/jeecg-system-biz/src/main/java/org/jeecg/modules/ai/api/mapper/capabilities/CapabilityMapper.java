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
        dto.setCode(source.getSnapshot().getCapabilityCode());
        dto.setVersion(source.getSnapshot().getCapabilityVersion());
        dto.setDisplayName(source.getDisplayName());
        dto.setAvailable(source.isAvailable());
        dto.setSimulated(source.isSimulated());
        dto.setUnavailableReason(source.getUnavailableReason());
        dto.setInputMediaTypes(source.getInputMediaTypes());
        dto.setMaxInputBytes(source.getMaxInputBytes());
        dto.setMaxOutputBytes(source.getMaxOutputBytes());
        dto.setMaxWaitMillis(source.getMaxWaitMillis());
        dto.setParametersSchema("detection.v1");
        return dto;
    }
}
