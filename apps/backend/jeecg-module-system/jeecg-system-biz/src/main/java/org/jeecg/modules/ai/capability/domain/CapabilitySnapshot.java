package org.jeecg.modules.ai.capability.domain;

/**
 * Immutable binding copied into each job before acceptance. Unknown providerVersion may be null.
 * providerKey selects server-side configuration; contains no URL, credential, filesystem path or wire JSON.
 */
public final class CapabilitySnapshot {
    private final String capabilityCode;
    private final String capabilityVersion;
    private final String providerKey;
    private final String adapterId;
    private final String providerCapabilityCode;
    private final String providerVersion;
    private final ProviderFeatures features;

    public CapabilitySnapshot(
            String capabilityCode,
            String capabilityVersion,
            String providerKey,
            String adapterId,
            String providerCapabilityCode,
            String providerVersion,
            ProviderFeatures features) {
        this.capabilityCode = capabilityCode;
        this.capabilityVersion = capabilityVersion;
        this.providerKey = providerKey;
        this.adapterId = adapterId;
        this.providerCapabilityCode = providerCapabilityCode;
        this.providerVersion = providerVersion;
        this.features = features;
    }

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public String getCapabilityVersion() {
        return capabilityVersion;
    }

    public String getProviderKey() {
        return providerKey;
    }

    public String getAdapterId() {
        return adapterId;
    }

    public String getProviderCapabilityCode() {
        return providerCapabilityCode;
    }

    public String getProviderVersion() {
        return providerVersion;
    }

    public ProviderFeatures getFeatures() {
        return features;
    }
}
