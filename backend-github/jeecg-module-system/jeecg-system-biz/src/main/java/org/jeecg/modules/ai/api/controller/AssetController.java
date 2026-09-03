package org.jeecg.modules.ai.api.controller;

import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.ai.api.dto.AssetDto;
import org.jeecg.modules.ai.api.mapper.assets.AssetDtoMapper;
import org.jeecg.modules.ai.api.mapper.jobs.*;
import org.jeecg.modules.ai.application.assets.AssetService;
import org.jeecg.modules.ai.domain.*;

@RestController
@RequestMapping("/ai/v1/assets")
public final class AssetController {
    private final AssetService assets;
    private final AssetDtoMapper mapper=new AssetDtoMapper();
    public AssetController(AssetService assets) { this.assets=assets; }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result<AssetDto>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String owner=JobApiIdentity.owner();
        ContentMetadata metadata=new ContentMetadata(file.getOriginalFilename(),file.getContentType(),file.getSize(),null);
        try (InputStream in=file.getInputStream()) { return JobApiResponse.of(201,mapper.map(assets.upload(owner,metadata,in))); }
    }
    @GetMapping("/{id}/content")
    public void download(@PathVariable String id,HttpServletResponse response) throws IOException {
        String owner=JobApiIdentity.owner();
        Asset asset=assets.owned(id,owner);
        try (InputStream in=assets.open(id,owner)) {
            response.setContentType(asset.getMediaType()); response.setContentLengthLong(asset.getStored().getSizeBytes());
            response.setHeader("Cache-Control","private, no-store"); response.setHeader("X-Content-Type-Options","nosniff");
            response.setHeader("Content-Disposition",ContentDisposition.attachment()
                    .filename(asset.getFileName(),StandardCharsets.UTF_8).build().toString());
            byte[] buffer=new byte[65536]; int n;
            while ((n=in.read(buffer))!=-1) response.getOutputStream().write(buffer,0,n);
        } catch (IOException e) {
            if (!response.isCommitted()) { response.reset(); throw e; }
            // A started binary response cannot be replaced or appended with JSON.
            try { response.getOutputStream().close(); } catch (IOException ignored) { }
        }
    }
}
