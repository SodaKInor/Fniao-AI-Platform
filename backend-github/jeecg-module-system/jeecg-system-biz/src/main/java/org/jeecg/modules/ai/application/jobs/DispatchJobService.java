package org.jeecg.modules.ai.application.jobs;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;
import org.jeecg.modules.ai.application.assets.AssetService;

public final class DispatchJobService {
    private final JobRepository jobs;
    private final InferenceProvider provider;
    private final AssetService assets;
    private final CollectResultService collector;
    private final Clock clock;

    public DispatchJobService(JobRepository jobs,InferenceProvider provider,AssetService assets,
                              CollectResultService collector,Clock clock) {
        this.jobs=jobs; this.provider=provider; this.assets=assets; this.collector=collector; this.clock=clock;
    }

    public void dispatch(JobRecord candidate) {
        JobRequest request=candidate.getRequest();
        Optional<JobRecord> won=jobs.claimPending(request.getRequestId(),candidate.getVersion(),
                UUID.randomUUID().toString(),Instant.ofEpochMilli(clock.millis()));
        if (!won.isPresent()) return;
        ClaimedJob claim=new ClaimedJob(jobs,clock,won.get());
        ProviderResult result;
        boolean enteredProvider=false;
        try {
            Asset asset=assets.owned(request.getInputAssetId(),request.getOwnerId());
            ContentMetadata metadata=new ContentMetadata(asset.getFileName(),asset.getMediaType(),
                    asset.getStored().getSizeBytes(),asset.getStored().getSha256());
            claim.move(JobState.WAITING,null,null,null);
            enteredProvider=true;
            result=provider.infer(new ProviderRequest(request.getRequestId(),request.getCapability(),request.getParameters(),
                    metadata,assets.source(asset.getAssetId(),request.getOwnerId())));
            new ProviderResultValidator().validate(request,result);
        } catch (ProviderException e) {
            JobState state=e.getCertainty()==ExecutionCertainty.UNKNOWN ? JobState.UNKNOWN : JobState.FAILED;
            claim.move(state,null,null,new JobError(e.getErrorCode(),"Provider call did not complete",request.isSimulated()));
            return;
        } catch (RuntimeException e) {
            ErrorCode code=e instanceof AiRequestException ? ((AiRequestException)e).getCode() : ErrorCode.INTERNAL_ERROR;
            claim.move(enteredProvider ? JobState.UNKNOWN : JobState.FAILED,null,null,
                    new JobError(code,"Inference could not be completed",request.isSimulated()));
            return;
        }
        claim.move(JobState.FETCHING_RESULT,result,null,null);
        collector.collect(claim);
    }
}
