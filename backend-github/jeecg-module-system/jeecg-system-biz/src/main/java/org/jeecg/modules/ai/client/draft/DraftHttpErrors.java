package org.jeecg.modules.ai.client.draft;

import java.io.IOException;
import java.io.InterruptedIOException;
import org.jeecg.modules.ai.domain.ErrorCode;
import org.jeecg.modules.ai.domain.ExecutionCertainty;
import org.jeecg.modules.ai.domain.ProviderException;

/** Sanitized v0.2 error classification. Draft error bodies never prove execution outcome. */
final class DraftHttpErrors {
    private DraftHttpErrors() { }

    static void requireSuccess(int status) throws ProviderException {
        if (status == 200) return;
        if (status == 401 || status == 403) {
            throw failure(ErrorCode.PROVIDER_AUTH, ExecutionCertainty.NOT_STARTED,
                    "Provider credential rejected");
        }
        ErrorCode code = status == 400 || status == 404 || status == 429
                ? ErrorCode.PROVIDER_REJECTED : ErrorCode.PROVIDER_PROTOCOL;
        throw failure(code, ExecutionCertainty.UNKNOWN, "Unexpected provider HTTP status: " + status);
    }

    static ProviderException connection(IOException error, boolean sent) {
        ErrorCode code = error instanceof InterruptedIOException
                ? ErrorCode.PROVIDER_TIMEOUT : ErrorCode.PROVIDER_OFFLINE;
        return failure(code, sent ? ExecutionCertainty.UNKNOWN : ExecutionCertainty.NOT_STARTED,
                "Provider connection or transfer failed");
    }

    static ProviderException protocol(String operation) {
        return failure(ErrorCode.PROVIDER_PROTOCOL, ExecutionCertainty.UNKNOWN,
                "Provider " + operation + " response violates the draft contract");
    }

    private static ProviderException failure(
            ErrorCode code,
            ExecutionCertainty certainty,
            String safeMessage) {
        return new ProviderException(code, certainty, safeMessage);
    }
}
