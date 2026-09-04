package org.jeecg.modules.ai.job.domain;

/**
 * Atomic create-or-return result; same owner/key with different digest must throw conflict.
 */
public final class JobSubmission {
    private final JobRecord job;
    private final boolean created;

    public JobSubmission(
            JobRecord job,
            boolean created) {
        this.job = job;
        this.created = created;
    }

    public JobRecord getJob() {
        return job;
    }

    public boolean isCreated() {
        return created;
    }
}
