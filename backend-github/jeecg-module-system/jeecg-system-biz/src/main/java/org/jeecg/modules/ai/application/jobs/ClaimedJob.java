package org.jeecg.modules.ai.application.jobs;

import java.time.*;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.JobRepository;

/** Worker-local claim cursor, never shared between threads or used to grant dispatch rights. */
final class ClaimedJob {
    private final JobRepository jobs;
    private final Clock clock;
    private JobRecord record;
    ClaimedJob(JobRepository jobs,Clock clock,JobRecord record) { this.jobs=jobs; this.clock=clock; this.record=record; }
    JobRecord record() { return record; }
    void move(JobState state,ProviderResult checkpoint,InferenceResult result,JobError error) {
        move(new JobUpdate(state,checkpoint,null,result,null,error,
                state==JobState.UNKNOWN ? UnknownOperationReason.PROVIDER_RESPONSE_LOST : null,
                Instant.ofEpochMilli(clock.millis())));
    }
    void move(JobState state,VideoProviderResult checkpoint,VideoResult result,JobError error,
              UnknownOperationReason reason) {
        move(new JobUpdate(state,null,checkpoint,null,result,error,reason,Instant.ofEpochMilli(clock.millis())));
    }
    private void move(JobUpdate update) {
        JobRequest q=record.getRequest();
        if (!jobs.updateClaimed(q.getRequestId(),record.getVersion(),record.getDispatchToken(),update))
            throw new IllegalStateException("Dispatch claim no longer current");
        record=jobs.findOwned(q.getRequestId(),q.getOwnerId()).orElseThrow(() -> new IllegalStateException("Request disappeared"));
    }
}
