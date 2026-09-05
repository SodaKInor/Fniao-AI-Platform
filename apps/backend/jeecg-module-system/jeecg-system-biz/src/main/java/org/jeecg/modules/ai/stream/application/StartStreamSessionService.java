package org.jeecg.modules.ai.stream.application;

import org.jeecg.modules.ai.capability.domain.Capability;
import org.jeecg.modules.ai.capability.port.CapabilityRepository;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.IdempotencyConflictException;
import org.jeecg.modules.ai.stream.domain.StreamParameters;
import org.jeecg.modules.ai.stream.domain.StreamProviderFeatures;
import org.jeecg.modules.ai.stream.domain.StreamSession;
import org.jeecg.modules.ai.stream.domain.StreamSessionRequest;
import org.jeecg.modules.ai.stream.domain.StreamSessionSubmission;
import org.jeecg.modules.ai.stream.domain.StreamSource;
import org.jeecg.modules.ai.stream.port.StreamSessionRepository;
import org.jeecg.modules.ai.stream.port.StreamSourceRepository;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jeecg.modules.ai.job.application.AiRequestException;

public final class StartStreamSessionService {
    private final StreamSessionRepository sessions;
    private final StreamSourceRepository sources;
    private final CapabilityRepository capabilities;
    private final Function<Capability,StreamProviderFeatures> features;
    private final Clock clock;
    private final StreamRequestFingerprint fingerprint=new StreamRequestFingerprint();
    public StartStreamSessionService(StreamSessionRepository sessions,StreamSourceRepository sources,
            CapabilityRepository capabilities,Function<Capability,StreamProviderFeatures> features,Clock clock) {
        this.sessions=sessions; this.sources=sources; this.capabilities=capabilities; this.features=features; this.clock=clock;
    }

    public StreamSessionSubmission start(String owner,String key,String capabilityCode,String sourceId,StreamParameters parameters) {
        if (owner==null || owner.isEmpty()) throw new AiRequestException(ErrorCode.UNAUTHENTICATED,"Login required");
        fingerprint.key(key); String digest=fingerprint.digest(capabilityCode,sourceId,parameters);
        Optional<StreamSession> existing=sessions.findByKeyOwned(owner,key);
        if (existing.isPresent()) {
            if (!digest.equals(existing.get().getRequest().getRequestDigest())) throw new IdempotencyConflictException();
            return new StreamSessionSubmission(existing.get(),false);
        }
        StreamSource source=sources.findOwned(sourceId,owner)
                .orElseThrow(() -> new AiRequestException(ErrorCode.NOT_FOUND,"Stream source not found"));
        if (!source.isEnabled() || source.getProviderSourceRef()==null || source.getProviderSourceRef().isEmpty())
            throw new AiRequestException(ErrorCode.CAPABILITY_UNAVAILABLE,"Stream source is unavailable");
        Capability capability=capabilities.find(capabilityCode)
                .orElseThrow(() -> new AiRequestException(ErrorCode.CAPABILITY_UNAVAILABLE,"Capability unavailable"));
        if (!capability.isEnabled() || !capability.isAvailable())
            throw new AiRequestException(ErrorCode.CAPABILITY_UNAVAILABLE,"Capability unavailable");
        StreamProviderFeatures confirmed=features.apply(capability);
        if (confirmed==null || !confirmed.isEventQuery())
            throw new AiRequestException(ErrorCode.CAPABILITY_UNAVAILABLE,"Stream event query is unconfirmed");
        StreamSessionRequest request=new StreamSessionRequest(UUID.randomUUID().toString(),owner,key,digest,sourceId,
                capability.getSnapshot(),confirmed,parameters,clock.instant());
        return sessions.createOrGet(request);
    }
}
