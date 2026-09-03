package org.jeecg.modules.ai.client.draft;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.jeecg.modules.ai.domain.BoundingBox;
import org.jeecg.modules.ai.domain.Detection;
import org.jeecg.modules.ai.domain.DetectionData;

final class DraftDetectionDecoder {
    DetectionData decode(JsonNode data, int maxDetections) {
        DraftFields.object(data, "schema_version", "image_width", "image_height", "detections");
        DraftFields.require("detection.v1".equals(DraftFields.text(data, "schema_version", 30)));
        int width = (int) DraftFields.integer(data, "image_width", 1, 4096);
        int height = (int) DraftFields.integer(data, "image_height", 1, 4096);
        JsonNode items = data.get("detections");
        DraftFields.require(items.isArray() && items.size() <= maxDetections);
        List<Detection> detections = new ArrayList<>();
        for (JsonNode item : items) detections.add(detection(item));
        return new DetectionData("detection.v1", width, height, detections);
    }

    private Detection detection(JsonNode node) {
        DraftFields.object(node, "label", "score", "box");
        String label = DraftFields.text(node, "label", 120);
        double score = DraftFields.unit(node, "score");
        JsonNode box = node.get("box");
        DraftFields.object(box, "x", "y", "width", "height");
        double x = DraftFields.unit(box, "x");
        double y = DraftFields.unit(box, "y");
        double width = DraftFields.unit(box, "width");
        double height = DraftFields.unit(box, "height");
        DraftFields.require(box.get("x").decimalValue().add(box.get("width").decimalValue())
                .compareTo(java.math.BigDecimal.ONE) <= 0);
        DraftFields.require(box.get("y").decimalValue().add(box.get("height").decimalValue())
                .compareTo(java.math.BigDecimal.ONE) <= 0);
        return new Detection(label, score, new BoundingBox(x, y, width, height));
    }
}
