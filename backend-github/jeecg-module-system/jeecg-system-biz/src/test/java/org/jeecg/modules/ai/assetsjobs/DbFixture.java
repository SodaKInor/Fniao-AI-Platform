package org.jeecg.modules.ai.assetsjobs;

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
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;
import org.jeecg.modules.ai.persistence.mapper.*;
import org.jeecg.modules.ai.persistence.converter.*;
import org.jeecg.modules.ai.persistence.repository.*;
import org.jeecg.modules.ai.application.assets.AssetService;
import org.jeecg.modules.ai.application.jobs.*;
import org.jeecg.modules.ai.storage.PrivateFileArtifactStore;

public final class DbFixture implements AutoCloseable {
    public final Clock clock=Clock.systemUTC();
    public final JdbcTemplate sql;
    public final SnapshotCodec codec=new SnapshotCodec();
    public final MyBatisJobRepository jobs;
    public final AssetRepository assets;
    public final CapabilityRepository capabilities;
    public final AssetService files;
    public final SubmitInferenceService submit;
    public final JobQueryService query;
    public final PrivateFileArtifactStore store;
    public final Path privateRoot;
    public final byte[] png;
    public final Capability capability;
    public final AtomicInteger calls=new AtomicInteger();
    public final AtomicInteger reads=new AtomicInteger();
    public final AtomicInteger closes=new AtomicInteger();

    public DbFixture() throws Exception { this(20,1); }
    public DbFixture(int pending,int active) throws Exception {
        DriverManagerDataSource source=new DriverManagerDataSource("jdbc:mysql://mysql:3306/wgai_ri_04a_assets_jobs?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "foundation",System.getenv("MYSQL_PASSWORD"));
        source.setDriverClassName("com.mysql.cj.jdbc.Driver"); sql=new JdbcTemplate(source);
        for (String table:Arrays.asList("ai_job_event","ai_job","ai_asset","ai_capability_binding")) sql.update("DELETE FROM "+table);
        org.apache.ibatis.session.Configuration configuration=new org.apache.ibatis.session.Configuration();
        configuration.addMapper(JobMapper.class); configuration.addMapper(AssetMapper.class); configuration.addMapper(CapabilityMapper.class);
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(configuration);
        SqlSessionFactory sessions=factory.getObject(); SqlSessionTemplate template=new SqlSessionTemplate(sessions);
        jobs=new MyBatisJobRepository(template.getMapper(JobMapper.class),template.getMapper(AssetMapper.class),codec,
                new DataSourceTransactionManager(source),pending,active);
        assets=new MyBatisAssetRepository(template.getMapper(AssetMapper.class),new RecordConverter(codec));
        capabilities=new MyBatisCapabilityRepository(template.getMapper(CapabilityMapper.class),codec);
        privateRoot=Files.createTempDirectory(Paths.get("/validation"),"private-");
        store=new PrivateFileArtifactStore(privateRoot,Collections.singletonList(Paths.get("/validation/public")),4096);
        files=new AssetService(assets,store,clock,10*1024*1024,Duration.ofDays(7),Duration.ofDays(30));
        CapabilitySnapshot snapshot=new CapabilitySnapshot("image-detection.v1","1.0.0","fixture","fixture-v1","image-detection.v1",null,
                new ProviderFeatures(false,false,false));
        capability=new Capability(snapshot,"Mock detection",true,true,true,"",Arrays.asList("image/png","image/jpeg"),10*1024*1024,10*1024*1024,1500);
        sql.update("INSERT INTO ai_capability_binding(capability_code,descriptor_json) VALUES(?,?)","image-detection.v1",codec.write(capability));
        submit=new SubmitInferenceService(jobs,capabilities,files,clock); query=new JobQueryService(jobs,assets);
        png=Files.readAllBytes(Paths.get("/workspace/backend-github/integrations/ai-contracts/examples/input.png"));
    }
    public DetectionParameters parameters() { return new DetectionParameters(new BigDecimal("0.50"),10,true); }
    public Asset input(String owner) { return files.upload(owner,new ContentMetadata("input.png","image/png",(long)png.length,null),new ByteArrayInputStream(png)); }
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
