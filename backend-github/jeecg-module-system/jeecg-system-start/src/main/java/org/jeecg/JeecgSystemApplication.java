package org.jeecg;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
* 单体启动类
* 报错提醒: 未集成mongo报错，可以打开启动类上面的注释 exclude={MongoAutoConfiguration.class}
*/
@Slf4j
@SpringBootApplication
//@EnableAutoConfiguration(exclude={MongoAutoConfiguration.class})
public class JeecgSystemApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(JeecgSystemApplication.class);
    }
    public static void main(String[] args) throws UnknownHostException {
       // System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

       // System.load("C:\\JAVAAI\\opencv\\build\\java\\x64\\opencv_java481.dll");
        ConfigurableApplicationContext application = SpringApplication.run(JeecgSystemApplication.class, args);
        Environment env = application.getEnvironment();
        String ip = InetAddress.getLocalHost().getHostAddress();
        String port = env.getProperty("server.port");
        String path = oConvertUtils.getString(env.getProperty("server.servlet.context-path"));
        String opencvpath = env.getProperty("opencv");
        boolean opencvEnabled = env.getProperty("wgai.features.opencv.enabled", Boolean.class, false);
        if (opencvEnabled) {
            if (oConvertUtils.isEmpty(opencvpath)) {
                throw new IllegalStateException("OpenCV is enabled but the native library path is empty");
            }
            System.load(opencvpath);
            log.info("OpenCV native library loaded from {}", opencvpath);
        } else {
            log.info("OpenCV native loading is disabled for the core deployment");
        }
        log.info("\n----------------------------------------------------------\n\t" +
                "Application Jeecg-Boot is running! Access URLs:\n\t" +
                "Local: \t\thttp://localhost:" + port + path + "/\n\t" +
                "External: \thttp://" + ip + ":" + port + path + "/\n\t" +
                "Swagger文档: \thttp://" + ip + ":" + port + path + "/doc.html\n\t" +
                "OpenCV enabled: " + opencvEnabled + "\n"+
                "----------------------------------------------------------");

    }

}
