package org.jeecg.modules.ai.image.api;

import org.jeecg.modules.ai.image.api.dto.DetectionParametersDto;
import org.jeecg.modules.ai.image.api.dto.InferenceRequestDto;
import org.jeecg.modules.ai.image.domain.DetectionParameters;
import org.jeecg.modules.ai.job.api.JobApiIdentity;
import org.jeecg.modules.ai.job.api.JobApiResponse;
import org.jeecg.modules.ai.job.api.dto.JobDto;
import org.jeecg.modules.ai.job.api.mapper.JobDtoMapper;
import org.jeecg.modules.ai.job.application.AiRequestException;
import org.jeecg.modules.ai.job.application.JobQueryService;
import org.jeecg.modules.ai.job.application.SubmitInferenceService;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.JobRecord;
import org.jeecg.modules.ai.job.domain.JobSubmission;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.jeecg.common.api.vo.Result;

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
