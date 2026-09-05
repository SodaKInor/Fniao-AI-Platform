package org.jeecg.modules.ai.integration;

import org.jeecg.modules.ai.asset.domain.Asset;
import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.capability.domain.Capability;
import org.jeecg.modules.ai.capability.domain.CapabilitySnapshot;
import org.jeecg.modules.ai.capability.domain.ProviderFeatures;
import org.jeecg.modules.ai.job.application.AiRequestException;
import org.jeecg.modules.ai.job.application.JobQueryService;
import org.jeecg.modules.ai.job.application.SubmitInferenceService;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.JobRecord;
import org.jeecg.modules.ai.job.domain.JobState;
import org.jeecg.modules.ai.provider.config.ProviderConfiguration;
import org.jeecg.modules.ai.provider.config.ProviderProperties;

import java.io.ByteArrayInputStream;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.jeecg.modules.ai.assetsjobs.DbFixture;
import org.jeecg.modules.ai.asset.application.AssetService;
import org.jeecg.modules.ai.capability.application.CapabilityQueryService;
import org.jeecg.modules.ai.operations.config.JobsConfiguration;

public class CombinedConfigurationTest {
    private AnnotationConfigApplicationContext start(DbFixture f,String mode,long limit) throws Exception {
        CapabilitySnapshot snapshot=new CapabilitySnapshot("image-detection.v1","mock-v1","mock","mock-v1",
                "image-detection.v1",null,new ProviderFeatures(false,false,false));
        Capability c=new Capability(snapshot,"模拟图片检测",true,true,true,"",f.capability.getInputMediaTypes(),10485760,10485760,1500);
        f.sql.update("UPDATE ai_capability_binding SET descriptor_json=?",f.codec.write(c));
        AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();
        Map<String,Object> p=new HashMap<>();
        p.put("wgai.ai.jobs.private-root",f.privateRoot.toString());
        p.put("wgai.inference.mode",mode); p.put("wgai.inference.upload-max-bytes",limit);
        p.put("jeecg.path.upload","/validation/public");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("00-combined",p));
        SqlSessionFactoryBean factory=new SqlSessionFactoryBean(); factory.setDataSource(f.sql.getDataSource());
        org.apache.ibatis.session.SqlSessionFactory sessions=factory.getObject();
        context.registerBean("sqlSessionFactory",org.apache.ibatis.session.SqlSessionFactory.class,() -> sessions);
        context.registerBean("transactionManager",org.springframework.transaction.PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(f.sql.getDataSource()));
        context.register(ProviderConfiguration.class,JobsConfiguration.class,
                org.jeecg.modules.ai.capability.api.mapper.CapabilityMapper.class);
        context.refresh();
        return context;
    }

    @Test public void bothMappersAndRealProviderCompleteDurableJobThenDisablePreservesDuplicate() throws Exception {
        try (DbFixture f=new DbFixture(); AnnotationConfigApplicationContext c=start(f,"mock",10485760)) {
            assertNotNull(c.getBean("aiCapabilityDtoMapper"));
            assertNotNull(c.getBean(org.jeecg.modules.ai.capability.persistence.mapper.CapabilityMapper.class));
            AssetService assets=c.getBean(AssetService.class);
            Asset input=assets.upload("owner-a",new ContentMetadata("in.png","image/png",(long)f.png.length,null),new ByteArrayInputStream(f.png));
            SubmitInferenceService submit=c.getBean(SubmitInferenceService.class);
            JobRecord job=submit.submit("owner-a","combined-one","image-detection.v1",input.getAssetId(),f.parameters(),null).getJob();
            JobQueryService query=c.getBean(JobQueryService.class);
            long until=System.currentTimeMillis()+8000;
            while (System.currentTimeMillis()<until && query.owned(job.getRequest().getRequestId(),"owner-a").getState()!=JobState.SUCCEEDED) Thread.sleep(40);
            assertEquals(JobState.SUCCEEDED,query.owned(job.getRequest().getRequestId(),"owner-a").getState());
            assertEquals(Integer.valueOf(2),f.sql.queryForObject("SELECT COUNT(*) FROM ai_asset",Integer.class));
            c.getBean(ProviderProperties.class).setMode("disabled");
            assertFalse(c.getBean(CapabilityQueryService.class).list(true).get(0).isAvailable());
            assertEquals(job.getRequest().getRequestId(),submit.submit("owner-a","combined-one","image-detection.v1",input.getAssetId(),f.parameters(),null).getJob().getRequest().getRequestId());
            reject(submit,input,f,"combined-new",ErrorCode.CAPABILITY_UNAVAILABLE);
            assertEquals(Integer.valueOf(1),f.sql.queryForObject("SELECT COUNT(*) FROM ai_job",Integer.class));
            assertEquals(JobState.SUCCEEDED,query.owned(job.getRequest().getRequestId(),"owner-a").getState());
        }
    }

    @Test public void advertisedProviderLimitAlsoRejectsNewSubmissionBeforePersistence() throws Exception {
        try (DbFixture f=new DbFixture(); AnnotationConfigApplicationContext c=start(f,"mock",1)) {
            Asset input=f.input("owner-a");
            assertEquals(1,c.getBean(CapabilityQueryService.class).list(true).get(0).getMaxInputBytes());
            reject(c.getBean(SubmitInferenceService.class),input,f,"limit-key",ErrorCode.LIMIT_EXCEEDED);
            assertEquals(Integer.valueOf(0),f.sql.queryForObject("SELECT COUNT(*) FROM ai_job",Integer.class));
        }
    }

    @Test public void unconfirmedRemoteNeverAdmitsDatabaseAdvertisedAvailableBinding() throws Exception {
        try (DbFixture f=new DbFixture(); AnnotationConfigApplicationContext c=start(f,"remote",10485760)) {
            reject(c.getBean(SubmitInferenceService.class),f.input("owner-a"),f,"remote-key",ErrorCode.CAPABILITY_UNAVAILABLE);
            assertEquals(Integer.valueOf(0),f.sql.queryForObject("SELECT COUNT(*) FROM ai_job",Integer.class));
        }
    }

    private void reject(SubmitInferenceService submit,Asset input,DbFixture f,String key,ErrorCode expected) {
        try {
            submit.submit(input.getOwnerId(),key,"image-detection.v1",input.getAssetId(),f.parameters(),null);
            fail("Expected admission rejection");
        } catch (AiRequestException error) { assertEquals(expected,error.getCode()); }
    }
}
