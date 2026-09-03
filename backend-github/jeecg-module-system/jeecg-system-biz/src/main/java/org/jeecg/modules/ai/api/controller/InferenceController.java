package org.jeecg.modules.ai.api.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.ai.api.dto.*;
import org.jeecg.modules.ai.api.mapper.jobs.*;
import org.jeecg.modules.ai.application.jobs.*;
import org.jeecg.modules.ai.domain.*;

@RestController
@RequestMapping("/ai/v1")
public class InferenceController {
    private final SubmitInferenceService submit;
    private final JobQueryService query;
    private final JobDtoMapper mapper=new JobDtoMapper();
    public InferenceController(SubmitInferenceService submit,JobQueryService query) { this.submit=submit; this.query=query; }

    @PostMapping("/infer")
    public ResponseEntity<Result<JobDto>> infer(@RequestHeader("Idempotency-Key") String key,
            @RequestParam(defaultValue="1500") long waitMillis,@RequestBody InferenceRequestDto body) {
        String owner=JobApiIdentity.owner();
        if (waitMillis<0 || waitMillis>1500) throw new AiRequestException(ErrorCode.INVALID_REQUEST,"Invalid wait budget");
        JobSubmission accepted=accept(owner,key,body);
        JobRecord job=query.await(accepted.getJob().getRequest().getRequestId(),owner,waitMillis);
        return JobApiResponse.of(JobQueryService.finished(job) ? 200 : 202,mapper.map(job,query.resultAssets(job)));
    }

    @PostMapping("/jobs")
    public ResponseEntity<Result<JobDto>> jobs(@RequestHeader("Idempotency-Key") String key,@RequestBody InferenceRequestDto body) {
        String owner=JobApiIdentity.owner();
        JobSubmission accepted=accept(owner,key,body);
        JobRecord job=accepted.getJob();
        return JobApiResponse.of(!accepted.isCreated() && JobQueryService.finished(job) ? 200 : 202,
                mapper.map(job,query.resultAssets(job)));
    }

    private JobSubmission accept(String owner,String key,InferenceRequestDto body) {
        if (body==null || body.getParameters()==null || body.getParameters().getThreshold()==null
                || body.getParameters().getMaxDetections()==null || body.getParameters().getAnnotate()==null)
            throw new AiRequestException(ErrorCode.INVALID_REQUEST,"Required parameters are missing");
        DetectionParametersDto p=body.getParameters();
        return submit.submit(owner,key,body.getCapabilityCode(),body.getInputAssetId(),
                new DetectionParameters(p.getThreshold(),p.getMaxDetections(),p.getAnnotate()),body.getRetryOfRequestId());
    }
}
