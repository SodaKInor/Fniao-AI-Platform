package org.jeecg.modules.ai.job.domain;

/**
 * Evidence of provider execution, not a retry authorization.
 * A timeout, lost response, unrecognized payload or unconfirmed HTTP 500 maps to UNKNOWN.
 */
public enum ExecutionCertainty {
    NOT_STARTED,
    CONFIRMED_FAILED,
    UNKNOWN
}
