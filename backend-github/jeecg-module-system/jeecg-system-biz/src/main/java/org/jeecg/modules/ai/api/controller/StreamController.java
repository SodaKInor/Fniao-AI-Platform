package org.jeecg.modules.ai.api.controller;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.ai.api.dto.*;
import org.jeecg.modules.ai.api.mapper.streams.StreamDtoMapper;
import org.jeecg.modules.ai.application.jobs.AiRequestException;
import org.jeecg.modules.ai.application.streams.*;
import org.jeecg.modules.ai.domain.*;

@RestController
@RequestMapping("/ai/v1")
public class StreamController {
    private final StartStreamSessionService start;
    private final StreamQueryService query;
    private final StopStreamSessionService stop;
    private final StreamDtoMapper mapper=new StreamDtoMapper();
    public StreamController(StartStreamSessionService start,StreamQueryService query,StopStreamSessionService stop) {
        this.start=start; this.query=query; this.stop=stop;
    }

    @GetMapping("/stream-sources")
    public ResponseEntity<Result<List<StreamSourceDto>>> sources() {
        List<StreamSourceDto> result=query.sources(JobApiIdentity.owner()).stream().map(mapper::source).collect(Collectors.toList());
        return JobApiResponse.of(200,result);
    }
    @PostMapping("/stream-sessions")
    public ResponseEntity<Result<StreamSessionDto>> start(@RequestHeader("Idempotency-Key") String key,
            @RequestBody StreamSessionRequestDto body) {
        if (body==null || body.getParameters()==null || body.getParameters().getMaxEventsPerPoll()==null
                || body.getParameters().getPollIntervalMillis()==null || body.getParameters().getIncludeSnapshots()==null)
            throw new AiRequestException(ErrorCode.INVALID_REQUEST,"Required stream parameters are missing");
        StreamParametersDto p=body.getParameters();
        StreamSessionSubmission result=start.start(JobApiIdentity.owner(),key,body.getCapabilityCode(),body.getStreamSourceId(),
                new StreamParameters(p.getMaxEventsPerPoll(),p.getPollIntervalMillis(),p.getIncludeSnapshots()));
        return JobApiResponse.of(!result.isCreated() && StreamQueryService.finished(result.getSession()) ? 200 : 202,
                mapper.session(result.getSession()));
    }
    @GetMapping("/stream-sessions/{id}")
    public ResponseEntity<Result<StreamSessionDto>> session(@PathVariable String id) {
        return JobApiResponse.of(200,mapper.session(query.owned(id,JobApiIdentity.owner())));
    }
    @GetMapping("/stream-sessions/{id}/events")
    public ResponseEntity<Result<StreamEventPageDto>> events(@PathVariable String id,
            @RequestParam(required=false) String cursor,@RequestParam(defaultValue="50") int limit) {
        return JobApiResponse.of(200,mapper.events(query.events(id,JobApiIdentity.owner(),cursor,limit)));
    }
    @PostMapping("/stream-sessions/{id}/stop")
    public ResponseEntity<Result<StreamSessionDto>> stop(@PathVariable String id) {
        StreamSession result=stop.stop(id,JobApiIdentity.owner());
        return JobApiResponse.of(result.getState()==StreamSessionState.STOPPED ? 200 : 202,mapper.session(result));
    }
}
