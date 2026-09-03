package org.jeecg.modules.ai.application.jobs;

import java.time.*;
import java.util.*;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;
import org.jeecg.modules.ai.application.assets.AssetService;

public final class SubmitInferenceService {
    private final JobRepository jobs;
    private final CapabilityRepository capabilities;
    private final AssetService assets;
    private final Clock clock;
    private final RequestFingerprint fingerprint=new RequestFingerprint();

    public SubmitInferenceService(JobRepository jobs,CapabilityRepository capabilities,AssetService assets,Clock clock) {
        this.jobs=jobs; this.capabilities=capabilities; this.assets=assets; this.clock=clock;
    }

    public JobSubmission submit(String owner,String key,String capabilityCode,String assetId,
                                DetectionParameters parameters,String retryOf) {
        if (owner==null || owner.isEmpty()) throw new AiRequestException(ErrorCode.UNAUTHENTICATED,"Login required");
        fingerprint.key(key);
        String digest=fingerprint.digest(capabilityCode,assetId,parameters,retryOf);
        Optional<JobRecord> existing=jobs.findByKeyOwned(owner,key);
        if (existing.isPresent()) {
            if (!digest.equals(existing.get().getRequest().getRequestDigest())) throw new IdempotencyConflictException();
            return new JobSubmission(existing.get(),false);
        }
        if (retryOf!=null) {
            JobRecord previous=jobs.findOwned(retryOf,owner)
                    .orElseThrow(() -> new AiRequestException(ErrorCode.NOT_FOUND,"Previous request not found"));
            if (previous.getState()!=JobState.UNKNOWN)
                throw new AiRequestException(ErrorCode.JOB_STATE_CONFLICT,"Only an unknown request may be explicitly retried");
        }
        Capability capability=capabilities.find(capabilityCode)
                .orElseThrow(() -> new AiRequestException(ErrorCode.CAPABILITY_UNAVAILABLE,"Capability unavailable"));
        if (!capability.isEnabled() || !capability.isAvailable())
            throw new AiRequestException(ErrorCode.CAPABILITY_UNAVAILABLE,"Capability unavailable");
        Asset asset=assets.owned(assetId,owner);
        if (!capability.getInputMediaTypes().contains(asset.getMediaType()))
            throw new AiRequestException(ErrorCode.UNSUPPORTED_MEDIA,"Capability does not support this media");
        if (asset.getStored().getSizeBytes()>capability.getMaxInputBytes())
            throw new AiRequestException(ErrorCode.LIMIT_EXCEEDED,"Input exceeds capability limit");
        JobRequest request=new JobRequest(UUID.randomUUID().toString(),owner,key,digest,assetId,parameters,
                capability.getSnapshot(),retryOf,capability.isSimulated(),Instant.ofEpochMilli(clock.millis()));
        return jobs.createOrGet(request);
    }
}
