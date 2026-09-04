package org.jeecg.modules.ai.domain;

/** Server-side source mapping. providerSourceRef must never be mapped into API DTOs. */
public final class StreamSource {
    private final String streamSourceId;
    private final String ownerId;
    private final String displayName;
    private final String providerSourceRef;
    private final boolean enabled;
    private final String unavailableReason;

    public StreamSource(
            String streamSourceId,
            String ownerId,
            String displayName,
            String providerSourceRef,
            boolean enabled,
            String unavailableReason) {
        this.streamSourceId = streamSourceId;
        this.ownerId = ownerId;
        this.displayName = displayName;
        this.providerSourceRef = providerSourceRef;
        this.enabled = enabled;
        this.unavailableReason = unavailableReason;
    }

    public String getStreamSourceId() {
        return streamSourceId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getProviderSourceRef() {
        return providerSourceRef;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }
}
