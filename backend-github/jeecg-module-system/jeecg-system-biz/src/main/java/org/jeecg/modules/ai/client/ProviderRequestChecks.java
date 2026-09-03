package org.jeecg.modules.ai.client;

import java.math.BigDecimal;
import org.jeecg.modules.ai.domain.*;

public final class ProviderRequestChecks {
    private ProviderRequestChecks() { }

    public static void validate(ProviderRequest request, long maxBytes) throws ProviderException {
        if (request == null || request.getRequestId() == null
                || !request.getRequestId().matches("[A-Za-z0-9_-]{1,80}")
                || request.getInput() == null || request.getInputMetadata() == null
                || request.getCapability() == null || request.getParameters() == null) {
            throw invalid();
        }
        DetectionParameters p = request.getParameters();
        ContentMetadata metadata = request.getInputMetadata();
        if (p.getThreshold() == null || p.getThreshold().compareTo(BigDecimal.ZERO) < 0
                || p.getThreshold().compareTo(BigDecimal.ONE) > 0
                || p.getMaxDetections() < 1 || p.getMaxDetections() > 100
                || !("image/png".equals(metadata.getMediaType()) || "image/jpeg".equals(metadata.getMediaType()))
                || (metadata.getSizeBytes() != null && (metadata.getSizeBytes() <= 0 || metadata.getSizeBytes() > maxBytes))) {
            throw invalid();
        }
    }

    public static boolean binding(CapabilitySnapshot snapshot, String key, String adapter) {
        return snapshot != null && key.equals(snapshot.getProviderKey()) && adapter.equals(snapshot.getAdapterId())
                && "image-detection.v1".equals(snapshot.getCapabilityCode())
                && "image-detection.v1".equals(snapshot.getProviderCapabilityCode())
                && snapshot.getFeatures() != null && !snapshot.getFeatures().isQuery()
                && !snapshot.getFeatures().isCancel() && !snapshot.getFeatures().isDeduplication();
    }

    public static ProviderException unavailable(String message) {
        return new ProviderException(ErrorCode.CAPABILITY_UNAVAILABLE, ExecutionCertainty.NOT_STARTED, message);
    }

    private static ProviderException invalid() {
        return new ProviderException(ErrorCode.INVALID_REQUEST, ExecutionCertainty.NOT_STARTED, "Invalid provider input");
    }
}
