package org.jeecg.modules.ai.operations.config;

import org.jeecg.modules.ai.asset.persistence.mapper.AssetMapper;
import org.jeecg.modules.ai.asset.persistence.repository.MyBatisAssetRepository;
import org.jeecg.modules.ai.asset.port.ArtifactStore;
import org.jeecg.modules.ai.asset.port.AssetRepository;
import org.jeecg.modules.ai.capability.application.CapabilityQueryService;
import org.jeecg.modules.ai.capability.persistence.repository.MyBatisCapabilityRepository;
import org.jeecg.modules.ai.capability.port.CapabilityRepository;
import org.jeecg.modules.ai.image.port.InferenceProvider;
import org.jeecg.modules.ai.job.application.CancelJobService;
import org.jeecg.modules.ai.job.application.JobQueryService;
import org.jeecg.modules.ai.job.application.SubmitInferenceService;
import org.jeecg.modules.ai.job.persistence.converter.RecordConverter;
import org.jeecg.modules.ai.job.persistence.converter.SnapshotCodec;
import org.jeecg.modules.ai.job.persistence.converter.VideoSnapshotCodec;
import org.jeecg.modules.ai.job.persistence.mapper.JobMapper;
import org.jeecg.modules.ai.job.persistence.repository.MyBatisJobRepository;
import org.jeecg.modules.ai.job.port.JobRepository;
import org.jeecg.modules.ai.provider.config.ProviderAvailability;
import org.jeecg.modules.ai.result.port.ProviderArtifactReader;
import org.jeecg.modules.ai.stream.application.StartStreamSessionService;
import org.jeecg.modules.ai.stream.application.StopStreamSessionService;
import org.jeecg.modules.ai.stream.application.StreamQueryService;
import org.jeecg.modules.ai.stream.domain.StreamProviderFeatures;
import org.jeecg.modules.ai.stream.persistence.converter.StreamRecordConverter;
import org.jeecg.modules.ai.stream.persistence.converter.StreamSnapshotCodec;
import org.jeecg.modules.ai.stream.persistence.mapper.StreamEventMapper;
import org.jeecg.modules.ai.stream.persistence.mapper.StreamSessionMapper;
import org.jeecg.modules.ai.stream.persistence.mapper.StreamSourceMapper;
import org.jeecg.modules.ai.stream.persistence.repository.MyBatisStreamEventRepository;
import org.jeecg.modules.ai.stream.persistence.repository.MyBatisStreamSessionRepository;
import org.jeecg.modules.ai.stream.persistence.repository.MyBatisStreamSourceRepository;
import org.jeecg.modules.ai.stream.port.StreamEventRepository;
import org.jeecg.modules.ai.stream.port.StreamSessionProvider;
import org.jeecg.modules.ai.stream.port.StreamSessionRepository;
import org.jeecg.modules.ai.stream.port.StreamSourceRepository;
import org.jeecg.modules.ai.video.port.VideoAnalysisProvider;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.jeecg.modules.ai.asset.storage.PrivateFileArtifactStore;
import org.jeecg.modules.ai.capability.persistence.mapper.CapabilityMapper;
import org.jeecg.modules.ai.asset.application.AssetService;
import org.jeecg.modules.ai.provider.config.ProviderProperties;

@Configuration
@EnableConfigurationProperties(JobsProperties.class)
@MapperScan({"org.jeecg.modules.ai.asset.persistence.mapper",
        "org.jeecg.modules.ai.capability.persistence.mapper",
        "org.jeecg.modules.ai.job.persistence.mapper",
        "org.jeecg.modules.ai.stream.persistence.mapper"})
public class JobsConfiguration implements WebMvcConfigurer {
    @Bean public AiRuntimeMetrics aiRuntimeMetrics(ObjectProvider<MeterRegistry> registry) {
        return new AiRuntimeMetrics(registry.getIfAvailable());
    }
    @Bean public SnapshotCodec aiSnapshotCodec() { return new SnapshotCodec(); }
    @Bean public VideoSnapshotCodec aiVideoSnapshotCodec() { return new VideoSnapshotCodec(); }
    @Bean public StreamSnapshotCodec aiStreamSnapshotCodec() { return new StreamSnapshotCodec(); }
    @Bean public RecordConverter aiRecordConverter(SnapshotCodec codec,VideoSnapshotCodec video) { return new RecordConverter(codec,video); }
    @Bean public StreamRecordConverter aiStreamRecordConverter(StreamSnapshotCodec stream,SnapshotCodec common) {
        return new StreamRecordConverter(stream,common);
    }
    @Bean public AssetRepository aiAssetRepository(AssetMapper mapper,RecordConverter converter) {
        return new MyBatisAssetRepository(mapper,converter);
    }
    @Bean public CapabilityRepository aiCapabilityRepository(CapabilityMapper mapper,SnapshotCodec codec) {
        return new MyBatisCapabilityRepository(mapper,codec);
    }
    @Bean public MyBatisJobRepository aiJobRepository(JobMapper mapper,AssetMapper assets,SnapshotCodec codec,
                                             PlatformTransactionManager manager,JobsProperties properties) {
        properties.validate();
        return new MyBatisJobRepository(mapper,assets,codec,manager,properties.getMaxQueued(),properties.getParallelism());
    }
    @Bean public StreamSourceRepository aiStreamSourceRepository(StreamSourceMapper mapper,StreamRecordConverter converter) {
        return new MyBatisStreamSourceRepository(mapper,converter);
    }
    @Bean public MyBatisStreamSessionRepository aiStreamSessionRepository(StreamSessionMapper mapper,
            StreamRecordConverter converter,PlatformTransactionManager manager,JobsProperties properties) {
        return new MyBatisStreamSessionRepository(mapper,converter,manager,properties.getMaxQueued(),properties.getParallelism());
    }
    @Bean public StreamEventRepository aiStreamEventRepository(StreamSessionMapper sessions,StreamEventMapper events,
            AssetMapper assets,StreamRecordConverter converter,PlatformTransactionManager manager,AiRuntimeMetrics metrics) {
        return new MyBatisStreamEventRepository(sessions,events,assets,converter,manager,
                count -> metrics.streamEvents("inserted",count),count -> metrics.streamEvents("duplicate",count));
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
        return new AssetService(assets,store,Clock.systemUTC(),p.getMaxInputBytes(),p.getMaxVideoInputBytes(),
                Duration.ofDays(p.getInputRetentionDays()),Duration.ofDays(p.getOutputRetentionDays()));
    }
    @Bean public SubmitInferenceService aiSubmitService(JobRepository jobs,CapabilityRepository capabilities,AssetService assets,
            ObjectProvider<org.jeecg.modules.ai.capability.application.CapabilityQueryService> policies) {
        return new SubmitInferenceService(jobs,new SubmissionCapabilities(capabilities,policies::getIfAvailable),assets,Clock.systemUTC());
    }
    @Bean public JobQueryService aiQueryService(JobRepository jobs,AssetRepository assets) { return new JobQueryService(jobs,assets); }
    @Bean public CancelJobService aiCancelService(JobRepository jobs) { return new CancelJobService(jobs,Clock.systemUTC()); }
    @Bean public org.jeecg.modules.ai.stream.application.StartStreamSessionService aiStartStreamService(
            StreamSessionRepository sessions,StreamSourceRepository sources,CapabilityRepository capabilities,
            ObjectProvider<org.jeecg.modules.ai.provider.config.ProviderAvailability> availability) {
        org.jeecg.modules.ai.provider.config.ProviderAvailability gates=availability.getIfAvailable();
        return new org.jeecg.modules.ai.stream.application.StartStreamSessionService(sessions,sources,capabilities,
                capability -> {
                    boolean supported=gates!=null && gates.reason(capability).isEmpty();
                    return new org.jeecg.modules.ai.stream.domain.StreamProviderFeatures(
                            supported && gates.streamSessionQueryReason().isEmpty(),
                            supported && gates.streamEventQueryReason().isEmpty(),
                            supported && gates.streamStopReason().isEmpty(),
                            supported && capability.getSnapshot().getFeatures().isDeduplication());
                },Clock.systemUTC());
    }
    @Bean public org.jeecg.modules.ai.stream.application.StreamQueryService aiStreamQueryService(
            StreamSourceRepository sources,StreamSessionRepository sessions,StreamEventRepository events) {
        return new org.jeecg.modules.ai.stream.application.StreamQueryService(sources,sessions,events);
    }
    @Bean public org.jeecg.modules.ai.stream.application.StopStreamSessionService aiStopStreamService(StreamSessionRepository sessions) {
        return new org.jeecg.modules.ai.stream.application.StopStreamSessionService(sessions,Clock.systemUTC());
    }
    @Bean(initMethod="start",destroyMethod="close")
    public JobWorker aiJobWorker(MyBatisJobRepository jobs,ObjectProvider<InferenceProvider> provider,
                                 ObjectProvider<VideoAnalysisProvider> video,ObjectProvider<ProviderArtifactReader> reader,
                                 AssetService assets,JobsProperties p,CapabilityRepository capabilities,
                                 ObjectProvider<ProviderProperties> remote,AiRuntimeMetrics metrics) {
        metrics.bindJobs(jobs);
        return new JobWorker(jobs,jobs,provider,video,reader,assets,Clock.systemUTC(),p.getParallelism(),
                Math.max(p.getMaxOutputBytes(),p.getMaxVideoOutputBytes()),capabilities,
                recoveryLease(p,remote.getIfAvailable()),metrics);
    }
    @Bean(initMethod="start",destroyMethod="close")
    public StreamSessionWorker aiStreamWorker(MyBatisStreamSessionRepository sessions,StreamSourceRepository sources,
            StreamEventRepository events,ObjectProvider<StreamSessionProvider> provider,ObjectProvider<ProviderArtifactReader> reader,
            AssetService assets,JobsProperties p,ObjectProvider<ProviderProperties> remote,AiRuntimeMetrics metrics) {
        metrics.bindStreams(sessions);
        return new StreamSessionWorker(sessions,sources,events,provider,reader,assets,Clock.systemUTC(),
                p.getParallelism(),p.getMaxOutputBytes(),recoveryLease(p,remote.getIfAvailable()),metrics);
    }
    private long recoveryLease(JobsProperties jobs,ProviderProperties provider) {
        long minimum=60000;
        if (provider!=null) {
            long request=(long)provider.getConnectTimeoutMs()+provider.getRequestTimeoutMs()+5000;
            long transfer=(long)provider.getConnectTimeoutMs()+3L*provider.getTransferTimeoutMs()+8000;
            minimum=Math.max(minimum,Math.max(request,transfer));
        }
        if (minimum>86400000) throw new IllegalArgumentException("Provider timeouts exceed bounded AI recovery lease");
        return Math.max(jobs.getRecoveryLeaseMs(),minimum);
    }
    @Override public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0,new StrictInferenceJsonConverter());
    }
}
