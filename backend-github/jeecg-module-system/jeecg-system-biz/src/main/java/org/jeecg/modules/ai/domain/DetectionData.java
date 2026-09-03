package org.jeecg.modules.ai.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * detection.v1 structured result. An empty detections list is valid, never a transport error.
 */
public final class DetectionData {
    private final String schemaVersion;
    private final int imageWidth;
    private final int imageHeight;
    private final List<Detection> detections;

    public DetectionData(
            String schemaVersion,
            int imageWidth,
            int imageHeight,
            List<Detection> detections) {
        this.schemaVersion = schemaVersion;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.detections = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(detections, "detections")));
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public List<Detection> getDetections() {
        return detections;
    }
}
