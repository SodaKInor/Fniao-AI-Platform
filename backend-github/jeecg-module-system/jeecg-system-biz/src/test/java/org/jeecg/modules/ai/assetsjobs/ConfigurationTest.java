package org.jeecg.modules.ai.assetsjobs;

import java.io.ByteArrayInputStream;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.jeecg.modules.ai.config.jobs.JobsConfiguration;
import org.jeecg.modules.ai.application.jobs.*;
import org.jeecg.modules.ai.application.assets.AssetService;
import org.jeecg.modules.ai.domain.*;

public class ConfigurationTest {
    @Test public void springConfigurationWiresRealRepositoriesAndRequiresNoProductionTestDouble() throws Exception {
        try (DbFixture f=new DbFixture(); AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext()) {
            Map<String,Object> properties=new HashMap<>();
            properties.put("wgai.ai.jobs.private-root",f.privateRoot.resolve("spring").toString());
            properties.put("jeecg.path.upload","/validation/public"); properties.put("jeecg.path.webapp","/validation/webapp");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("04a-test",properties));
            SqlSessionFactoryBean factory=new SqlSessionFactoryBean(); factory.setDataSource(f.sql.getDataSource());
            org.apache.ibatis.session.SqlSessionFactory sessions=factory.getObject();
            context.registerBean("sqlSessionFactory",org.apache.ibatis.session.SqlSessionFactory.class,() -> sessions);
            context.registerBean("transactionManager",org.springframework.transaction.PlatformTransactionManager.class,
                    () -> new DataSourceTransactionManager(f.sql.getDataSource()));
            context.register(JobsConfiguration.class); context.refresh();
            AssetService assets=context.getBean(AssetService.class);
            Asset input=assets.upload("a",new ContentMetadata("in.png","image/png",(long)f.png.length,null),new ByteArrayInputStream(f.png));
            try {
                context.getBean(SubmitInferenceService.class).submit("a","spring-key","image-detection.v1",input.getAssetId(),f.parameters(),null);
                fail("Missing provider policy must reject new submissions");
            } catch (AiRequestException error) { assertEquals(ErrorCode.CAPABILITY_UNAVAILABLE,error.getCode()); }
            assertEquals(0,context.getBeansOfType(org.jeecg.modules.ai.port.InferenceProvider.class).size());
            assertEquals(0,context.getBeansOfType(org.jeecg.modules.ai.port.VideoAnalysisProvider.class).size());
            assertEquals(0,context.getBeansOfType(org.jeecg.modules.ai.port.StreamSessionProvider.class).size());
            f.streamSource("a","spring_source",true);
            try {
                context.getBean(org.jeecg.modules.ai.application.streams.StartStreamSessionService.class).start(
                        "a","spring-stream","video-stream-analysis.v1","spring_source",f.streamParameters());
                fail("Unconfirmed production stream feature must stay disabled");
            } catch (AiRequestException error) { assertEquals(ErrorCode.CAPABILITY_UNAVAILABLE,error.getCode()); }
        }
    }

    @Test public void videoLimitsAreExplicitlyBounded() {
        org.jeecg.modules.ai.config.jobs.JobsProperties properties=new org.jeecg.modules.ai.config.jobs.JobsProperties();
        properties.validate(); assertEquals(512L*1024*1024,properties.getMaxVideoInputBytes());
        properties.setMaxVideoOutputBytes(512L*1024*1024+1);
        try { properties.validate(); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void stricterCapabilityOutputLimitReachesReaderAndStore() throws Exception {
        try (DbFixture f=new DbFixture()) {
            Capability c=f.capability;
            Capability limited=new Capability(c.getSnapshot(),c.getDisplayName(),true,true,true,"",c.getInputMediaTypes(),c.getMaxInputBytes(),1,1500);
            f.sql.update("UPDATE ai_capability_binding SET descriptor_json=?",f.codec.write(limited));
            JobRecord job=f.submit(f.input("a"),"output-limit-key");
            f.dispatcher(f.provider(f.result(true)),(snapshot,artifact,limit) -> {
                assertEquals(1,limit); f.reads.incrementAndGet(); return new ByteArrayInputStream(f.png);
            }).dispatch(job);
            assertEquals(JobState.FAILED,f.get(job).getState()); assertEquals(1,f.countFiles()); assertEquals(1,f.calls.get());
        }
    }
}
