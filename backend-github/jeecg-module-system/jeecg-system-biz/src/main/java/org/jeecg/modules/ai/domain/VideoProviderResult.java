package org.jeecg.modules.ai.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Confirmed complete provider response before local snapshot and video collection. */
public final class VideoProviderResult {
    private final String providerRequestId;
    private final boolean simulated;
    private final List<ProviderVideoEvent> events;
    private final ProviderArtifact annotatedVideo;

    public VideoProviderResult(
            String providerRequestId,
            boolean simulated,
            List<ProviderVideoEvent> events,
            ProviderArtifact annotatedVideo) {
        this.providerRequestId = providerRequestId;
        this.simulated = simulated;
        this.events = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(events, "events")));
        this.annotatedVideo = annotatedVideo;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public List<ProviderVideoEvent> getEvents() {
        return events;
    }

    public ProviderArtifact getAnnotatedVideo() {
        return annotatedVideo;
    }
}
