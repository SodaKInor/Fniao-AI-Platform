package org.jeecg.modules.ai.api.mapper.streams;

import java.util.*;
import org.jeecg.modules.ai.api.dto.*;
import org.jeecg.modules.ai.domain.*;

public final class StreamDtoMapper {
    public StreamSourceDto source(StreamSource value) {
        StreamSourceDto dto=new StreamSourceDto(); dto.setStreamSourceId(value.getStreamSourceId());
        dto.setDisplayName(value.getDisplayName());
        boolean available=value.isEnabled() && value.getProviderSourceRef()!=null && !value.getProviderSourceRef().isEmpty();
        dto.setAvailable(available); dto.setUnavailableReason(available ? null : value.getUnavailableReason()); return dto;
    }
    public StreamSessionDto session(StreamSession value) {
        StreamSessionRequest request=value.getRequest(); StreamSessionDto dto=new StreamSessionDto();
        dto.setSessionId(request.getSessionId()); dto.setStreamSourceId(request.getStreamSourceId());
        dto.setCapabilityCode(request.getCapability().getCapabilityCode());
        dto.setCapabilityVersion(request.getCapability().getCapabilityVersion());
        StreamParameters p=request.getParameters(); StreamParametersDto parameters=new StreamParametersDto();
        parameters.setMaxEventsPerPoll(p.getMaxEventsPerPoll()); parameters.setPollIntervalMillis(p.getPollIntervalMillis());
        parameters.setIncludeSnapshots(p.isIncludeSnapshots()); dto.setParameters(parameters); dto.setState(value.getState());
        dto.setCreatedAt(request.getCreatedAt()); dto.setUpdatedAt(value.getUpdatedAt());
        dto.setUnknownReason(value.getUnknownReason());
        if (value.getError()!=null) {
            ErrorDto error=new ErrorDto(); error.setErrorCode(value.getError().getCode());
            error.setMessage(value.getError().getMessage()); error.setRequestId(request.getSessionId());
            error.setSimulated(value.getError().isSimulated()); dto.setError(error);
        }
        return dto;
    }
    public StreamEventPageDto events(StreamEventPage value) {
        StreamEventPageDto dto=new StreamEventPageDto(); dto.setSessionId(value.getSessionId());
        List<StreamEventDto> items=new ArrayList<>();
        for (StreamEvent event:value.getItems()) {
            StreamEventDto item=new StreamEventDto(); item.setEventId(event.getEventId());
            item.setOffsetMillis(event.getOffsetMillis()); item.setOccurredAt(event.getOccurredAt());
            item.setEventType(event.getEventType()); item.setScore(event.getScore());
            item.setSnapshotAssetId(event.getSnapshotAssetId()); items.add(item);
        }
        dto.setItems(items); dto.setNextCursor(value.getNextCursor()); return dto;
    }
}
