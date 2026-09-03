package org.jeecg.modules.ai.persistence.converter;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.*;
import org.jeecg.modules.ai.domain.*;

/** Persistence JSON is private and explicitly reconstructed; it is not an API DTO. */
public final class SnapshotCodec {
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    public String write(Object value) {
        if (value == null) return null;
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot encode AI snapshot", e); }
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception e) { throw new IllegalStateException("Invalid AI snapshot", e); }
    }

    private String text(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    private Instant instant(JsonNode n, String key) {
        String v = text(n, key);
        return v == null ? null : Instant.parse(v);
    }

    public JobRequest request(String value) {
        JsonNode n = read(value);
        return new JobRequest(text(n,"requestId"), text(n,"ownerId"), text(n,"idempotencyKey"),
                text(n,"requestDigest"), text(n,"inputAssetId"), parameters(n.get("parameters")),
                snapshot(n.get("capability")), text(n,"retryOfRequestId"), n.path("simulated").asBoolean(),
                instant(n,"createdAt"));
    }

    private DetectionParameters parameters(JsonNode n) {
        return new DetectionParameters(n.get("threshold").decimalValue(), n.get("maxDetections").asInt(),
                n.get("annotate").asBoolean());
    }

    private CapabilitySnapshot snapshot(JsonNode n) {
        JsonNode f = n.get("features");
        ProviderFeatures features = new ProviderFeatures(f.path("query").asBoolean(),
                f.path("cancel").asBoolean(), f.path("deduplication").asBoolean());
        return new CapabilitySnapshot(text(n,"capabilityCode"), text(n,"capabilityVersion"),
                text(n,"providerKey"), text(n,"adapterId"), text(n,"providerCapabilityCode"),
                text(n,"providerVersion"), features);
    }

    public Capability capability(String value) {
        JsonNode n = read(value);
        List<String> types = new ArrayList<>();
        n.path("inputMediaTypes").forEach(t -> types.add(t.asText()));
        return new Capability(snapshot(n.get("snapshot")),text(n,"displayName"),n.path("enabled").asBoolean(),
                n.path("available").asBoolean(),n.path("simulated").asBoolean(),text(n,"unavailableReason"),
                types,n.path("maxInputBytes").asLong(),n.path("maxOutputBytes").asLong(),n.path("maxWaitMillis").asLong());
    }

    private DetectionData data(JsonNode n) {
        List<Detection> detections = new ArrayList<>();
        for (JsonNode d : n.path("detections")) {
            JsonNode b = d.get("box");
            detections.add(new Detection(text(d,"label"), d.path("score").asDouble(),
                    new BoundingBox(b.path("x").asDouble(), b.path("y").asDouble(),
                            b.path("width").asDouble(), b.path("height").asDouble())));
        }
        return new DetectionData(text(n,"schemaVersion"),n.path("imageWidth").asInt(),n.path("imageHeight").asInt(),detections);
    }

    public ProviderResult checkpoint(String value) {
        if (value == null) return null;
        JsonNode n = read(value);
        List<ProviderArtifact> artifacts = new ArrayList<>();
        for (JsonNode a : n.path("artifacts")) {
            JsonNode m = a.get("metadata");
            Long size = m.path("sizeBytes").isNumber() ? m.get("sizeBytes").asLong() : null;
            artifacts.add(new ProviderArtifact(text(a,"reference"),
                    new ContentMetadata(text(m,"fileName"),text(m,"mediaType"),size,text(m,"sha256")),
                    instant(a,"expiresAt")));
        }
        return new ProviderResult(text(n,"providerRequestId"),n.path("simulated").asBoolean(),data(n.get("data")),artifacts);
    }

    public InferenceResult result(String value) {
        if (value == null) return null;
        JsonNode n = read(value);
        List<String> ids = new ArrayList<>();
        n.path("artifactIds").forEach(a -> ids.add(a.asText()));
        return new InferenceResult(n.path("simulated").asBoolean(), data(n.get("data")), ids);
    }

    public JobError error(String value) {
        if (value == null) return null;
        JsonNode n = read(value);
        return new JobError(ErrorCode.valueOf(text(n,"code")), text(n,"message"), n.path("simulated").asBoolean());
    }
}
