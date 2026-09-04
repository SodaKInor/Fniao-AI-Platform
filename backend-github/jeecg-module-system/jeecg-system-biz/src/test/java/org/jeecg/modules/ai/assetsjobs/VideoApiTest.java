package org.jeecg.modules.ai.assetsjobs;

import org.jeecg.modules.ai.asset.api.AssetController;
import org.jeecg.modules.ai.asset.domain.Asset;
import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.image.api.InferenceController;
import org.jeecg.modules.ai.job.api.JobController;
import org.jeecg.modules.ai.job.application.CancelJobService;
import org.jeecg.modules.ai.job.application.DispatchJobService;
import org.jeecg.modules.ai.job.domain.JobRecord;
import org.jeecg.modules.ai.operations.api.JobsApiExceptionHandler;
import org.jeecg.modules.ai.result.application.CollectResultService;
import org.jeecg.modules.ai.result.domain.ProviderArtifact;
import org.jeecg.modules.ai.result.port.ProviderArtifactReader;
import org.jeecg.modules.ai.video.domain.ProviderVideoEvent;
import org.jeecg.modules.ai.video.domain.VideoProviderResult;
import org.jeecg.modules.ai.video.port.VideoAnalysisProvider;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.*;
import org.apache.shiro.util.ThreadContext;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.ai.operations.config.StrictInferenceJsonConverter;

public class VideoApiTest {
    private DbFixture f;
    private MockMvc mvc;
    private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule());
    @Before public void before() throws Exception {
        f=new DbFixture(); owner("a");
        mvc=MockMvcBuilders.standaloneSetup(new AssetController(f.files),new InferenceController(f.submit,f.query),
                new JobController(f.query,new CancelJobService(f.jobs,f.clock)))
                .setControllerAdvice(new JobsApiExceptionHandler())
                .setMessageConverters(new StrictInferenceJsonConverter(),new MappingJackson2HttpMessageConverter(json)).build();
    }
    @After public void after() throws Exception { ThreadContext.remove(); f.close(); }

    @Test public void uploadSubmitTimelineDownloadAndCancelUseTheUnifiedJobApi() throws Exception {
        MockMultipartFile upload=new MockMultipartFile("file","input.mp4","video/mp4",f.mp4);
        MvcResult stored=mvc.perform(multipart("/ai/v1/assets").file(upload)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.mediaType").value("video/mp4")).andReturn();
        String asset=json.readTree(stored.getResponse().getContentAsString()).path("result").path("assetId").asText();
        MvcResult accepted=mvc.perform(post("/ai/v1/video-jobs").header("Idempotency-Key","video-api-key")
                .contentType("application/json").content(body(asset))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result.jobType").value("VIDEO_FILE_ANALYSIS")).andReturn();
        String id=json.readTree(accepted.getResponse().getContentAsString()).path("result").path("requestId").asText();
        dispatch(f.query.owned(id,"a"));
        MvcResult result=mvc.perform(get("/ai/v1/jobs/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.state").value("SUCCEEDED"))
                .andExpect(jsonPath("$.result.videoResult.resultType").value("VIDEO_TIMELINE"))
                .andExpect(jsonPath("$.result.videoResult.events[0].offsetMillis").value(25))
                .andExpect(jsonPath("$.result.videoResult.snapshots[0].mediaType").value("image/png"))
                .andExpect(jsonPath("$.result.videoResult.annotatedVideo.mediaType").value("video/mp4")).andReturn();
        String response=result.getResponse().getContentAsString();
        assertFalse(response.contains("fixture:")); assertFalse(response.contains("providerRequestId"));
        String annotated=json.readTree(response).path("result").path("videoResult").path("annotatedVideo").path("assetId").asText();
        mvc.perform(get("/ai/v1/assets/"+annotated+"/content")).andExpect(status().isOk())
                .andExpect(content().bytes(f.mp4));

        MvcResult second=mvc.perform(post("/ai/v1/video-jobs").header("Idempotency-Key","video-cancel-api")
                .contentType("application/json").content(body(asset))).andExpect(status().isAccepted()).andReturn();
        String cancelId=json.readTree(second.getResponse().getContentAsString()).path("result").path("requestId").asText();
        mvc.perform(post("/ai/v1/jobs/"+cancelId+"/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.state").value("CANCELLED"));
    }

    @Test public void videoJsonAndMediaBoundariesRejectProviderControlsAndCrossOwnerUse() throws Exception {
        Asset input=f.videoInput("a"); String valid=body(input.getAssetId());
        List<String> invalid=Arrays.asList(valid.replace("\"parameters\":","\"providerUrl\":\"https://gpu\",\"parameters\":"),
                valid.replace("1000","\"1000\""),valid.replace("20","20,\"maxEvents\":21"),
                valid.replace("true","null"));
        int i=0; for (String value:invalid) mvc.perform(post("/ai/v1/video-jobs")
                .header("Idempotency-Key","bad-video-"+(i++)).contentType("application/json").content(value))
                .andExpect(status().isBadRequest());
        owner("b");
        mvc.perform(post("/ai/v1/video-jobs").header("Idempotency-Key","foreign-video")
                .contentType("application/json").content(valid)).andExpect(status().isNotFound());
        mvc.perform(get("/ai/v1/assets/"+input.getAssetId()+"/content")).andExpect(status().isNotFound());
    }

    private void dispatch(JobRecord job) {
        ProviderArtifact snapshot=new ProviderArtifact("fixture:snapshot",
                new ContentMetadata("snapshot.png","image/png",(long)f.png.length,null),f.clock.instant().plusSeconds(60));
        ProviderArtifact video=new ProviderArtifact("fixture:video",
                new ContentMetadata("annotated.mp4","video/mp4",(long)f.mp4.length,null),f.clock.instant().plusSeconds(60));
        VideoAnalysisProvider provider=request -> new VideoProviderResult(request.getRequestId(),true,
                Collections.singletonList(new ProviderVideoEvent("event_1",25,"person",new BigDecimal("0.8"),snapshot)),video);
        ProviderArtifactReader reader=(capability,artifact,limit) -> new ByteArrayInputStream(
                "video/mp4".equals(artifact.getMetadata().getMediaType()) ? f.mp4 : f.png);
        new DispatchJobService(f.jobs,null,provider,f.files,
                new CollectResultService(reader,f.files,f.clock,512L*1024*1024,f.capabilities),f.clock).dispatch(job);
    }
    private String body(String asset) {
        return "{\"capabilityCode\":\"video-file-analysis.v1\",\"inputAssetId\":\""+asset+"\","
                +"\"parameters\":{\"threshold\":0.5,\"sampleIntervalMillis\":1000,\"maxEvents\":20,"
                +"\"includeSnapshots\":true,\"annotate\":true}}";
    }
    private void owner(String id) {
        LoginUser user=new LoginUser(); user.setId(id);
        Subject subject=new Subject.Builder(new DefaultSecurityManager()).principals(new SimplePrincipalCollection(user,"fixture"))
                .authenticated(true).buildSubject(); ThreadContext.bind(subject);
    }
}
