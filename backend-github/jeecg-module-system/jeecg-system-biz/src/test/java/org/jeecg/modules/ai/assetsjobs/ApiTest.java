package org.jeecg.modules.ai.assetsjobs;

import java.util.*;
import java.nio.file.*;
import org.junit.*;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.*;
import org.apache.shiro.util.ThreadContext;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.api.controller.*;
import org.jeecg.modules.ai.config.jobs.StrictInferenceJsonConverter;
import org.jeecg.modules.system.controller.CommonController;

public class ApiTest {
    private DbFixture f;
    private MockMvc mvc;
    private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule());
    @Before public void before() throws Exception {
        f=new DbFixture(); owner("a");
        mvc=MockMvcBuilders.standaloneSetup(new InferenceController(f.submit,f.query),new JobController(f.query),new AssetController(f.files))
                .setControllerAdvice(new JobsApiExceptionHandler())
                .setMessageConverters(new StrictInferenceJsonConverter(),new MappingJackson2HttpMessageConverter(json)).build();
    }
    @After public void after() throws Exception { ThreadContext.remove(); f.close(); }
    private void owner(String id) {
        LoginUser user=new LoginUser(); user.setId(id);
        Subject subject=new Subject.Builder(new DefaultSecurityManager()).principals(new SimplePrincipalCollection(user,"fixture")).authenticated(true).buildSubject();
        ThreadContext.bind(subject);
    }
    private String body(Asset input) {
        return "{\"capabilityCode\":\"image-detection.v1\",\"inputAssetId\":\""+input.getAssetId()+"\",\"parameters\":{\"threshold\":0.5,\"maxDetections\":10,\"annotate\":true}}";
    }

    @Test public void twoSubmissionRoutesShareIdentityAndQueriesDoNotDispatch() throws Exception {
        Asset input=f.input("a"); String body=body(input);
        MvcResult submitted=mvc.perform(post("/ai/v1/jobs").header("Idempotency-Key","api-same-key").contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.code").value(202)).andReturn();
        String id=json.readTree(submitted.getResponse().getContentAsString()).path("result").path("requestId").asText();
        mvc.perform(post("/ai/v1/infer?waitMillis=0").header("Idempotency-Key","api-same-key").contentType("application/json").content(body))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.result.requestId").value(id));
        JobRecord job=f.query.owned(id,"a"); f.dispatcher(f.provider(f.result(true)),f.reader()).dispatch(job);
        mvc.perform(post("/ai/v1/jobs").header("Idempotency-Key","api-same-key").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.state").value("SUCCEEDED"));
        MvcResult history=mvc.perform(get("/ai/v1/jobs")).andExpect(status().isOk()).andReturn();
        String response=history.getResponse().getContentAsString();
        assertFalse(response.contains("storageKey")); assertFalse(response.contains("fixture:")); assertFalse(response.contains("providerKey"));
        assertTrue(json.readTree(response).path("result").path("items").get(0).path("createdAt").asText().endsWith("Z"));
        mvc.perform(get("/ai/v1/jobs/"+id)).andExpect(status().isOk()); assertEquals(1,f.calls.get());
    }

    @Test public void strictJsonRejectsUnknownDuplicateAndCoercedFieldsBeforeDispatch() throws Exception {
        String valid=body(f.input("a"));
        List<String> invalid=Arrays.asList(valid.replace("0.5","\"0.5\""),valid.replace("10","1.5"),
                valid.replace("true","\"true\""),valid.replace("0.5","0.5,\"threshold\":0.6"),
                valid.replace("\"parameters\":","\"gpuUrl\":\"http://unapproved\",\"parameters\":"),
                valid+"{}",valid.replace("true","null"),valid.replace("0.5","1e99999999"));
        for (String value:invalid) mvc.perform(post("/ai/v1/jobs").header("Idempotency-Key","invalid-key").contentType("application/json").content(value))
                .andExpect(status().isBadRequest());
        assertEquals(Integer.valueOf(0),f.sql.queryForObject("SELECT COUNT(*) FROM ai_job",Integer.class)); assertEquals(0,f.calls.get());
    }

    @Test public void unauthenticatedAndForeignResourcesAreNotReadable() throws Exception {
        Asset input=f.input("a"); JobRecord job=f.submit(input,"owned-key");
        ThreadContext.remove();
        mvc.perform(get("/ai/v1/jobs/"+job.getRequest().getRequestId())).andExpect(status().isUnauthorized());
        mvc.perform(post("/ai/v1/jobs").header("Idempotency-Key","anonymous-key").contentType("application/json").content(body(input)))
                .andExpect(status().isUnauthorized());
        owner("b");
        mvc.perform(get("/ai/v1/jobs/"+job.getRequest().getRequestId())).andExpect(status().isNotFound());
        mvc.perform(get("/ai/v1/assets/"+input.getAssetId()+"/content")).andExpect(status().isNotFound());
        mvc.perform(get("/ai/v1/jobs")).andExpect(jsonPath("$.result.items").isEmpty());
        assertEquals(0,f.calls.get());
    }

    @Test public void rememberedIdentityWithoutAuthenticationIsRejected() throws Exception {
        LoginUser user=new LoginUser(); user.setId("a");
        Subject remembered=new Subject.Builder(new DefaultSecurityManager()).principals(new SimplePrincipalCollection(user,"fixture"))
                .authenticated(false).buildSubject();
        ThreadContext.bind(remembered);
        mvc.perform(get("/ai/v1/jobs")).andExpect(status().isUnauthorized());
    }

    @Test public void uploadAndDownloadArePrivateAndExpiryIsEnforced() throws Exception {
        MockMultipartFile upload=new MockMultipartFile("file","../input.png","image/png",f.png);
        MvcResult accepted=mvc.perform(multipart("/ai/v1/assets").file(upload)).andExpect(status().isCreated()).andReturn();
        String id=json.readTree(accepted.getResponse().getContentAsString()).path("result").path("assetId").asText();
        mvc.perform(get("/ai/v1/assets/"+id+"/content")).andExpect(status().isOk()).andExpect(content().bytes(f.png))
                .andExpect(header().string("Cache-Control","private, no-store"));
        f.sql.update("UPDATE ai_asset SET expires_at=0 WHERE asset_id=?",id);
        mvc.perform(get("/ai/v1/assets/"+id+"/content")).andExpect(status().isGone());
        owner("b"); mvc.perform(get("/ai/v1/assets/"+id+"/content")).andExpect(status().isNotFound());
    }

    @Test public void oldAnonymousStaticControllerCannotReadNewPrivateFiles() throws Exception {
        Asset input=f.input("a"); Path oldRoot=Files.createDirectories(f.privateRoot.getParent().resolve("public"));
        CommonController old=new CommonController(); ReflectionTestUtils.setField(old,"uploadpath",oldRoot.toString());
        MockMvc legacy=MockMvcBuilders.standaloneSetup(old).build(); ThreadContext.remove();
        try {
            MvcResult response=legacy.perform(get("/sys/common/static/"+input.getStored().getStorageKey())).andReturn();
            assertFalse(Arrays.equals(f.png,response.getResponse().getContentAsByteArray()));
        } catch (org.springframework.web.util.NestedServletException denied) {
            assertTrue(denied.getCause().getMessage().contains("不存在"));
        }
        org.springframework.web.servlet.resource.ResourceHttpRequestHandler resources=new org.springframework.web.servlet.resource.ResourceHttpRequestHandler();
        resources.setLocations(Collections.singletonList(new org.springframework.core.io.FileSystemResource(oldRoot.toString()+"/")));
        resources.afterPropertiesSet(); MockHttpServletRequest request=new MockHttpServletRequest("GET","/"+input.getStored().getStorageKey());
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,input.getStored().getStorageKey());
        MockHttpServletResponse output=new MockHttpServletResponse(); resources.handleRequest(request,output);
        assertEquals(404,output.getStatus()); assertEquals(0,output.getContentAsByteArray().length);
    }

    @Test public void startedDownloadFailureClosesStreamWithoutAppendingJson() throws Exception {
        Asset asset=f.input("a"); java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
        MockHttpServletResponse response=new MockHttpServletResponse() {
            public javax.servlet.ServletOutputStream getOutputStream() {
                return new javax.servlet.ServletOutputStream() {
                    public boolean isReady() { return true; }
                    public void setWriteListener(javax.servlet.WriteListener listener) { }
                    public void write(int value) throws java.io.IOException { throw new java.io.IOException("client disconnected"); }
                    public void close() { closed.set(true); }
                };
            }
        };
        response.setCommitted(true);
        new AssetController(f.files).download(asset.getAssetId(),response);
        assertTrue(closed.get()); assertEquals(0,response.getContentAsByteArray().length);
        assertEquals(1,f.countFiles());
    }

    @Test public void providerAuthenticationFailureDoesNotBecomeLogin401() throws Exception {
        JobRecord job=f.submit(f.input("a"),"provider-auth-key");
        f.dispatcher(request -> { throw new ProviderException(ErrorCode.PROVIDER_AUTH,ExecutionCertainty.NOT_STARTED,"auth failure"); },f.reader()).dispatch(job);
        mvc.perform(get("/ai/v1/jobs/"+job.getRequest().getRequestId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.state").value("FAILED")).andExpect(jsonPath("$.result.error.errorCode").value("PROVIDER_AUTH"));
        assertNotNull(org.apache.shiro.SecurityUtils.getSubject().getPrincipal());
    }
}
