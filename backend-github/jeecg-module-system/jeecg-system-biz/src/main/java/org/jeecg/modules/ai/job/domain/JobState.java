package org.jeecg.modules.ai.job.domain;

/**
 * Canonical business JobState values. Semantics are frozen in v1/SEMANTICS.md.
 */
public enum JobState {
    PENDING,
    DISPATCHING,
    WAITING,
    FETCHING_RESULT,
    SUCCEEDED,
    FAILED,
    UNKNOWN,
    CANCELLED
}
