package org.jeecg.modules.ai.application.jobs;

import org.jeecg.modules.ai.domain.ErrorCode;

/** Safe use-case failure; transport mapping is kept in the API layer. */
public final class AiRequestException extends RuntimeException {
    private final ErrorCode code;
    public AiRequestException(ErrorCode code, String message) { super(message); this.code=code; }
    public ErrorCode getCode() { return code; }
}
