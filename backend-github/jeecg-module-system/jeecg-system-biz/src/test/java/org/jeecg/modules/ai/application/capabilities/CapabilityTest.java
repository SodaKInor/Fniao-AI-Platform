package org.jeecg.modules.ai.application.capabilities;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.jeecg.modules.ai.api.controller.CapabilityController;
import org.jeecg.modules.ai.api.mapper.capabilities.CapabilityMapper;
import org.jeecg.modules.ai.client.*;
import org.jeecg.modules.ai.client.mock.*;
import org.jeecg.modules.ai.config.provider.*;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.Assert.*;
import static org.jeecg.modules.ai.client.ClientTestInputs.*;

public class CapabilityTest {
    private Capability capability(boolean enabled) {
        return new Capability(binding(true), "模拟图片检测", enabled, true, true, "", Arrays.asList("image/png", "image/jpeg"),
                10485760, 10485760, 1500);
    }
    private Capability stubCapability() {
        CapabilitySnapshot snapshot = new CapabilitySnapshot("image-detection.v1", "1.1.0", "stub",
                "sync-draft-v0.1", "image-detection.v1", "stub-simulated-v1",
                new ProviderFeatures(false, false, false));
        return new Capability(snapshot, "开发模拟图片检测", true, true, true, "",
                Arrays.asList("image/png", "image/jpeg"), 10485760, 10485760, 1500);
    }
    private CapabilityRepository repository(Capability value) {
        return new CapabilityRepository() {
            public Optional<Capability> find(String code) { return Optional.of(value); }
            public List<Capability> list() { return Collections.singletonList(value); }
        };
    }

    @Test public void mockRequiresExplicitModeAndKeepsPastArtifactReadableWhenDisabled() throws Exception {
        ProviderProperties p = new ProviderProperties();
        ProviderConfiguration config = new ProviderConfiguration();
        ProviderAvailability availability = new ProviderAvailability(p, config.providerObservations());
        InferenceProvider provider = config.inferenceProvider(p, availability);
        CountingSource input = new CountingSource();
        try { provider.infer(request(input, true, true, null)); fail(); } catch (ProviderException e) { assertEquals(ErrorCode.CAPABILITY_UNAVAILABLE, e.getErrorCode()); }
        assertEquals(0, input.opens.get());
        p.setMode("mock");
        ProviderResult result = provider.infer(request(input, true, true, (long) input.bytes.length));
        assertTrue(result.isSimulated()); assertEquals(1, input.opens.get()); assertEquals(1, input.closes.get());
        assertEquals(16, result.getData().getImageWidth());
        p.setMode("disabled");
        try (InputStream artifact = config.providerArtifactReader(p).open(binding(true), result.getArtifacts().get(0), 1024)) {
            assertEquals(79, ProtocolFixture.read(artifact).length);
        }
        p.setMode("remote");
        CountingSource unopened = new CountingSource();
        try { provider.infer(request(unopened, true, true, null)); fail(); } catch (ProviderException expected) { }
        assertEquals(0, unopened.opens.get());
    }

    @Test public void mockEmptyAndNoAnnotationHaveNoArtifacts() throws Exception {
        MockInferenceProvider mock = new MockInferenceProvider(10485760, 10485760, 1, Clock.systemUTC());
        ProviderRequest empty = new ProviderRequest(REQUEST_ID, binding(true), new DetectionParameters(BigDecimal.ONE, 1, true),
                new ContentMetadata("input.png", "image/png", null, null), new CountingSource());
        ProviderResult result = mock.infer(empty);
        assertTrue(result.getData().getDetections().isEmpty()); assertTrue(result.getArtifacts().isEmpty());
        assertTrue(mock.infer(request(new CountingSource(), true, false, null)).getArtifacts().isEmpty());
    }

    @Test public void modesPermissionsAndRepositoryStatusControlAvailability() throws Exception {
        ProviderProperties p = new ProviderProperties();
        ProviderAvailability state = new ProviderAvailability(p, new ProviderObservations(Clock.systemUTC()));
        CapabilityQueryService service = new CapabilityQueryService(() -> repository(capability(true)), state::reason, 100, 200);
        assertFalse(service.list(true).get(0).isAvailable());
        p.setMode("mock"); assertTrue(service.list(true).get(0).isAvailable());
        assertEquals(100, service.list(true).get(0).getMaxInputBytes());
        assertFalse(service.list(false).get(0).isAvailable());
        CapabilityQueryService disabled = new CapabilityQueryService(() -> repository(capability(false)), state::reason, 100, 200);
        assertEquals("能力已停用", disabled.list(true).get(0).getUnavailableReason());
        p.setMode("remote"); assertTrue(service.list(true).get(0).getUnavailableReason().contains("配置不完整"));
        assertFalse(service.list(true).get(0).isAvailable());
    }

    @Test public void externalObservationsRemainSeparateFromModelReadiness() throws Exception {
        ProviderProperties p = properties("https://127.0.0.1:1234"); p.setMode("remote");
        ProviderObservations observed = new ProviderObservations(Clock.systemUTC());
        ProviderAvailability availability = new ProviderAvailability(p, observed);
        assertTrue(availability.modeReason().contains("可达性未确认"));
        observed.record("fixture", "外部服务离线");
        assertTrue(availability.modeReason().contains("外部服务离线"));
        observed.record("fixture", "");
        assertTrue(availability.modeReason().contains("模型就绪状态未确认"));
        assertTrue(availability.modeReason().contains("真实服务协议尚未确认"));
    }

    @Test public void missingRepositoryDoesNotPreventContextStartupOrProvideFakeBindings() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ProviderConfiguration.class)) {
            assertTrue(context.isActive()); assertTrue(context.getBeansOfType(CapabilityRepository.class).isEmpty());
            try { context.getBean(CapabilityQueryService.class).list(true); fail(); }
            catch (IllegalStateException e) { assertTrue(e.getMessage().contains("not ready")); }
            assertTrue(context.isActive());
        }
    }

    @Test public void mapperPreservesFrozenShapeWithoutProviderSecrets() throws Exception {
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new CapabilityMapper().map(Collections.singletonList(capability(true))));
        assertFalse(json.contains("providerKey")); assertFalse(json.contains("adapterId")); assertFalse(json.contains("token"));
        assertTrue(json.contains("\"parametersSchema\":\"detection.v1\""));
        assertTrue(json.contains("\"unavailableReason\":\"\""));
    }
    @Test public void capabilityHttpUsesFrozenEnvelopeAndReportsMissingDependency() throws Exception {
        org.apache.shiro.subject.support.DelegatingSubject subject = new org.apache.shiro.subject.support.DelegatingSubject(
                new org.apache.shiro.mgt.DefaultSecurityManager()) {
            @Override public boolean isPermitted(String permission) { return true; }
        };
        org.apache.shiro.util.ThreadContext.bind(subject);
        try {
            CapabilityQueryService ready = new CapabilityQueryService(() -> repository(capability(true)), c -> "", 10485760, 10485760);
            org.springframework.test.web.servlet.MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .standaloneSetup(new CapabilityController(ready, new CapabilityMapper())).build();
            org.springframework.mock.web.MockHttpServletResponse response = mvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/ai/v1/capabilities")).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
            assertEquals(200, json.get("code").asInt()); assertTrue(json.get("success").asBoolean());
            assertTrue(json.get("result").get(0).get("simulated").asBoolean());
            java.nio.file.Files.write(java.nio.file.Paths.get("/validation/capabilities.actual.json"), response.getContentAsByteArray());
            CapabilityQueryService missing = new CapabilityQueryService(() -> null, c -> "", 10485760, 10485760);
            mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .standaloneSetup(new CapabilityController(missing, new CapabilityMapper())).build();
            assertEquals(500, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/ai/v1/capabilities")).andReturn().getResponse().getStatus());
        } finally { org.apache.shiro.util.ThreadContext.remove(); }
    }

    @Test public void springBindsProviderLimitsAndRegistersOnlyClosedRuntimeGates() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "wgai.inference.mode=mock", "wgai.inference.max-inflight=2", "wgai.inference.upload-max-bytes=512",
                    "wgai.inference.video-upload-max-bytes=1024", "wgai.inference.video-api-path=/fixture-video");
            context.register(ProviderConfiguration.class); context.refresh();
            ProviderProperties p = context.getBean(ProviderProperties.class);
            assertEquals("mock", p.getMode()); assertEquals(2, p.getMaxInflight()); assertEquals(512, p.getUploadMaxBytes());
            assertEquals(1024, p.getVideoUploadMaxBytes()); assertEquals("/fixture-video", p.getVideoApiPath());
            assertFalse(p.isDevelopmentStub());
            assertEquals(1, context.getBeansOfType(InferenceProvider.class).size());
            assertEquals(1, context.getBeansOfType(VideoAnalysisProvider.class).size());
            assertEquals(1, context.getBeansOfType(StreamSessionProvider.class).size());
            assertTrue(context.getBeansOfType(org.jeecg.modules.ai.client.draft.DraftHttpProvider.class).isEmpty());
            assertTrue(context.getBeansOfType(org.jeecg.modules.ai.client.draft.DraftVideoHttpProvider.class).isEmpty());
            assertTrue(context.getBeansOfType(org.jeecg.modules.ai.client.draft.DraftStreamHttpProvider.class).isEmpty());
            try { context.getBean(VideoAnalysisProvider.class).analyze(videoRequest(new CountingSource(), false, false, null)); fail(); }
            catch (ProviderException error) { assertEquals(ErrorCode.CAPABILITY_UNAVAILABLE, error.getErrorCode()); }
            try { context.getBean(StreamSessionProvider.class).start(streamRequest(false)); fail(); }
            catch (ProviderException error) { assertEquals(ErrorCode.CAPABILITY_UNAVAILABLE, error.getErrorCode()); }
            assertFalse(org.jeecg.modules.ai.legacy.LegacyExecutionGuard.isLocalExecutionAllowed());
        }
    }

    @Test public void developmentStubNeedsAllThreeExplicitSignalsAndNeverPromotesRealRemote() throws Exception {
        try (ProtocolFixture fixture = new ProtocolFixture(false)) {
            ProviderProperties p = properties(fixture.url());
            p.setMode("remote");
            p.setProviderKey("stub");
            ProviderConfiguration config = new ProviderConfiguration();
            ProviderAvailability state = new ProviderAvailability(p, config.providerObservations());

            assertFalse(state.modeReason().isEmpty());
            assertFalse(state.reason(stubCapability()).isEmpty());

            p.setDevelopmentStub(true);
            assertEquals("", state.modeReason());
            assertEquals("", state.reason(stubCapability()));
            assertEquals("", state.videoReason());
            assertEquals("", state.streamStartReason());

            p.setProviderKey("fixture");
            assertFalse(state.modeReason().isEmpty());
            p.setProviderKey("stub");
            p.setMode("mock");
            assertEquals("", state.modeReason());
            assertFalse(state.reason(stubCapability()).isEmpty());
        }
    }

}
