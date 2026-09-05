package org.jeecg.modules.ai.video.domain;

import org.jeecg.modules.ai.result.domain.ResultType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Durable minimum video result: ordered events and local snapshots; annotated video is optional. */
public final class VideoResult {
    private final boolean simulated;
    private final List<VideoEvent> events;
    private final List<String> snapshotAssetIds;
    private final String annotatedVideoAssetId;

    public VideoResult(
            boolean simulated,
            List<VideoEvent> events,
            List<String> snapshotAssetIds,
            String annotatedVideoAssetId) {
        this.simulated = simulated;
        this.events = immutable(events, "events");
        this.snapshotAssetIds = immutable(snapshotAssetIds, "snapshotAssetIds");
        this.annotatedVideoAssetId = annotatedVideoAssetId;
    }

    private static <T> List<T> immutable(List<T> values, String name) {
        return Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(values, name)));
    }

    public ResultType getResultType() {
        return ResultType.VIDEO_TIMELINE;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public List<VideoEvent> getEvents() {
        return events;
    }

    public List<String> getSnapshotAssetIds() {
        return snapshotAssetIds;
    }

    public String getAnnotatedVideoAssetId() {
        return annotatedVideoAssetId;
    }
}
