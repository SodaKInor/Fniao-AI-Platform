package org.jeecg.modules.ai.assetsjobs;

import org.jeecg.modules.ai.operations.api.JobsApiExceptionHandler;
import org.jeecg.modules.ai.stream.api.StreamController;
import org.jeecg.modules.ai.stream.application.StartStreamSessionService;
import org.jeecg.modules.ai.stream.application.StopStreamSessionService;
import org.jeecg.modules.ai.stream.application.StreamQueryService;
import org.jeecg.modules.ai.stream.domain.StreamProviderFeatures;

import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.*;
import org.apache.shiro.util.ThreadContext;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.ai.operations.config.StrictInferenceJsonConverter;

public class StreamApiTest {
    private DbFixture f;
    private MockMvc mvc;
    private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule());
    @Before public void before() throws Exception {
        f=new DbFixture(); f.streamSource("a","api_source",true); owner("a");
        StartStreamSessionService start=new StartStreamSessionService(f.streamSessions,f.streamSources,f.capabilities,
                capability -> new StreamProviderFeatures(true,true,true,true),f.clock);
        StreamQueryService query=new StreamQueryService(f.streamSources,f.streamSessions,f.streamEvents);
        StreamController controller=new StreamController(start,query,new StopStreamSessionService(f.streamSessions,f.clock));
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new JobsApiExceptionHandler())
                .setMessageConverters(new StrictInferenceJsonConverter(),new MappingJackson2HttpMessageConverter(json)).build();
    }
    @After public void after() throws Exception { ThreadContext.remove(); f.close(); }

    @Test public void fiveRoutesUseOnlyLocalOpaqueIdentity() throws Exception {
        String sources=mvc.perform(get("/ai/v1/stream-sources")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].streamSourceId").value("api_source")).andReturn().getResponse().getContentAsString();
        assertFalse(sources.contains("provider-source-fixture"));
        MvcResult created=mvc.perform(post("/ai/v1/stream-sessions").header("Idempotency-Key","stream-api-key")
                .contentType("application/json").content(body())).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result.state").value("PENDING")).andReturn();
        String response=created.getResponse().getContentAsString();
        String id=json.readTree(response).path("result").path("sessionId").asText();
        assertFalse(response.contains("providerSessionId")); assertFalse(response.contains("provider-source-fixture"));
        mvc.perform(get("/ai/v1/stream-sessions/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.streamSourceId").value("api_source"));
        mvc.perform(get("/ai/v1/stream-sessions/"+id+"/events")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items").isEmpty());
        mvc.perform(post("/ai/v1/stream-sessions/"+id+"/stop")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.state").value("STOPPED"));
        mvc.perform(post("/ai/v1/stream-sessions").header("Idempotency-Key","stream-api-key")
                .contentType("application/json").content(body())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.state").value("STOPPED"));
    }

    @Test public void unknownFieldsSecretsDuplicatesAndScalarCoercionAreRejected() throws Exception {
        List<String> invalid=Arrays.asList(
                body().replace("\"parameters\":","\"rtspUrl\":\"rtsp://secret\",\"parameters\":"),
                body().replace("\"parameters\":","\"gpuUrl\":\"https://gpu\",\"parameters\":"),
                body().replace("\"parameters\":","\"credentials\":{\"token\":\"secret\"},\"parameters\":"),
                body().replace("20","\"20\""),
                body().replace("250","250,\"pollIntervalMillis\":300"));
        int i=0;
        for (String value:invalid) mvc.perform(post("/ai/v1/stream-sessions")
                .header("Idempotency-Key","invalid-stream-"+(i++)).contentType("application/json").content(value))
                .andExpect(status().isBadRequest());
        assertEquals(Integer.valueOf(0),f.sql.queryForObject("SELECT COUNT(*) FROM ai_stream_session",Integer.class));
    }

    @Test public void anonymousAndOtherUsersCannotSeeSourcesSessionsOrEvents() throws Exception {
        MvcResult created=mvc.perform(post("/ai/v1/stream-sessions").header("Idempotency-Key","owned-stream")
                .contentType("application/json").content(body())).andExpect(status().isAccepted()).andReturn();
        String id=json.readTree(created.getResponse().getContentAsString()).path("result").path("sessionId").asText();
        ThreadContext.remove(); mvc.perform(get("/ai/v1/stream-sources")).andExpect(status().isUnauthorized());
        owner("b");
        mvc.perform(get("/ai/v1/stream-sources")).andExpect(jsonPath("$.result").isEmpty());
        mvc.perform(get("/ai/v1/stream-sessions/"+id)).andExpect(status().isNotFound());
        mvc.perform(get("/ai/v1/stream-sessions/"+id+"/events")).andExpect(status().isNotFound());
        mvc.perform(post("/ai/v1/stream-sessions/"+id+"/stop")).andExpect(status().isNotFound());
    }

    private String body() {
        return "{\"capabilityCode\":\"video-stream-analysis.v1\",\"streamSourceId\":\"api_source\","
                +"\"parameters\":{\"maxEventsPerPoll\":20,\"pollIntervalMillis\":250,\"includeSnapshots\":true}}";
    }
    private void owner(String id) {
        LoginUser user=new LoginUser(); user.setId(id);
        Subject subject=new Subject.Builder(new DefaultSecurityManager()).principals(
                new SimplePrincipalCollection(user,"fixture")).authenticated(true).buildSubject();
        ThreadContext.bind(subject);
    }
}
