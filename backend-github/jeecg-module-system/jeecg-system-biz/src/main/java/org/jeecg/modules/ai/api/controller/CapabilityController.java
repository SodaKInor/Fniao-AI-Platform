package org.jeecg.modules.ai.api.controller;

import java.util.List;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.ai.api.dto.CapabilityDto;
import org.jeecg.modules.ai.api.dto.ErrorDto;
import org.jeecg.modules.ai.api.mapper.capabilities.CapabilityMapper;
import org.jeecg.modules.ai.application.capabilities.CapabilityQueryService;
import org.jeecg.modules.ai.domain.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/v1/capabilities")
public class CapabilityController {
    private final CapabilityQueryService service;
    private final CapabilityMapper mapper;

    public CapabilityController(CapabilityQueryService service, CapabilityMapper mapper) { this.service = service; this.mapper = mapper; }

    @GetMapping
    public Result<List<CapabilityDto>> list() {
        return Result.OK(mapper.map(service.list(SecurityUtils.getSubject().isPermitted("ai:infer"))));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<ErrorDto>> unavailable() {
        ErrorDto error = new ErrorDto();
        error.setErrorCode(ErrorCode.INTERNAL_ERROR);
        error.setMessage("能力仓储尚未就绪");
        Result<ErrorDto> response = Result.error(500, error.getMessage());
        response.setResult(error);
        return ResponseEntity.status(500).body(response);
    }
}
