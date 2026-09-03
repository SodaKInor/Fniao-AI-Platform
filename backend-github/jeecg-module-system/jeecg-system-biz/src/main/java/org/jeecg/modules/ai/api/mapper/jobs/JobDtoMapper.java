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
        d.setRequestId(q.getRequestId()); d.setCapabilityCode(q.getCapability().getCapabilityCode());
        d.setCapabilityVersion(q.getCapability().getCapabilityVersion()); d.setInputAssetId(q.getInputAssetId());
        DetectionParametersDto p=new DetectionParametersDto();
        p.setThreshold(q.getParameters().getThreshold()); p.setMaxDetections(q.getParameters().getMaxDetections());
        p.setAnnotate(q.getParameters().isAnnotate()); d.setParameters(p);
        d.setState(job.getState()); d.setSimulated(q.isSimulated()); d.setCreatedAt(q.getCreatedAt());
        d.setUpdatedAt(job.getUpdatedAt()); d.setRetryOfRequestId(q.getRetryOfRequestId());
        if (job.getResult()!=null) {
            InferenceResultDto r=new InferenceResultDto(); r.setSimulated(job.getResult().isSimulated());
            r.setData(data(job.getResult().getData()));
            r.setArtifacts(artifacts.stream().map(new AssetDtoMapper()::map).collect(Collectors.toList()));
            d.setResult(r);
        }
        if (job.getError()!=null) d.setError(error(job.getError().getCode(),job.getError().getMessage(),q.getRequestId(),job.getError().isSimulated()));
        return d;
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
