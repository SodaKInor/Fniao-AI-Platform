package org.jeecg.modules.ai.domain;

/** Bounded client-facing polling preferences; no endpoint or source secrets. */
public final class StreamParameters {
    private final int maxEventsPerPoll;
    private final long pollIntervalMillis;
    private final boolean includeSnapshots;

    public StreamParameters(
            int maxEventsPerPoll,
            long pollIntervalMillis,
            boolean includeSnapshots) {
        this.maxEventsPerPoll = maxEventsPerPoll;
        this.pollIntervalMillis = pollIntervalMillis;
        this.includeSnapshots = includeSnapshots;
    }

    public int getMaxEventsPerPoll() {
        return maxEventsPerPoll;
    }

    public long getPollIntervalMillis() {
        return pollIntervalMillis;
    }

    public boolean isIncludeSnapshots() {
        return includeSnapshots;
    }
}
