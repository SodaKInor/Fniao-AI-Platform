package org.jeecg.modules.ai.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Durable business result after complete local artifact persistence. Empty artifacts are valid.
 * Each ID resolves to an owned asset; provider references/URLs must not be substituted.
 */
public final class InferenceResult {
    private final boolean simulated;
    private final DetectionData data;
    private final List<String> artifactIds;

    public InferenceResult(
            boolean simulated,
            DetectionData data,
            List<String> artifactIds) {
        this.simulated = simulated;
        this.data = data;
        this.artifactIds = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(artifactIds, "artifactIds")));
    }

    public boolean isSimulated() {
        return simulated;
    }

    public DetectionData getData() {
        return data;
    }

    public List<String> getArtifactIds() {
        return artifactIds;
    }
}
