package org.jeecg.modules.ai.config.jobs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.jeecg.modules.ai.port.*;
import org.jeecg.modules.ai.storage.PrivateFileArtifactStore;
import org.jeecg.modules.ai.persistence.mapper.*;
import org.jeecg.modules.ai.persistence.converter.*;
import org.jeecg.modules.ai.persistence.repository.*;
import org.jeecg.modules.ai.application.assets.AssetService;
import org.jeecg.modules.ai.application.jobs.*;

@Configuration
@EnableConfigurationProperties(JobsProperties.class)
@MapperScan("org.jeecg.modules.ai.persistence.mapper")
public class JobsConfiguration implements WebMvcConfigurer {
    @Bean public SnapshotCodec aiSnapshotCodec() { return new SnapshotCodec(); }
    @Bean public RecordConverter aiRecordConverter(SnapshotCodec codec) { return new RecordConverter(codec); }
    @Bean public AssetRepository aiAssetRepository(AssetMapper mapper,RecordConverter converter) {
        return new MyBatisAssetRepository(mapper,converter);
    }
    @Bean public CapabilityRepository aiCapabilityRepository(CapabilityMapper mapper,SnapshotCodec codec) {
        return new MyBatisCapabilityRepository(mapper,codec);
    }
    @Bean public JobRepository aiJobRepository(JobMapper mapper,AssetMapper assets,SnapshotCodec codec,
                                             PlatformTransactionManager manager,JobsProperties properties) {
        properties.validate();
        return new MyBatisJobRepository(mapper,assets,codec,manager,properties.getMaxQueued(),properties.getParallelism());
    }
    @Bean public ArtifactStore aiArtifactStore(JobsProperties p,Environment env) throws IOException {
        p.validate(); List<Path> publicRoots=new ArrayList<>();
        for (String key:Arrays.asList("jeecg.path.upload","jeecg.path.webapp")) {
            String value=env.getProperty(key);
            if (value!=null && !value.trim().isEmpty()) publicRoots.add(Paths.get(value));
        }
        for (String key:Arrays.asList("spring.resource.static-locations","spring.resources.static-locations","spring.web.resources.static-locations")) {
            for (String value:env.getProperty(key,"").split(","))
                if (value.trim().startsWith("file:")) publicRoots.add(Paths.get(URI.create(value.trim())));
        }
        return new PrivateFileArtifactStore(Paths.get(p.getPrivateRoot()),publicRoots,p.getMaxImageDimension());
    }
    @Bean public AssetService aiAssetService(AssetRepository assets,ArtifactStore store,JobsProperties p) {
        return new AssetService(assets,store,Clock.systemUTC(),p.getMaxInputBytes(),
                Duration.ofDays(p.getInputRetentionDays()),Duration.ofDays(p.getOutputRetentionDays()));
    }
    @Bean public SubmitInferenceService aiSubmitService(JobRepository jobs,CapabilityRepository capabilities,AssetService assets,
            ObjectProvider<org.jeecg.modules.ai.application.capabilities.CapabilityQueryService> policies) {
        return new SubmitInferenceService(jobs,new SubmissionCapabilities(capabilities,policies::getIfAvailable),assets,Clock.systemUTC());
    }
    @Bean public JobQueryService aiQueryService(JobRepository jobs,AssetRepository assets) { return new JobQueryService(jobs,assets); }
    @Bean(initMethod="start",destroyMethod="close")
    public JobWorker aiJobWorker(JobRepository jobs,ObjectProvider<InferenceProvider> provider,ObjectProvider<ProviderArtifactReader> reader,
                                 AssetService assets,JobsProperties p,CapabilityRepository capabilities) {
        return new JobWorker(jobs,provider,reader,assets,Clock.systemUTC(),p.getParallelism(),p.getMaxOutputBytes(),capabilities);
    }
    @Override public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0,new StrictInferenceJsonConverter());
    }
}
