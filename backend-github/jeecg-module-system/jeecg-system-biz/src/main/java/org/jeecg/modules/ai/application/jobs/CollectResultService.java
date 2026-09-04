package org.jeecg.modules.ai.application.jobs;

import java.io.*;
import java.time.*;
import java.util.*;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;
import org.jeecg.modules.ai.application.assets.AssetService;

public final class CollectResultService {
    private final ProviderArtifactReader reader;
    private final AssetService assets;
    private final Clock clock;
    private final long maxOutputBytes;
    private final CapabilityRepository capabilities;

    public CollectResultService(ProviderArtifactReader reader,AssetService assets,Clock clock,long maxOutputBytes,CapabilityRepository capabilities) {
        this.reader=reader; this.assets=assets; this.clock=clock; this.maxOutputBytes=maxOutputBytes; this.capabilities=capabilities;
    }

    public void recover(JobRepository jobs,JobRecord record) {
        if (record.getState()!=JobState.FETCHING_RESULT || record.getDispatchToken()==null)
            throw new IllegalArgumentException("Job is not recoverable");
        ClaimedJob claim=new ClaimedJob(jobs,clock,record);
        if (record.getRequest().getJobType()==JobType.VIDEO_FILE_ANALYSIS) collectVideo(claim);
        else collect(claim);
    }

    void collect(ClaimedJob claim) {
        JobRequest request=claim.record().getRequest();
        ProviderResult checkpoint=claim.record().getProviderResult();
        long outputLimit=capabilities.find(request.getCapability().getCapabilityCode())
                .map(c -> Math.min(maxOutputBytes,c.getMaxOutputBytes())).orElse(maxOutputBytes);
        ErrorCode error=ErrorCode.ARTIFACT_TRANSFER;
        for (int attempt=0; attempt<3; attempt++) {
            if (attempt>0 && !pause(attempt*1000L,checkpoint)) { error=ErrorCode.ARTIFACT_EXPIRED; break; }
            try {
                List<String> ids=new ArrayList<>();
                for (int index=0; index<checkpoint.getArtifacts().size(); index++) {
                    ProviderArtifact artifact=checkpoint.getArtifacts().get(index);
                    String assetId="out_"+request.getRequestId()+"_"+index;
                    if (!assets.collected(assetId,request.getOwnerId()).isPresent()) {
                        if (expired(artifact,clock.instant())) throw new AiRequestException(ErrorCode.ARTIFACT_EXPIRED,"Artifact expired");
                        try (InputStream input=reader.open(request.getCapability(),artifact,outputLimit)) {
                            assets.collect(assetId,request.getOwnerId(),artifact.getMetadata(),input,outputLimit);
                        }
                    }
                    assets.owned(assetId,request.getOwnerId());
                    ids.add(assetId);
                }
                InferenceResult result=new InferenceResult(checkpoint.isSimulated(),checkpoint.getData(),ids);
                claim.move(JobState.SUCCEEDED,checkpoint,result,null);
                return;
            } catch (ProviderException e) {
                error=e.getErrorCode()==ErrorCode.ARTIFACT_EXPIRED ? ErrorCode.ARTIFACT_EXPIRED : ErrorCode.ARTIFACT_TRANSFER;
            } catch (AiRequestException e) {
                error=e.getCode()==ErrorCode.ARTIFACT_EXPIRED ? ErrorCode.ARTIFACT_EXPIRED : ErrorCode.ARTIFACT_TRANSFER;
            } catch (IOException | IllegalArgumentException e) { error=ErrorCode.ARTIFACT_TRANSFER; }
            catch (RuntimeException e) {
                // Metadata/commit failures retain the checkpoint. Never invoke inference again.
                error=ErrorCode.ARTIFACT_TRANSFER;
            }
            claim.move(JobState.FETCHING_RESULT,checkpoint,null,new JobError(error,"Result collection incomplete",request.isSimulated()));
            if (error==ErrorCode.ARTIFACT_EXPIRED) break;
        }
        claim.move(JobState.FAILED,checkpoint,null,new JobError(error,"Could not save complete result",request.isSimulated()));
    }

    void collectVideo(ClaimedJob claim) {
        JobRequest request=claim.record().getRequest();
        VideoProviderResult checkpoint=claim.record().getVideoProviderResult();
        long outputLimit=capabilities.find(request.getCapability().getCapabilityCode())
                .map(c -> Math.min(maxOutputBytes,c.getMaxOutputBytes())).orElse(maxOutputBytes);
        ErrorCode error=ErrorCode.ARTIFACT_TRANSFER;
        for (int attempt=0;attempt<3;attempt++) {
            try {
                List<VideoEvent> events=new ArrayList<>(); List<String> snapshots=new ArrayList<>();
                for (int index=0;index<checkpoint.getEvents().size();index++) {
                    ProviderVideoEvent source=checkpoint.getEvents().get(index); String snapshotId=null;
                    if (source.getSnapshot()!=null) {
                        snapshotId="out_"+request.getRequestId()+"_s"+index;
                        collectArtifact(snapshotId,request,source.getSnapshot(),outputLimit); snapshots.add(snapshotId);
                    }
                    events.add(new VideoEvent("evt_"+request.getRequestId()+"_"+index,source.getOffsetMillis(),
                            source.getEventType(),source.getScore(),snapshotId));
                }
                String annotatedId=null;
                if (checkpoint.getAnnotatedVideo()!=null) {
                    annotatedId="out_"+request.getRequestId()+"_video";
                    collectArtifact(annotatedId,request,checkpoint.getAnnotatedVideo(),outputLimit);
                }
                claim.move(JobState.SUCCEEDED,checkpoint,
                        new VideoResult(checkpoint.isSimulated(),events,snapshots,annotatedId),null,null);
                return;
            } catch (ProviderException e) {
                error=e.getErrorCode()==ErrorCode.ARTIFACT_EXPIRED ? ErrorCode.ARTIFACT_EXPIRED : ErrorCode.ARTIFACT_TRANSFER;
            } catch (AiRequestException e) {
                error=e.getCode()==ErrorCode.ARTIFACT_EXPIRED ? ErrorCode.ARTIFACT_EXPIRED : ErrorCode.ARTIFACT_TRANSFER;
            } catch (IOException | IllegalArgumentException e) { error=ErrorCode.ARTIFACT_TRANSFER; }
            catch (RuntimeException e) { error=ErrorCode.ARTIFACT_TRANSFER; }
            claim.move(JobState.FETCHING_RESULT,checkpoint,null,
                    new JobError(error,"Video result collection incomplete",request.isSimulated()),null);
            if (error==ErrorCode.ARTIFACT_EXPIRED || !pauseVideo((attempt+1)*1000L,checkpoint)) break;
        }
        claim.move(JobState.FAILED,checkpoint,null,
                new JobError(error,"Could not save complete video result",request.isSimulated()),null);
    }

    private void collectArtifact(String assetId,JobRequest request,ProviderArtifact artifact,long limit)
            throws ProviderException,IOException {
        if (!assets.collected(assetId,request.getOwnerId()).isPresent()) {
            if (expired(artifact,clock.instant())) throw new AiRequestException(ErrorCode.ARTIFACT_EXPIRED,"Artifact expired");
            try (InputStream input=reader.open(request.getCapability(),artifact,limit)) {
                assets.collect(assetId,request.getOwnerId(),artifact.getMetadata(),input,limit);
            }
        }
        assets.owned(assetId,request.getOwnerId());
    }

    private boolean pauseVideo(long millis,VideoProviderResult checkpoint) {
        if (checkpoint.getAnnotatedVideo()!=null && expired(checkpoint.getAnnotatedVideo(),clock.instant().plusMillis(millis))) return false;
        for (ProviderVideoEvent event:checkpoint.getEvents())
            if (event.getSnapshot()!=null && expired(event.getSnapshot(),clock.instant().plusMillis(millis))) return false;
        try { Thread.sleep(millis); return true; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }

    private boolean expired(ProviderArtifact artifact,Instant at) {
        return artifact.getExpiresAt()!=null && !at.isBefore(artifact.getExpiresAt());
    }
    private boolean pause(long millis,ProviderResult checkpoint) {
        for (ProviderArtifact artifact:checkpoint.getArtifacts())
            if (expired(artifact,clock.instant().plusMillis(millis))) return false;
        try { Thread.sleep(millis); return true; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }
}
