package org.jeecg.modules.ai.job.application;

import org.jeecg.modules.ai.image.domain.InferenceResult;
import org.jeecg.modules.ai.image.domain.ProviderResult;
import org.jeecg.modules.ai.job.domain.JobError;
import org.jeecg.modules.ai.job.domain.JobRecord;
import org.jeecg.modules.ai.job.domain.JobRequest;
import org.jeecg.modules.ai.job.domain.JobState;
import org.jeecg.modules.ai.job.domain.JobUpdate;
import org.jeecg.modules.ai.job.domain.UnknownOperationReason;
import org.jeecg.modules.ai.video.domain.VideoProviderResult;
import org.jeecg.modules.ai.video.domain.VideoResult;

import java.time.*;
import org.jeecg.modules.ai.job.port.JobRepository;

/** Worker-local claim cursor, never shared between threads or used to grant dispatch rights. */
public final class ClaimedJob {
    private final JobRepository jobs;
    private final Clock clock;
    private JobRecord record;
    public ClaimedJob(JobRepository jobs,Clock clock,JobRecord record) { this.jobs=jobs; this.clock=clock; this.record=record; }
    public JobRecord record() { return record; }
    public void move(JobState state,ProviderResult checkpoint,InferenceResult result,JobError error) {
        move(new JobUpdate(state,checkpoint,null,result,null,error,
                state==JobState.UNKNOWN ? UnknownOperationReason.PROVIDER_RESPONSE_LOST : null,
                Instant.ofEpochMilli(clock.millis())));
    }
    public void move(JobState state,VideoProviderResult checkpoint,VideoResult result,JobError error,
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
