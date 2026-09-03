package org.jeecg.modules.ai.api.controller;

import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.ai.api.dto.*;
import org.jeecg.modules.ai.api.mapper.jobs.*;
import org.jeecg.modules.ai.application.jobs.JobQueryService;
import org.jeecg.modules.ai.domain.*;

@RestController
@RequestMapping("/ai/v1/jobs")
public final class JobController {
    private final JobQueryService query;
    private final JobDtoMapper mapper=new JobDtoMapper();
    public JobController(JobQueryService query) { this.query=query; }
    @GetMapping("/{id}")
    public ResponseEntity<Result<JobDto>> get(@PathVariable String id) {
        JobRecord job=query.owned(id,JobApiIdentity.owner());
        return JobApiResponse.of(200,mapper.map(job,query.resultAssets(job)));
    }
    @GetMapping
    public ResponseEntity<Result<JobPageDto>> list(@RequestParam(required=false) JobState state,
            @RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit) {
        JobPage page=query.history(JobApiIdentity.owner(),state,cursor,limit);
        JobPageDto dto=new JobPageDto(); dto.setNextCursor(page.getNextCursor());
        dto.setItems(page.getItems().stream().map(j -> mapper.map(j,query.resultAssets(j))).collect(Collectors.toList()));
        return JobApiResponse.of(200,dto);
    }
}
