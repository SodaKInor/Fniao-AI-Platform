package org.jeecg.modules.ai.job.domain;

import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;

/**
 * Adapter failure with sanitized diagnostic; never attach a raw credential-bearing HTTP exception.
 * Application maps certainty to local state; provider code never updates repositories.
 */
public final class ProviderException extends Exception {
    private static final long serialVersionUID = 1L;
    private final ErrorCode errorCode;
    private final ExecutionCertainty certainty;

    public ProviderException(ErrorCode errorCode, ExecutionCertainty certainty, String safeMessage) {
        super(safeMessage);
        this.errorCode = errorCode;
        this.certainty = certainty;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public ExecutionCertainty getCertainty() {
        return certainty;
    }
}
