package org.jeecg.modules.ai.config.jobs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix="wgai.ai.jobs")
public class JobsProperties {
    private String privateRoot="/data/ai-private";
    private int parallelism=1;
    private int maxQueued=20;
    private long maxInputBytes=10*1024*1024;
    private long maxOutputBytes=10*1024*1024;
    private int maxImageDimension=4096;
    private int inputRetentionDays=7;
    private int outputRetentionDays=30;

    public void validate() {
        if (parallelism<1 || parallelism>32 || maxQueued<1 || maxQueued>1000
                || maxInputBytes<1 || maxInputBytes>10*1024*1024 || maxOutputBytes<1 || maxOutputBytes>10*1024*1024
                || maxImageDimension<1 || maxImageDimension>4096 || inputRetentionDays<1 || outputRetentionDays<1)
            throw new IllegalArgumentException("Invalid AI jobs configuration");
    }
}
