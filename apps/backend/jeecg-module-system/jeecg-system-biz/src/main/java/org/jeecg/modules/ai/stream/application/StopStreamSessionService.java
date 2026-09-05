package org.jeecg.modules.ai.stream.application;

import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.stream.domain.StreamSession;
import org.jeecg.modules.ai.stream.domain.StreamSessionState;

import java.time.Clock;
import java.util.Optional;
import org.jeecg.modules.ai.job.application.AiRequestException;
import org.jeecg.modules.ai.stream.port.StreamSessionRepository;

public final class StopStreamSessionService {
    private final StreamSessionRepository sessions;
    private final Clock clock;
    public StopStreamSessionService(StreamSessionRepository sessions,Clock clock) { this.sessions=sessions; this.clock=clock; }
    public StreamSession stop(String id,String owner) {
        new StreamRequestFingerprint().id(id);
        StreamSession current=sessions.findOwned(id,owner)
                .orElseThrow(() -> new AiRequestException(ErrorCode.NOT_FOUND,"Stream session not found"));
        if (current.getState()==StreamSessionState.STOPPED || current.getState()==StreamSessionState.STOP_REQUESTED) return current;
        if (current.getState()!=StreamSessionState.PENDING && !current.getRequest().getProviderFeatures().isStop())
            throw new AiRequestException(ErrorCode.CANCEL_NOT_SUPPORTED,"Provider stream stop is not confirmed");
        Optional<StreamSession> stopped=sessions.requestStopOwned(id,owner,current.getVersion(),clock.instant());
        if (stopped.isPresent()) return stopped.get();
        StreamSession raced=sessions.findOwned(id,owner)
                .orElseThrow(() -> new AiRequestException(ErrorCode.NOT_FOUND,"Stream session not found"));
        if (raced.getState()==StreamSessionState.STOPPED || raced.getState()==StreamSessionState.STOP_REQUESTED) return raced;
        throw new AiRequestException(ErrorCode.JOB_STATE_CONFLICT,"Stream state changed before stop request");
    }
}
