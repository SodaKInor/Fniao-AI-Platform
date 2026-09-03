package org.jeecg.modules.ai.application.jobs;

import org.jeecg.modules.ai.domain.*;

final class ProviderResultValidator {
    void validate(JobRequest request,ProviderResult result) {
        if (result==null || result.getData()==null || result.isSimulated()!=request.isSimulated()) invalid();
        DetectionData data=result.getData();
        if (!"detection.v1".equals(data.getSchemaVersion()) || data.getImageWidth()<1 || data.getImageHeight()<1 || data.getImageWidth()>4096 || data.getImageHeight()>4096
                || data.getDetections().size()>request.getParameters().getMaxDetections() || result.getArtifacts().size()>1
                || (!request.getParameters().isAnnotate() && !result.getArtifacts().isEmpty())) invalid();
        for (Detection detection:data.getDetections()) {
            if (detection==null || detection.getLabel()==null || detection.getLabel().isEmpty()
                    || detection.getLabel().codePointCount(0,detection.getLabel().length())>120 || !unit(detection.getScore()) || detection.getBox()==null) invalid();
            BoundingBox box=detection.getBox();
            if (!unit(box.getX()) || !unit(box.getY()) || !unit(box.getWidth()) || !unit(box.getHeight())
                    || box.getX()+box.getWidth()>1 || box.getY()+box.getHeight()>1) invalid();
        }
        for (ProviderArtifact artifact:result.getArtifacts())
            if (artifact==null || artifact.getMetadata()==null || artifact.getReference()==null) invalid();
    }
    private boolean unit(double value) { return Double.isFinite(value) && value>=0 && value<=1; }
    private void invalid() { throw new AiRequestException(ErrorCode.PROVIDER_PROTOCOL,"Provider result does not match the contract"); }
}
