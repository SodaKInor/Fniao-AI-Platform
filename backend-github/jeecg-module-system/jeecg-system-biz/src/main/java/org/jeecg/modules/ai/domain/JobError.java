package org.jeecg.modules.ai.domain;

/**
 * Safe persisted diagnostic. Contains no credentials, supplier raw response or private path.
 * A result transfer error must not be represented as an empty successful inference.
 */
public final class JobError {
    private final ErrorCode code;
    private final String message;
    private final boolean simulated;

    public JobError(
            ErrorCode code,
            String message,
            boolean simulated) {
        this.code = code;
        this.message = message;
        this.simulated = simulated;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSimulated() {
        return simulated;
    }
}
