package org.jeecg.modules.ai.domain;

import java.math.BigDecimal;

/** Bounded video-file-analysis.v1 parameters; application enforces business ranges. */
public final class VideoParameters {
    private final BigDecimal threshold;
    private final long sampleIntervalMillis;
    private final int maxEvents;
    private final boolean includeSnapshots;
    private final boolean annotate;

    public VideoParameters(
            BigDecimal threshold,
            long sampleIntervalMillis,
            int maxEvents,
            boolean includeSnapshots,
            boolean annotate) {
        this.threshold = threshold;
        this.sampleIntervalMillis = sampleIntervalMillis;
        this.maxEvents = maxEvents;
        this.includeSnapshots = includeSnapshots;
        this.annotate = annotate;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public long getSampleIntervalMillis() {
        return sampleIntervalMillis;
    }

    public int getMaxEvents() {
        return maxEvents;
    }

    public boolean isIncludeSnapshots() {
        return includeSnapshots;
    }

    public boolean isAnnotate() {
        return annotate;
    }
}
