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
    private final VideoAnalysisProvider videoProvider;
    private final AssetService assets;
    private final CollectResultService collector;
    private final Clock clock;

    public DispatchJobService(JobRepository jobs,InferenceProvider provider,AssetService assets,
                              CollectResultService collector,Clock clock) {
        this(jobs,provider,null,assets,collector,clock);
    }

    public DispatchJobService(JobRepository jobs,InferenceProvider provider,VideoAnalysisProvider videoProvider,
                              AssetService assets,CollectResultService collector,Clock clock) {
        this.jobs=jobs; this.provider=provider; this.videoProvider=videoProvider;
        this.assets=assets; this.collector=collector; this.clock=clock;
    }

    public void dispatch(JobRecord candidate) {
        JobRequest request=candidate.getRequest();
        Optional<JobRecord> won=jobs.claimPending(request.getRequestId(),candidate.getVersion(),
                UUID.randomUUID().toString(),Instant.ofEpochMilli(clock.millis()));
        if (!won.isPresent()) return;
        ClaimedJob claim=new ClaimedJob(jobs,clock,won.get());
        boolean enteredProvider=false;
        try {
            Asset asset=assets.owned(request.getInputAssetId(),request.getOwnerId());
            ContentMetadata metadata=new ContentMetadata(asset.getFileName(),asset.getMediaType(),
                    asset.getStored().getSizeBytes(),asset.getStored().getSha256());
            claim.move(JobState.WAITING,null,null,null);
            if (request.getJobType()==JobType.VIDEO_FILE_ANALYSIS) {
                if (videoProvider==null) throw new AiRequestException(ErrorCode.CAPABILITY_UNAVAILABLE,"Video provider unavailable");
                enteredProvider=true;
                VideoProviderResult result=videoProvider.analyze(new VideoProviderRequest(request.getRequestId(),
                        request.getCapability(),request.getVideoParameters(),metadata,
                        assets.source(asset.getAssetId(),request.getOwnerId())));
                new VideoProviderResultValidator().validate(request,result);
                claim.move(JobState.FETCHING_RESULT,result,null,null,null);
                collector.collectVideo(claim); return;
            }
            if (provider==null) throw new AiRequestException(ErrorCode.CAPABILITY_UNAVAILABLE,"Image provider unavailable");
            enteredProvider=true;
            ProviderResult result=provider.infer(new ProviderRequest(request.getRequestId(),request.getCapability(),
                    request.getParameters(),metadata,assets.source(asset.getAssetId(),request.getOwnerId())));
            new ProviderResultValidator().validate(request,result);
            claim.move(JobState.FETCHING_RESULT,result,null,null);
            collector.collect(claim); return;
        } catch (ProviderException e) {
            JobState state=e.getCertainty()==ExecutionCertainty.UNKNOWN ? JobState.UNKNOWN : JobState.FAILED;
            if (request.getJobType()==JobType.VIDEO_FILE_ANALYSIS)
                claim.move(state,(VideoProviderResult)null,null,
                        new JobError(e.getErrorCode(),"Provider call did not complete",request.isSimulated()),
                        state==JobState.UNKNOWN ? UnknownOperationReason.PROVIDER_RESPONSE_LOST : null);
            else claim.move(state,(ProviderResult)null,null,
                    new JobError(e.getErrorCode(),"Provider call did not complete",request.isSimulated()));
            return;
        } catch (RuntimeException e) {
            ErrorCode code=e instanceof AiRequestException ? ((AiRequestException)e).getCode() : ErrorCode.INTERNAL_ERROR;
            JobState state=enteredProvider ? JobState.UNKNOWN : JobState.FAILED;
            JobError error=new JobError(code,"Inference could not be completed",request.isSimulated());
            if (request.getJobType()==JobType.VIDEO_FILE_ANALYSIS)
                claim.move(state,(VideoProviderResult)null,null,error,
                        state==JobState.UNKNOWN ? UnknownOperationReason.PROVIDER_RESPONSE_LOST : null);
            else claim.move(state,(ProviderResult)null,null,error);
            return;
        }
    }
}
