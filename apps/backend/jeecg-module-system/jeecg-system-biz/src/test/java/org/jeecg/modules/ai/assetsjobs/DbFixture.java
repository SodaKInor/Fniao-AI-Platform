package org.jeecg.modules.ai.assetsjobs;

import org.jeecg.modules.ai.asset.domain.Asset;
import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.asset.persistence.mapper.AssetMapper;
import org.jeecg.modules.ai.asset.persistence.repository.MyBatisAssetRepository;
import org.jeecg.modules.ai.asset.port.AssetRepository;
import org.jeecg.modules.ai.capability.domain.Capability;
import org.jeecg.modules.ai.capability.domain.CapabilitySnapshot;
import org.jeecg.modules.ai.capability.domain.ProviderFeatures;
import org.jeecg.modules.ai.capability.persistence.repository.MyBatisCapabilityRepository;
import org.jeecg.modules.ai.capability.port.CapabilityRepository;
import org.jeecg.modules.ai.client.ClientTestInputs;
import org.jeecg.modules.ai.image.domain.DetectionData;
import org.jeecg.modules.ai.image.domain.DetectionParameters;
import org.jeecg.modules.ai.image.domain.ProviderResult;
import org.jeecg.modules.ai.image.port.InferenceProvider;
import org.jeecg.modules.ai.job.application.DispatchJobService;
import org.jeecg.modules.ai.job.application.JobQueryService;
import org.jeecg.modules.ai.job.application.SubmitInferenceService;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.job.domain.JobRecord;
import org.jeecg.modules.ai.job.domain.JobRequest;
import org.jeecg.modules.ai.job.persistence.converter.RecordConverter;
import org.jeecg.modules.ai.job.persistence.converter.SnapshotCodec;
import org.jeecg.modules.ai.job.persistence.mapper.JobMapper;
import org.jeecg.modules.ai.job.persistence.repository.MyBatisJobRepository;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.result.application.CollectResultService;
import org.jeecg.modules.ai.result.domain.ProviderArtifact;
import org.jeecg.modules.ai.result.port.ProviderArtifactReader;
import org.jeecg.modules.ai.stream.domain.StreamParameters;
import org.jeecg.modules.ai.stream.domain.StreamSource;
import org.jeecg.modules.ai.stream.persistence.converter.StreamRecordConverter;
import org.jeecg.modules.ai.stream.persistence.converter.StreamSnapshotCodec;
import org.jeecg.modules.ai.stream.persistence.mapper.StreamEventMapper;
import org.jeecg.modules.ai.stream.persistence.mapper.StreamSessionMapper;
import org.jeecg.modules.ai.stream.persistence.mapper.StreamSourceMapper;
import org.jeecg.modules.ai.stream.persistence.repository.MyBatisStreamEventRepository;
import org.jeecg.modules.ai.stream.persistence.repository.MyBatisStreamSessionRepository;
import org.jeecg.modules.ai.stream.persistence.repository.MyBatisStreamSourceRepository;
import org.jeecg.modules.ai.stream.port.StreamEventRepository;
import org.jeecg.modules.ai.stream.port.StreamSourceRepository;
import org.jeecg.modules.ai.video.domain.VideoParameters;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.jeecg.modules.ai.capability.persistence.mapper.CapabilityMapper;
import org.jeecg.modules.ai.asset.application.AssetService;
import org.jeecg.modules.ai.operations.config.AiRuntimeMetrics;
import org.jeecg.modules.ai.asset.storage.PrivateFileArtifactStore;

public final class DbFixture implements AutoCloseable {
    public final Clock clock=Clock.systemUTC();
    public final JdbcTemplate sql;
    public final SnapshotCodec codec=new SnapshotCodec();
    public final StreamSnapshotCodec streamCodec=new StreamSnapshotCodec();
    public final MyBatisJobRepository jobs;
    public final MyBatisStreamSessionRepository streamSessions;
    public final StreamSourceRepository streamSources;
    public final StreamEventRepository streamEvents;
    public final AssetRepository assets;
    public final CapabilityRepository capabilities;
    public final AssetService files;
    public final SubmitInferenceService submit;
    public final JobQueryService query;
    public final PrivateFileArtifactStore store;
    public final Path privateRoot;
    public final byte[] png;
    public final byte[] mp4;
    public final Capability capability;
    public final Capability videoCapability;
    public final Capability streamCapability;
    public final AtomicInteger calls=new AtomicInteger();
    public final AtomicInteger reads=new AtomicInteger();
    public final AtomicInteger closes=new AtomicInteger();

    public DbFixture() throws Exception { this(20,1,AiRuntimeMetrics.disabled()); }
    public DbFixture(int pending,int active) throws Exception { this(pending,active,AiRuntimeMetrics.disabled()); }
    public DbFixture(int pending,int active,AiRuntimeMetrics metrics) throws Exception {
        DriverManagerDataSource source=new DriverManagerDataSource(System.getProperty("ai.test.jdbc",
                "jdbc:mysql://mysql:3306/wgai_ri_04a_assets_jobs?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"),
                "foundation",System.getenv("MYSQL_PASSWORD"));
        source.setDriverClassName("com.mysql.cj.jdbc.Driver"); sql=new JdbcTemplate(source);
        for (String table:Arrays.asList("ai_stream_event","ai_stream_session","ai_stream_source",
                "ai_job_event","ai_job","ai_asset","ai_capability_binding")) sql.update("DELETE FROM "+table);
        org.apache.ibatis.session.Configuration configuration=new org.apache.ibatis.session.Configuration();
        configuration.addMapper(JobMapper.class); configuration.addMapper(AssetMapper.class); configuration.addMapper(CapabilityMapper.class);
        configuration.addMapper(StreamSourceMapper.class); configuration.addMapper(StreamSessionMapper.class);
        configuration.addMapper(StreamEventMapper.class);
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        SqlSessionFactory sessions=factory.getObject(); SqlSessionTemplate template=new SqlSessionTemplate(sessions);
        jobs=new MyBatisJobRepository(template.getMapper(JobMapper.class),template.getMapper(AssetMapper.class),codec,
                new DataSourceTransactionManager(source),pending,active);
        assets=new MyBatisAssetRepository(template.getMapper(AssetMapper.class),new RecordConverter(codec));
        capabilities=new MyBatisCapabilityRepository(template.getMapper(CapabilityMapper.class),codec);
        StreamRecordConverter streams=new StreamRecordConverter(streamCodec,codec);
        streamSources=new MyBatisStreamSourceRepository(template.getMapper(StreamSourceMapper.class),streams);
        streamSessions=new MyBatisStreamSessionRepository(template.getMapper(StreamSessionMapper.class),streams,
                new DataSourceTransactionManager(source),pending,active);
        streamEvents=new MyBatisStreamEventRepository(template.getMapper(StreamSessionMapper.class),
                template.getMapper(StreamEventMapper.class),template.getMapper(AssetMapper.class),streams,
                new DataSourceTransactionManager(source),count -> metrics.streamEvents("inserted",count),
                count -> metrics.streamEvents("duplicate",count));
        metrics.bindJobs(jobs); metrics.bindStreams(streamSessions);
        privateRoot=Files.createTempDirectory(Paths.get("/validation"),"private-");
        store=new PrivateFileArtifactStore(privateRoot,Collections.singletonList(Paths.get("/validation/public")),4096);
        files=new AssetService(assets,store,clock,10*1024*1024,512L*1024*1024,
                Duration.ofDays(7),Duration.ofDays(30));
        CapabilitySnapshot snapshot=new CapabilitySnapshot("image-detection.v1","1.0.0","fixture","fixture-v1","image-detection.v1",null,
                new ProviderFeatures(false,false,false));
        capability=new Capability(snapshot,"Mock detection",true,true,true,"",Arrays.asList("image/png","image/jpeg"),10*1024*1024,10*1024*1024,1500);
        sql.update("INSERT INTO ai_capability_binding(capability_code,descriptor_json) VALUES(?,?)","image-detection.v1",codec.write(capability));
        CapabilitySnapshot videoSnapshot=new CapabilitySnapshot("video-file-analysis.v1","1.1.0","fixture","fixture-v1",
                "video-file-analysis.v1",null,new ProviderFeatures(false,false,false));
        videoCapability=new Capability(videoSnapshot,"Mock video",true,true,true,"",Collections.singletonList("video/mp4"),
                512L*1024*1024,512L*1024*1024,1500);
        CapabilitySnapshot streamSnapshot=new CapabilitySnapshot("video-stream-analysis.v1","1.1.0","fixture","fixture-v1",
                "video-stream-analysis.v1",null,new ProviderFeatures(true,true,true));
        streamCapability=new Capability(streamSnapshot,"Mock stream",true,true,true,"",Collections.emptyList(),1,10*1024*1024,1500);
        sql.update("INSERT INTO ai_capability_binding(capability_code,descriptor_json) VALUES(?,?),(?,?)",
                "video-file-analysis.v1",codec.write(videoCapability),"video-stream-analysis.v1",codec.write(streamCapability));
        submit=new SubmitInferenceService(jobs,capabilities,files,clock); query=new JobQueryService(jobs,assets);
        png=Files.readAllBytes(ClientTestInputs.EXAMPLES.resolve("input.png"));
        mp4=new byte[]{0,0,0,24,'f','t','y','p','i','s','o','m',0,0,0,0,'i','s','o','m','a','v','c','1'};
    }
    public DetectionParameters parameters() { return new DetectionParameters(new BigDecimal("0.50"),10,true); }
    public Asset input(String owner) { return files.upload(owner,new ContentMetadata("input.png","image/png",(long)png.length,null),new ByteArrayInputStream(png)); }
    public Asset videoInput(String owner) { return files.upload(owner,new ContentMetadata("input.mp4","video/mp4",(long)mp4.length,null),new ByteArrayInputStream(mp4)); }
    public VideoParameters videoParameters() { return new VideoParameters(new BigDecimal("0.50"),1000,20,true,true); }
    public StreamParameters streamParameters() { return new StreamParameters(20,250,true); }
    public StreamSource streamSource(String owner,String id,boolean enabled) {
        long now=clock.millis();
        sql.update("INSERT INTO ai_stream_source(stream_source_id,owner_id,display_name,provider_source_ref,enabled,unavailable_reason,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                id,owner,"Fixture camera","provider-source-fixture",enabled,enabled ? null : "Disabled",now,now);
        return streamSources.findOwned(id,owner).get();
    }
    public JobRecord submit(Asset asset,String key) {
        return submit.submit(asset.getOwnerId(),key,"image-detection.v1",asset.getAssetId(),parameters(),null).getJob();
    }
    public JobRequest request(String owner,String key) {
        Instant now=Instant.ofEpochMilli(clock.millis());
        return new JobRequest(UUID.randomUUID().toString(),owner,key,"digest","input",parameters(),capability.getSnapshot(),null,true,now);
    }
    public ProviderResult result(boolean artifact) {
        ProviderArtifact file=new ProviderArtifact("fixture:approved-image",new ContentMetadata("output.png","image/png",(long)png.length,null),clock.instant().plusSeconds(120));
        return new ProviderResult("external-test",true,new DetectionData("detection.v1",2,2,Collections.emptyList()),
                artifact ? Collections.singletonList(file) : Collections.emptyList());
    }
    public InferenceProvider provider(ProviderResult result) {
        return request -> {
            calls.incrementAndGet();
            if (!"WAITING".equals(sql.queryForObject("SELECT state FROM ai_job WHERE request_id=?",String.class,request.getRequestId())))
                throw new AssertionError("Provider invoked before durable claim");
            try (InputStream input=request.getInput().openStream()) { while (input.read()!=-1) { } }
            catch (IOException e) { throw new ProviderException(ErrorCode.PROVIDER_PROTOCOL,ExecutionCertainty.NOT_STARTED,"Fixture input failure"); }
            return result;
        };
    }
    public ProviderArtifactReader reader() {
        return (snapshot,artifact,limit) -> {
            reads.incrementAndGet();
            return new ByteArrayInputStream(png) { public void close() throws IOException { closes.incrementAndGet(); super.close(); } };
        };
    }
    public DispatchJobService dispatcher(InferenceProvider provider,ProviderArtifactReader reader) {
        return new DispatchJobService(jobs,provider,files,new CollectResultService(reader,files,clock,10*1024*1024,capabilities),clock);
    }
    public JobRecord get(JobRecord job) { return query.owned(job.getRequest().getRequestId(),job.getRequest().getOwnerId()); }
    public long countFiles() throws IOException { try (java.util.stream.Stream<Path> stream=Files.list(privateRoot)) { return stream.count(); } }
    public void close() throws IOException {
        try (java.util.stream.Stream<Path> paths=Files.walk(privateRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (IOException e) { throw new UncheckedIOException(e); } });
        }
    }
}
