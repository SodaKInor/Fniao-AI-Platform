package org.jeecg.modules.ai.api.mapper.jobs;

import java.util.*;
import java.util.stream.Collectors;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.api.dto.*;
import org.jeecg.modules.ai.api.mapper.assets.AssetDtoMapper;

public final class JobDtoMapper {
    public JobDto map(JobRecord job,List<Asset> artifacts) {
        JobRequest q=job.getRequest();
        JobDto d=new JobDto();
        d.setRequestId(q.getRequestId()); d.setJobType(q.getJobType()); d.setCapabilityCode(q.getCapability().getCapabilityCode());
        d.setCapabilityVersion(q.getCapability().getCapabilityVersion()); d.setInputAssetId(q.getInputAssetId());
        if (q.getJobType()==JobType.IMAGE_DETECTION) d.setParameters(imageParameters(q.getParameters()));
        else d.setVideoParameters(videoParameters(q.getVideoParameters()));
        d.setState(job.getState()); d.setSimulated(q.isSimulated()); d.setCreatedAt(q.getCreatedAt());
        d.setUpdatedAt(job.getUpdatedAt()); d.setRetryOfRequestId(q.getRetryOfRequestId());
        if (job.getResult()!=null) {
            InferenceResultDto r=new InferenceResultDto(); r.setSimulated(job.getResult().isSimulated());
            r.setData(data(job.getResult().getData()));
            r.setArtifacts(artifacts.stream().map(new AssetDtoMapper()::map).collect(Collectors.toList()));
            d.setResult(r);
        }
        if (job.getVideoResult()!=null) d.setVideoResult(video(job.getVideoResult(),artifacts));
        if (job.getError()!=null) d.setError(error(job.getError().getCode(),job.getError().getMessage(),q.getRequestId(),job.getError().isSimulated()));
        d.setUnknownReason(job.getUnknownReason());
        return d;
    }

    private DetectionParametersDto imageParameters(DetectionParameters value) {
        DetectionParametersDto p=new DetectionParametersDto(); p.setThreshold(value.getThreshold());
        p.setMaxDetections(value.getMaxDetections()); p.setAnnotate(value.isAnnotate()); return p;
    }

    private VideoParametersDto videoParameters(VideoParameters value) {
        VideoParametersDto p=new VideoParametersDto(); p.setThreshold(value.getThreshold());
        p.setSampleIntervalMillis(value.getSampleIntervalMillis()); p.setMaxEvents(value.getMaxEvents());
        p.setIncludeSnapshots(value.isIncludeSnapshots()); p.setAnnotate(value.isAnnotate()); return p;
    }

    private VideoResultDto video(VideoResult value,List<Asset> assets) {
        Map<String,Asset> byId=new HashMap<>(); for (Asset asset:assets) byId.put(asset.getAssetId(),asset);
        AssetDtoMapper mapper=new AssetDtoMapper(); VideoResultDto result=new VideoResultDto();
        result.setResultType(value.getResultType()); result.setSimulated(value.isSimulated());
        List<VideoEventDto> events=new ArrayList<>();
        for (VideoEvent event:value.getEvents()) {
            VideoEventDto dto=new VideoEventDto(); dto.setEventId(event.getEventId());
            dto.setOffsetMillis(event.getOffsetMillis()); dto.setEventType(event.getEventType());
            dto.setScore(event.getScore()); dto.setSnapshotAssetId(event.getSnapshotAssetId()); events.add(dto);
        }
        result.setEvents(events);
        result.setSnapshots(value.getSnapshotAssetIds().stream().map(byId::get)
                .filter(Objects::nonNull).map(mapper::map).collect(Collectors.toList()));
        if (value.getAnnotatedVideoAssetId()!=null && byId.containsKey(value.getAnnotatedVideoAssetId()))
            result.setAnnotatedVideo(mapper.map(byId.get(value.getAnnotatedVideoAssetId())));
        return result;
    }

    private DetectionDataDto data(DetectionData value) {
        DetectionDataDto d=new DetectionDataDto(); d.setSchemaVersion(value.getSchemaVersion());
        d.setImageWidth(value.getImageWidth()); d.setImageHeight(value.getImageHeight());
        List<DetectionDto> detections=new ArrayList<>();
        for (Detection source:value.getDetections()) {
            DetectionDto target=new DetectionDto(); target.setLabel(source.getLabel()); target.setScore(source.getScore());
            BoundingBox box=source.getBox(); BoundingBoxDto b=new BoundingBoxDto();
            b.setX(box.getX()); b.setY(box.getY()); b.setWidth(box.getWidth()); b.setHeight(box.getHeight());
            target.setBox(b); detections.add(target);
        }
        d.setDetections(detections); return d;
    }

    public ErrorDto error(ErrorCode code,String message,String requestId,boolean simulated) {
        ErrorDto d=new ErrorDto(); d.setErrorCode(code); d.setMessage(message); d.setRequestId(requestId); d.setSimulated(simulated);
        return d;
    }
}
