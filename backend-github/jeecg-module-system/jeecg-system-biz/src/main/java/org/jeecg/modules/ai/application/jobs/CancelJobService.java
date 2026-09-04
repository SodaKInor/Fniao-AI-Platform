package org.jeecg.modules.ai.application.jobs;

import java.time.Clock;
import java.util.Optional;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.JobRepository;

/** PENDING cancellation is local and atomic; dispatched cancellation stays unsupported until confirmed. */
public final class CancelJobService {
    private final JobRepository jobs;
    private final Clock clock;
    public CancelJobService(JobRepository jobs,Clock clock) { this.jobs=jobs; this.clock=clock; }

    public JobRecord cancel(String requestId,String owner) {
        new RequestFingerprint().identifier(requestId);
        JobRecord current=jobs.findOwned(requestId,owner)
                .orElseThrow(() -> new AiRequestException(ErrorCode.NOT_FOUND,"Request not found"));
        if (current.getState()==JobState.CANCELLED) return current;
        if (current.getState()!=JobState.PENDING)
            throw new AiRequestException(ErrorCode.CANCEL_NOT_SUPPORTED,
                    "Dispatched job cancellation is not confirmed by the provider");
        Optional<JobRecord> cancelled=jobs.cancelPending(requestId,owner,current.getVersion(),clock.instant());
        if (cancelled.isPresent()) return cancelled.get();
        JobRecord raced=jobs.findOwned(requestId,owner)
                .orElseThrow(() -> new AiRequestException(ErrorCode.NOT_FOUND,"Request not found"));
        if (raced.getState()==JobState.CANCELLED) return raced;
        throw new AiRequestException(ErrorCode.CANCEL_NOT_SUPPORTED,
                "Job was dispatched before local cancellation completed");
    }
}
