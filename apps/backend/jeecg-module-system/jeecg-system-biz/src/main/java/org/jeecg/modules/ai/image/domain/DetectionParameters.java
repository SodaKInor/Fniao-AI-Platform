package org.jeecg.modules.ai.image.domain;

import java.math.BigDecimal;

/**
 * Fixed image-detection.v1 parameters. Application validates threshold [0,1] and count [1,100].
 */
public final class DetectionParameters {
    private final BigDecimal threshold;
    private final int maxDetections;
    private final boolean annotate;

    public DetectionParameters(
            BigDecimal threshold,
            int maxDetections,
            boolean annotate) {
        this.threshold = threshold;
        this.maxDetections = maxDetections;
        this.annotate = annotate;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public int getMaxDetections() {
        return maxDetections;
    }

    public boolean isAnnotate() {
        return annotate;
    }
}
