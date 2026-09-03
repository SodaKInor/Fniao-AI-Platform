package org.jeecg.modules.ai.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Local configured capability and limits. Unconfirmed remote bindings MUST be unavailable.
 * Historical jobs use their snapshot; disabling a binding blocks new submissions only.
 */
public final class Capability {
    private final CapabilitySnapshot snapshot;
    private final String displayName;
    private final boolean enabled;
    private final boolean available;
    private final boolean simulated;
    private final String unavailableReason;
    private final List<String> inputMediaTypes;
    private final long maxInputBytes;
    private final long maxOutputBytes;
    private final long maxWaitMillis;

    public Capability(
            CapabilitySnapshot snapshot,
            String displayName,
            boolean enabled,
            boolean available,
            boolean simulated,
            String unavailableReason,
            List<String> inputMediaTypes,
            long maxInputBytes,
            long maxOutputBytes,
            long maxWaitMillis) {
        this.snapshot = snapshot;
        this.displayName = displayName;
        this.enabled = enabled;
        this.available = available;
        this.simulated = simulated;
        this.unavailableReason = unavailableReason;
        this.inputMediaTypes = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(inputMediaTypes, "inputMediaTypes")));
        this.maxInputBytes = maxInputBytes;
        this.maxOutputBytes = maxOutputBytes;
        this.maxWaitMillis = maxWaitMillis;
    }

    public CapabilitySnapshot getSnapshot() {
        return snapshot;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public List<String> getInputMediaTypes() {
        return inputMediaTypes;
    }

    public long getMaxInputBytes() {
        return maxInputBytes;
    }

    public long getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public long getMaxWaitMillis() {
        return maxWaitMillis;
    }
}
