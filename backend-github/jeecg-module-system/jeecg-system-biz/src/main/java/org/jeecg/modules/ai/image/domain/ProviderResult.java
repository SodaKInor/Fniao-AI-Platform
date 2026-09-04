package org.jeecg.modules.ai.image.domain;

import org.jeecg.modules.ai.result.domain.ProviderArtifact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Confirmed complete synchronous response, before local artifact collection.
 * providerRequestId is optional correlation, not evidence of remote query/cancel support.
 * Persist this checkpoint before collection; it is not itself a successful local job.
 */
public final class ProviderResult {
    private final String providerRequestId;
    private final boolean simulated;
    private final DetectionData data;
    private final List<ProviderArtifact> artifacts;

    public ProviderResult(
            String providerRequestId,
            boolean simulated,
            DetectionData data,
            List<ProviderArtifact> artifacts) {
        this.providerRequestId = providerRequestId;
        this.simulated = simulated;
        this.data = data;
        this.artifacts = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(artifacts, "artifacts")));
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public DetectionData getData() {
        return data;
    }

    public List<ProviderArtifact> getArtifacts() {
        return artifacts;
    }
}
