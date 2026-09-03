package org.jeecg.modules.ai.client;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Passive observations only; never probes an unconfirmed endpoint or claims model readiness. */
public final class ProviderObservations {
    private final ConcurrentHashMap<String, Observation> observations = new ConcurrentHashMap<>();
    private final Clock clock;

    public ProviderObservations(Clock clock) { this.clock = clock; }

    public void record(String key, String reason) {
        observations.put(key, new Observation(reason, clock.instant()));
    }

    public String reason(String key) {
        Observation value = observations.get(key);
        if (value == null || value.time.plusSeconds(60).isBefore(clock.instant())) return "外部可达性未确认";
        return value.reason;
    }

    private static final class Observation {
        private final String reason;
        private final Instant time;
        private Observation(String reason, Instant time) { this.reason = reason; this.time = time; }
    }
}
