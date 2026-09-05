package org.jeecg.modules.ai.provider.adapter;

import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.capability.domain.CapabilitySnapshot;
import org.jeecg.modules.ai.image.domain.DetectionParameters;
import org.jeecg.modules.ai.image.domain.ProviderRequest;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.stream.domain.ProviderStreamStartRequest;
import org.jeecg.modules.ai.stream.domain.StreamParameters;
import org.jeecg.modules.ai.video.domain.VideoParameters;
import org.jeecg.modules.ai.video.domain.VideoProviderRequest;

import java.math.BigDecimal;

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
                || !safeFileName(metadata.getFileName())
                || (metadata.getSizeBytes() != null && (metadata.getSizeBytes() <= 0 || metadata.getSizeBytes() > maxBytes))) {
            throw invalid();
        }
    }

    public static void validateVideo(VideoProviderRequest request, long maxBytes) throws ProviderException {
        if (request == null || !identifier(request.getRequestId(), 80)
                || request.getInput() == null || request.getInputMetadata() == null
                || request.getCapability() == null || request.getParameters() == null) {
            throw invalid();
        }
        VideoParameters p = request.getParameters();
        ContentMetadata metadata = request.getInputMetadata();
        if (p.getThreshold() == null || p.getThreshold().compareTo(BigDecimal.ZERO) < 0
                || p.getThreshold().compareTo(BigDecimal.ONE) > 0
                || p.getSampleIntervalMillis() < 100 || p.getSampleIntervalMillis() > 60000
                || p.getMaxEvents() < 1 || p.getMaxEvents() > 1000
                || !"video/mp4".equals(metadata.getMediaType()) || !safeFileName(metadata.getFileName())
                || (metadata.getSizeBytes() != null
                && (metadata.getSizeBytes() <= 0 || metadata.getSizeBytes() > maxBytes))) {
            throw invalid();
        }
    }

    public static void validateStreamStart(ProviderStreamStartRequest request) throws ProviderException {
        if (request == null || !identifier(request.getSessionId(), 80)
                || request.getCapability() == null || request.getParameters() == null
                || !identifier(request.getProviderSourceRef(), 160)) {
            throw invalid();
        }
        StreamParameters p = request.getParameters();
        if (p.getMaxEventsPerPoll() < 1 || p.getMaxEventsPerPoll() > 200
                || p.getPollIntervalMillis() < 250 || p.getPollIntervalMillis() > 30000) {
            throw invalid();
        }
    }

    public static void validateProviderSessionId(String value) throws ProviderException {
        if (!identifier(value, 160)) throw invalid();
    }

    public static void validateCursor(String cursor, int limit) throws ProviderException {
        if ((cursor != null && (cursor.isEmpty() || cursor.length() > 512
                || cursor.indexOf('\r') >= 0 || cursor.indexOf('\n') >= 0))
                || limit < 1 || limit > 200) {
            throw invalid();
        }
    }

    public static boolean binding(CapabilitySnapshot snapshot, String key, String adapter) {
        return binding(snapshot, key, adapter, "image-detection.v1")
                && snapshot.getFeatures() != null && !snapshot.getFeatures().isQuery()
                && !snapshot.getFeatures().isCancel() && !snapshot.getFeatures().isDeduplication();
    }

    public static boolean binding(
            CapabilitySnapshot snapshot,
            String key,
            String adapter,
            String capabilityCode) {
        return snapshot != null && key.equals(snapshot.getProviderKey()) && adapter.equals(snapshot.getAdapterId())
                && capabilityCode.equals(snapshot.getCapabilityCode())
                && capabilityCode.equals(snapshot.getProviderCapabilityCode())
                && snapshot.getFeatures() != null;
    }

    public static ProviderException unavailable(String message) {
        return new ProviderException(ErrorCode.CAPABILITY_UNAVAILABLE, ExecutionCertainty.NOT_STARTED, message);
    }

    private static ProviderException invalid() {
        return new ProviderException(ErrorCode.INVALID_REQUEST, ExecutionCertainty.NOT_STARTED, "Invalid provider input");
    }

    private static boolean identifier(String value, int max) {
        return value != null && value.length() <= max && value.matches("[A-Za-z0-9_-]+");
    }

    private static boolean safeFileName(String value) {
        return value != null && !value.isEmpty() && value.length() <= 255
                && !value.matches(".*[\\\\/\\r\\n].*");
    }
}
