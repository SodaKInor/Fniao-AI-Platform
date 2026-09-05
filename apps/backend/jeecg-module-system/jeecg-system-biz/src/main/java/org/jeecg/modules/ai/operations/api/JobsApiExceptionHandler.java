package org.jeecg.modules.ai.operations.api;

import org.jeecg.modules.ai.asset.api.AssetController;
import org.jeecg.modules.ai.image.api.InferenceController;
import org.jeecg.modules.ai.job.api.JobApiResponse;
import org.jeecg.modules.ai.job.api.JobController;
import org.jeecg.modules.ai.job.api.mapper.JobDtoMapper;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.IdempotencyConflictException;
import org.jeecg.modules.ai.stream.api.StreamController;
import org.jeecg.modules.ai.video.api.VideoJobController;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.ai.job.api.dto.ErrorDto;
import org.jeecg.modules.ai.job.application.AiRequestException;

@RestControllerAdvice(assignableTypes={InferenceController.class,VideoJobController.class,JobController.class,AssetController.class,StreamController.class})
public final class JobsApiExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<ErrorDto>> handle(Exception failure,HttpServletResponse response) {
        if (response.isCommitted()) return null;
        ErrorCode code=ErrorCode.INTERNAL_ERROR; int status=500; String message="AI operation could not be completed";
        if (failure instanceof AiRequestException) {
            code=((AiRequestException)failure).getCode(); message=failure.getMessage(); status=status(code);
        } else if (failure instanceof IdempotencyConflictException) {
            code=ErrorCode.IDEMPOTENCY_CONFLICT; status=409; message="Idempotency key has different input";
        } else if (failure instanceof RejectedExecutionException || failure instanceof MaxUploadSizeExceededException) {
            code=ErrorCode.LIMIT_EXCEEDED; status=failure instanceof MaxUploadSizeExceededException ? 413 : 429; message="Capacity limit reached";
        } else if (failure instanceof IllegalArgumentException || failure instanceof HttpMessageNotReadableException
                || failure instanceof ServletRequestBindingException || failure instanceof MethodArgumentTypeMismatchException) {
            code=ErrorCode.INVALID_REQUEST; status=400; message="Invalid request";
        }
        return JobApiResponse.of(status,new JobDtoMapper().error(code,message,null,false));
    }
    private int status(ErrorCode code) {
        switch (code) {
            case UNAUTHENTICATED: return 401;
            case FORBIDDEN: return 403;
            case NOT_FOUND: return 404;
            case ASSET_EXPIRED: return 410;
            case LIMIT_EXCEEDED: return 413;
            case UNSUPPORTED_MEDIA: return 415;
            case CAPABILITY_UNAVAILABLE: case JOB_STATE_CONFLICT: case CANCEL_NOT_SUPPORTED: return 409;
            case INVALID_REQUEST: return 400;
            default: return 500;
        }
    }
}
