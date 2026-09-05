package org.jeecg.modules.ai.provider.config;

import org.jeecg.modules.ai.capability.port.CapabilityRepository;
import org.jeecg.modules.ai.image.port.InferenceProvider;
import org.jeecg.modules.ai.legacy.AiJwtFilter;
import org.jeecg.modules.ai.provider.adapter.ProviderObservations;
import org.jeecg.modules.ai.result.port.ProviderArtifactReader;
import org.jeecg.modules.ai.stream.port.StreamSessionProvider;
import org.jeecg.modules.ai.video.port.VideoAnalysisProvider;

import java.time.Clock;
import org.jeecg.modules.ai.capability.application.CapabilityQueryService;
import org.jeecg.modules.ai.provider.adapter.ModeArtifactReader;
import org.jeecg.modules.ai.provider.adapter.ModeInferenceProvider;
import org.jeecg.modules.ai.provider.adapter.ModeStreamSessionProvider;
import org.jeecg.modules.ai.provider.adapter.ModeVideoAnalysisProvider;
import org.jeecg.modules.ai.provider.adapter.mock.MockArtifactReader;
import org.jeecg.modules.ai.provider.adapter.mock.MockInferenceProvider;
import org.jeecg.modules.ai.provider.adapter.draft.DraftArtifactReader;
import org.jeecg.modules.ai.provider.adapter.draft.DraftHttpProvider;
import org.jeecg.modules.ai.provider.adapter.draft.DraftStreamHttpProvider;
import org.jeecg.modules.ai.provider.adapter.draft.DraftTransport;
import org.jeecg.modules.ai.provider.adapter.draft.DraftVideoHttpProvider;
import org.jeecg.modules.ai.legacy.AiAccessFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderConfiguration {
    private volatile DraftTransport cachedDevelopmentStubTransport;
    @Bean public org.jeecg.modules.ai.provider.adapter.ProviderObservations providerObservations() {
        return new org.jeecg.modules.ai.provider.adapter.ProviderObservations(Clock.systemUTC());
    }

    @Bean public ProviderAvailability providerAvailability(ProviderProperties properties,
            org.jeecg.modules.ai.provider.adapter.ProviderObservations observations) { return new ProviderAvailability(properties, observations); }

    @Bean public InferenceProvider inferenceProvider(ProviderProperties p, ProviderAvailability availability) {
        DraftTransport transport = developmentStubTransport(p);
        return new ModeInferenceProvider(p::getMode, availability::modeReason,
                new MockInferenceProvider(p.getUploadMaxBytes(), p.getOutputMaxBytes(), Math.max(1, p.getMaxInflight()), Clock.systemUTC()),
                transport == null ? null : new DraftHttpProvider(transport, availability.observations()));
    }

    @Bean public ProviderArtifactReader providerArtifactReader(ProviderProperties p) {
        DraftTransport transport = developmentStubTransport(p);
        ProviderAvailability availability = new ProviderAvailability(p,
                new org.jeecg.modules.ai.provider.adapter.ProviderObservations(Clock.systemUTC()));
        return new ModeArtifactReader(new MockArtifactReader(Clock.systemUTC()),
                transport == null ? null : new DraftArtifactReader(transport, Clock.systemUTC()),
                availability::artifactReason, p.getOutputMaxBytes(), p.getVideoOutputMaxBytes());
    }

    @Bean public VideoAnalysisProvider videoAnalysisProvider(ProviderProperties p, ProviderAvailability availability) {
        DraftTransport transport = developmentStubTransport(p);
        return new ModeVideoAnalysisProvider(availability::videoReason,
                transport == null ? null : new DraftVideoHttpProvider(transport, availability.observations()));
    }

    @Bean public StreamSessionProvider streamSessionProvider(ProviderProperties p, ProviderAvailability availability) {
        DraftTransport transport = developmentStubTransport(p);
        return new ModeStreamSessionProvider(
                availability::streamStartReason,
                availability::streamSessionQueryReason,
                availability::streamEventQueryReason,
                availability::streamStopReason,
                transport == null ? null : new DraftStreamHttpProvider(transport, availability.observations()));
    }

    @Bean public CapabilityQueryService capabilityQueryService(ObjectProvider<CapabilityRepository> repositories,
            ProviderProperties p, ProviderAvailability availability) {
        return new CapabilityQueryService(repositories::getIfAvailable, availability::reason, p.getUploadMaxBytes(), p.getOutputMaxBytes());
    }

    @Bean(name = "aiAccessFilter") public AiAccessFilter aiAccessFilter() { return new AiAccessFilter(); }

    @Bean(name = "aiJwtFilter") public org.jeecg.modules.ai.legacy.AiJwtFilter aiJwtFilter(org.springframework.core.env.Environment env) {
        return new org.jeecg.modules.ai.legacy.AiJwtFilter(env.getProperty(org.jeecg.common.constant.CommonConstant.CLOUD_SERVER_KEY)==null);
    }

    @Bean public FilterRegistrationBean<org.jeecg.modules.ai.legacy.AiJwtFilter> aiJwtRegistration(org.jeecg.modules.ai.legacy.AiJwtFilter filter) {
        FilterRegistrationBean<org.jeecg.modules.ai.legacy.AiJwtFilter> registration=new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean public FilterRegistrationBean<AiAccessFilter> aiAccessRegistration(AiAccessFilter aiAccessFilter) {
        FilterRegistrationBean<AiAccessFilter> registration = new FilterRegistrationBean<>(aiAccessFilter);
        registration.setEnabled(false); // Shiro owns invocation, after JWT establishes the subject.
        return registration;
    }

    private synchronized DraftTransport developmentStubTransport(ProviderProperties properties) {
        if (!properties.isDevelopmentStub() || !"remote".equals(properties.getMode())
                || !"stub".equals(properties.getProviderKey())) return null;
        if (cachedDevelopmentStubTransport != null) return cachedDevelopmentStubTransport;
        try {
            cachedDevelopmentStubTransport = DraftTransportFactory.create(properties, true);
            return cachedDevelopmentStubTransport;
        }
        catch (RuntimeException unavailable) { return null; }
    }
}
