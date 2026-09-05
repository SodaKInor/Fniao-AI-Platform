package org.jeecg.modules.ai.stream.domain;

/** Only CONFIRMED_STOPPED authorizes the local STOPPED terminal state. */
public enum StreamStopOutcome {
    CONFIRMED_STOPPED,
    NOT_SUPPORTED,
    CONFIRMATION_UNKNOWN
}
