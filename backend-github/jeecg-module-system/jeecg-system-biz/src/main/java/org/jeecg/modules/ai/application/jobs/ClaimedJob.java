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
        JobRequest q=record.getRequest();
        JobUpdate update=new JobUpdate(state,checkpoint,result,error,Instant.ofEpochMilli(clock.millis()));
        if (!jobs.updateClaimed(q.getRequestId(),record.getVersion(),record.getDispatchToken(),update))
            throw new IllegalStateException("Dispatch claim no longer current");
        record=jobs.findOwned(q.getRequestId(),q.getOwnerId()).orElseThrow(() -> new IllegalStateException("Request disappeared"));
    }
}
