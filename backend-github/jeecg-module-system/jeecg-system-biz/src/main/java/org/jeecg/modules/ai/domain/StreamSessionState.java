package org.jeecg.modules.ai.domain;

/** File jobs and continuous stream sessions intentionally use different state machines. */
public enum StreamSessionState {
    PENDING,
    STARTING,
    RUNNING,
    STOP_REQUESTED,
    STOPPED,
    FAILED,
    UNKNOWN
}
