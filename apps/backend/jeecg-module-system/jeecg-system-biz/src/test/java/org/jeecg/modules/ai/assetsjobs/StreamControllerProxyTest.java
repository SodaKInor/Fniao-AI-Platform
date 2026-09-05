package org.jeecg.modules.ai.assetsjobs;

import org.jeecg.modules.ai.job.api.JobController;
import org.jeecg.modules.ai.stream.api.StreamController;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.aop.framework.ProxyFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class StreamControllerProxyTest {
    @Test
    public void supportsClassBasedSecurityProxyUsedByTheApplication() {
        ProxyFactory factory = new ProxyFactory(new StreamController(null, null, null));
        factory.setProxyTargetClass(true);

        assertNotNull(factory.getProxy());
    }

    @Test
    public void jobControllerSelectsOneApplicationConstructor() {
        long injectable = java.util.Arrays.stream(JobController.class.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .count();

        assertEquals(1L, injectable);
    }
}
