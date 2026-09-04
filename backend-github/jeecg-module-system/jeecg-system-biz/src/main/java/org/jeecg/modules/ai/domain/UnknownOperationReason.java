package org.jeecg.modules.ai.domain;

/** Why execution or a remote control operation cannot be proven. */
public enum UnknownOperationReason {
    PROVIDER_RESPONSE_LOST,
    PROVIDER_QUERY_UNAVAILABLE,
    PROVIDER_STATE_UNRECOGNIZED,
    CANCEL_CONFIRMATION_UNKNOWN,
    STOP_CONFIRMATION_UNKNOWN
}
