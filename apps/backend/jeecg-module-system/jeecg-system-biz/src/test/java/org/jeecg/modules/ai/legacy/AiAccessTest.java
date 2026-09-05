package org.jeecg.modules.ai.legacy;

import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ProviderException;

import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.Filter;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.*;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.config.shiro.ShiroConfig;
import org.jeecg.config.vo.Shiro;
import org.jeecg.modules.ai.client.ProtocolFixture;
import org.junit.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.*;
import static org.jeecg.modules.ai.client.ClientTestInputs.*;

/** Real Shiro/JwtFilter chain; only the account realm is a test substitute (no production Redis). */
public class AiAccessTest {
    private Filter filter;
    private final AtomicInteger downstream = new AtomicInteger();

    @Before public void setUp() throws Exception {
        DefaultWebSecurityManager manager = new DefaultWebSecurityManager(new FixtureRealm());
        ShiroConfig config = new ShiroConfig();
        ReflectionTestUtils.setField(config, "env", new MockEnvironment());
        ReflectionTestUtils.setField(config, "aiAccessFilter", new AiAccessFilter());
        ReflectionTestUtils.setField(config, "aiJwtFilter", new AiJwtFilter(true));
        JeecgBaseConfig base = new JeecgBaseConfig();
        Shiro exclusions = new Shiro(); exclusions.setExcludeUrls("/**,/ai/v1/**,/tab/testAI/**"); base.setShiro(exclusions);
        ReflectionTestUtils.setField(config, "jeecgBaseConfig", base);
        filter = config.shiroFilter(manager).getObject();
        filter.init(new MockFilterConfig());
    }

    @After public void tearDown() { if (filter != null) filter.destroy(); ThreadContext.remove(); }

    private MockHttpServletResponse call(String method, String path, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/jeecg-boot" + path);
        request.setContextPath("/jeecg-boot"); request.setServletPath(path);
        if (token != null) request.addHeader("X-Access-Token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> downstream.incrementAndGet());
        return response;
    }

    @Test public void anonymousCannotBypassViaExclusionsOrStaticSuffix() throws Exception {
        for (String path : new String[]{"/ai/v1/infer", "/ai/v1/assets/a.png", "/tab/testAI/test", "/tab/tabAiBase/list",
                "/tab/tabAiSubscription/list", "/tab/tabAiHistory/list", "/video/tabVideoUtil/startVideoUtil"}) {
            assertEquals(path, 401, call("GET", path, null).getStatus());
        }
        assertEquals(0, downstream.get());
    }

    @Test public void missingAndInvalidBusinessTokensUseFrozenAiErrorEnvelope() throws Exception {
        for (String token : new String[]{null,"invalid"}) {
            MockHttpServletResponse response=call("POST","/ai/v1/infer",token);
            assertEquals(401,response.getStatus());
            assertTrue(response.getContentType().startsWith("application/json"));
            com.fasterxml.jackson.databind.JsonNode json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
            assertEquals("UNAUTHENTICATED",json.path("result").path("errorCode").asText());
            assertEquals(401,json.path("code").asInt());
            assertFalse(json.path("success").asBoolean());
            assertTrue(json.path("result").has("simulated"));
            assertFalse(json.path("result").path("simulated").asBoolean());
        }
        assertEquals(0,downstream.get());
    }

    @Test public void newSubmissionsRequireAiInferButOwnedReadsDoNot() throws Exception {
        for (String path : new String[]{"/ai/v1/assets", "/ai/v1/infer", "/ai/v1/jobs", "/ai/v1/infer/"}) {
            MockHttpServletResponse response = call("POST", path, "reader");
            assertEquals(403, response.getStatus()); assertTrue(response.getContentAsString().contains("FORBIDDEN"));
            assertTrue(response.getContentAsString().contains("\"simulated\":false"));
        }
        assertEquals(0, downstream.get());
        assertEquals(200, call("GET", "/ai/v1/jobs", "reader").getStatus());
        assertEquals(200, call("GET", "/ai/v1/assets/owned/content", "reader").getStatus());
        assertEquals(200, call("POST", "/ai/v1/infer", "operator").getStatus());
        assertEquals(3, downstream.get());
    }

    @Test public void legacyEndpointsAreStoppedForAuthorizedOperators() throws Exception {
        for (String path : new String[]{"/tab/tabAiHistory/addIdentify", "/tab/tabAiHistory/addIdentifyClose", "/tab/tabAiHistory/addAudio",
                "/tab/testAI/test", "/tab/testAI/testAIModel", "/tab/testAI/testAIModel2", "/tab/testAI/testSavePic",
                "/video/tabVideoUtil/startVideoUtil", "/video/tabVideoUtil/stopVideoUtil", "/tab/tabAiSubscription/subInfo/",
                "/maxkb/tabMaxkbModel/testConnect"}) {
            assertEquals(path, 409, call("POST", path, "operator").getStatus());
        }
        assertEquals(0, downstream.get());
        assertEquals(200, call("GET", "/tab/tabAiHistory/list", "reader").getStatus());
        assertEquals(200, call("GET", "/tab/tabAiSubscription/list", "reader").getStatus());
        assertEquals(200, call("GET", "/tab/tabAiBase/list", "reader").getStatus());
    }

    @Test public void providerAuthFailureDoesNotInvalidateBusinessAuthentication() throws Exception {
        assertEquals(200, call("POST", "/ai/v1/infer", "operator").getStatus());
        try (ProtocolFixture f = new ProtocolFixture(false)) {
            f.status = 401; f.json("provider token rejected");
            try { provider(properties(f.url())).infer(request(new CountingSource(), false, true, null)); fail(); }
            catch (ProviderException e) { assertEquals(ErrorCode.PROVIDER_AUTH, e.getErrorCode()); }
            assertEquals("Bearer fixture-service-token", f.authorization);
        }
        assertEquals(200, call("GET", "/tab/tabAiHistory/list", "operator").getStatus());
    }

    @Test public void directServiceAndControllerCallsStopBeforeAnyDependencyAccess() throws Exception {
        assertGuarded("org.jeecg.modules.demo.tab.service.impl.TabAiHistoryServiceImpl", new String[]{"aiAudio", "saveStr", "saveAudioStr",
                "waveInt16", "saveCarIdentify", "saveCarIdentifyV5", "saveIdentify", "saveIdentifyYolov5", "saveIdentifyYolov8",
                "closedentify", "saveIdentifyVideo", "saveIdentifyLocalVideo", "saveIdentifyLocalVideoThread",
                "saveIdentifyLocalVideoThreadV5", "startAi", "sendUrl", "sendUrlFLV"});
        assertGuarded("org.jeecg.modules.demo.video.service.impl.TabVideoUtilServiceImpl", new String[]{"startVideoUtil", "endVideoUtil"});
        assertGuarded("org.jeecg.modules.demo.tab.controller.TabAiHistoryController", new String[]{"addIdentify", "addAudio", "addIdentifyClose"});
        assertGuarded("org.jeecg.modules.demo.video.controller.TabVideoUtilController", new String[]{"startVideoUtil", "stopVideoUtil"});
        assertGuarded("org.jeecg.modules.demo.tab.controller.TabAiSubscriptionController", new String[]{"getSub"});
        assertGuarded("org.jeecg.modules.tab.controller.AITestController", new String[]{"testSavePic", "testAIModel2", "testAIModel", "StartAITest"});
    }

    private void assertGuarded(String className, String[] methods) throws Exception {
        Class<?> type;
        try { type = Class.forName(className); }
        catch (ClassNotFoundException retired) { return; }
        Object instance = type.newInstance();
        for (String name : methods) {
            java.util.Optional<Method> candidate = java.util.Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name)).findFirst();
            if (!candidate.isPresent()) continue;
            Method method = candidate.get();
            try { method.invoke(instance, new Object[method.getParameterCount()]); fail(name + " bypassed guard"); }
            catch (InvocationTargetException e) {
                assertTrue(name, e.getCause() instanceof IllegalStateException);
                assertTrue(e.getCause().getMessage().contains("旧 AI 执行入口已停用"));
            }
        }
    }

    private static final class FixtureRealm extends AuthorizingRealm {
        @Override public boolean supports(AuthenticationToken token) { return true; }
        @Override protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
            String value = String.valueOf(token.getPrincipal());
            if (!value.equals("reader") && !value.equals("operator")) throw new AuthenticationException("Invalid fixture identity");
            return new SimpleAuthenticationInfo(value, value, getName());
        }
        @Override protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principal) {
            SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
            if (principal.getPrimaryPrincipal().equals("operator")) info.addStringPermission("ai:infer");
            return info;
        }
    }
}
