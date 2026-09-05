package org.jeecg.modules.ai.job.domain;

/**
 * Atomic owner/key collision with a different canonical request digest; business HTTP 409.
 */
public final class IdempotencyConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException() {
        super("Idempotency key already belongs to a different request");
    }
}
