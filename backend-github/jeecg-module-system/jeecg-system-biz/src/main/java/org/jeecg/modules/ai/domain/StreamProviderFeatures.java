package org.jeecg.modules.ai.domain;

/** Confirmed stream operations only; all values remain false until real evidence exists. */
public final class StreamProviderFeatures {
    private final boolean sessionQuery;
    private final boolean eventQuery;
    private final boolean stop;
    private final boolean deduplication;

    public StreamProviderFeatures(
            boolean sessionQuery,
            boolean eventQuery,
            boolean stop,
            boolean deduplication) {
        this.sessionQuery = sessionQuery;
        this.eventQuery = eventQuery;
        this.stop = stop;
        this.deduplication = deduplication;
    }

    public boolean isSessionQuery() {
        return sessionQuery;
    }

    public boolean isEventQuery() {
        return eventQuery;
    }

    public boolean isStop() {
        return stop;
    }

    public boolean isDeduplication() {
        return deduplication;
    }
}
