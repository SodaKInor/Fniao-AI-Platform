package org.jeecg.modules.ai.video.api;

import org.jeecg.common.api.vo.Result;
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
import org.jeecg.modules.ai.video.api.dto.VideoJobRequestDto;
import org.jeecg.modules.ai.video.api.dto.VideoParametersDto;
import org.jeecg.modules.ai.video.domain.VideoParameters;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/v1")
public class VideoJobController {
    private final SubmitInferenceService submit;
    private final JobQueryService query;
    private final JobDtoMapper mapper = new JobDtoMapper();

    public VideoJobController(SubmitInferenceService submit, JobQueryService query) {
        this.submit = submit;
        this.query = query;
    }

    @PostMapping("/video-jobs")
    public ResponseEntity<Result<JobDto>> submit(@RequestHeader("Idempotency-Key") String key,
            @RequestBody VideoJobRequestDto body) {
        if (body == null || body.getParameters() == null || body.getParameters().getThreshold() == null
                || body.getParameters().getSampleIntervalMillis() == null || body.getParameters().getMaxEvents() == null
                || body.getParameters().getIncludeSnapshots() == null || body.getParameters().getAnnotate() == null) {
            throw new AiRequestException(ErrorCode.INVALID_REQUEST, "Required video parameters are missing");
        }
        VideoParametersDto parameters = body.getParameters();
        JobSubmission accepted = submit.submitVideo(JobApiIdentity.owner(), key, body.getCapabilityCode(),
                body.getInputAssetId(), new VideoParameters(parameters.getThreshold(),
                        parameters.getSampleIntervalMillis(), parameters.getMaxEvents(),
                        parameters.getIncludeSnapshots(), parameters.getAnnotate()), body.getRetryOfRequestId());
        JobRecord job = accepted.getJob();
        return JobApiResponse.of(!accepted.isCreated() && JobQueryService.finished(job) ? 200 : 202,
                mapper.map(job, query.resultAssets(job)));
    }
}
