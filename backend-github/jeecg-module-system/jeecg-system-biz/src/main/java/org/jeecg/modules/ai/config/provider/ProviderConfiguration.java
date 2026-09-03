package org.jeecg.modules.ai.config.provider;

import java.time.Clock;
import org.jeecg.modules.ai.application.capabilities.CapabilityQueryService;
import org.jeecg.modules.ai.client.ModeArtifactReader;
import org.jeecg.modules.ai.client.ModeInferenceProvider;
import org.jeecg.modules.ai.client.mock.MockArtifactReader;
import org.jeecg.modules.ai.client.mock.MockInferenceProvider;
import org.jeecg.modules.ai.legacy.AiAccessFilter;
import org.jeecg.modules.ai.port.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderConfiguration {
    @Bean public org.jeecg.modules.ai.client.ProviderObservations providerObservations() {
        return new org.jeecg.modules.ai.client.ProviderObservations(Clock.systemUTC());
    }

    @Bean public ProviderAvailability providerAvailability(ProviderProperties properties,
            org.jeecg.modules.ai.client.ProviderObservations observations) { return new ProviderAvailability(properties, observations); }

    @Bean public InferenceProvider inferenceProvider(ProviderProperties p, ProviderAvailability availability) {
        return new ModeInferenceProvider(p::getMode, availability::modeReason,
                new MockInferenceProvider(p.getUploadMaxBytes(), p.getOutputMaxBytes(), Math.max(1, p.getMaxInflight()), Clock.systemUTC()));
    }

    @Bean public ProviderArtifactReader providerArtifactReader(ProviderProperties p) {
        return new ModeArtifactReader(new MockArtifactReader(Clock.systemUTC()), p.getOutputMaxBytes());
    }

    @Bean public CapabilityQueryService capabilityQueryService(ObjectProvider<CapabilityRepository> repositories,
            ProviderProperties p, ProviderAvailability availability) {
        return new CapabilityQueryService(repositories::getIfAvailable, availability::reason, p.getUploadMaxBytes(), p.getOutputMaxBytes());
    }

    @Bean(name = "aiAccessFilter") public AiAccessFilter aiAccessFilter() { return new AiAccessFilter(); }

    @Bean public FilterRegistrationBean<AiAccessFilter> aiAccessRegistration(AiAccessFilter aiAccessFilter) {
        FilterRegistrationBean<AiAccessFilter> registration = new FilterRegistrationBean<>(aiAccessFilter);
        registration.setEnabled(false); // Shiro owns invocation, after JWT establishes the subject.
        return registration;
    }
}
