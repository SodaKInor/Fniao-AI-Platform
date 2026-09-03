package org.jeecg.modules.ai.integration;

import java.lang.reflect.Constructor;
import org.junit.Test;
import static org.junit.Assert.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.jeecg.common.aspect.DictAspect;
import org.jeecg.modules.ai.api.controller.*;

/** The host application's dictionary aspect applies to every module controller. */
public class ControllerProxyTest {
    @Test public void allJobControllersSupportTheActualHostAspect() throws Exception {
        for (Class<?> type : new Class<?>[]{InferenceController.class,AssetController.class,JobController.class}) {
            Constructor<?> constructor=type.getConstructors()[0];
            Object controller=constructor.newInstance(new Object[constructor.getParameterCount()]);
            AspectJProxyFactory factory=new AspectJProxyFactory(controller);
            factory.setProxyTargetClass(true);
            factory.addAspect(new DictAspect());
            assertTrue(type.getName(),AopUtils.isCglibProxy(factory.getProxy()));
        }
    }
}
