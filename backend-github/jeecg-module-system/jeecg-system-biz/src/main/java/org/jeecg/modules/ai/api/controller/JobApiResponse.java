package org.jeecg.modules.ai.api.controller;

import org.jeecg.common.api.vo.Result;
import org.springframework.http.ResponseEntity;

public final class JobApiResponse {
    private JobApiResponse() { }
    public static <T> ResponseEntity<Result<T>> of(int status,T body) {
        Result<T> result=new Result<>();
        result.setSuccess(status<400); result.setCode(status); result.setResult(body);
        result.setMessage(status<400 ? "OK" : "Request rejected");
        return ResponseEntity.status(status).body(result);
    }
}
