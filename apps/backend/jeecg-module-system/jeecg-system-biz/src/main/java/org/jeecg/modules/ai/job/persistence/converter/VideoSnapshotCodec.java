package org.jeecg.modules.ai.job.persistence.converter;

import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.result.domain.ProviderArtifact;
import org.jeecg.modules.ai.video.domain.ProviderVideoEvent;
import org.jeecg.modules.ai.video.domain.VideoEvent;
import org.jeecg.modules.ai.video.domain.VideoProviderResult;
import org.jeecg.modules.ai.video.domain.VideoResult;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.*;

public final class VideoSnapshotCodec {
    private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    public String write(Object value) {
        if (value==null) return null;
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot encode video snapshot",e); }
    }

    public VideoProviderResult checkpoint(String value) {
        if (value==null) return null;
        JsonNode n=read(value); List<ProviderVideoEvent> events=new ArrayList<>();
        for (JsonNode e:n.path("events")) events.add(new ProviderVideoEvent(text(e,"providerEventId"),
                e.path("offsetMillis").asLong(),text(e,"eventType"),e.get("score").decimalValue(),
                artifact(e.get("snapshot"))));
        return new VideoProviderResult(text(n,"providerRequestId"),n.path("simulated").asBoolean(),events,
                artifact(n.get("annotatedVideo")));
    }

    public VideoResult result(String value) {
        if (value==null) return null;
        JsonNode n=read(value); List<VideoEvent> events=new ArrayList<>();
        for (JsonNode e:n.path("events")) events.add(new VideoEvent(text(e,"eventId"),
                e.path("offsetMillis").asLong(),text(e,"eventType"),e.get("score").decimalValue(),
                text(e,"snapshotAssetId")));
        List<String> snapshots=new ArrayList<>();
        n.path("snapshotAssetIds").forEach(id -> snapshots.add(id.asText()));
        return new VideoResult(n.path("simulated").asBoolean(),events,snapshots,text(n,"annotatedVideoAssetId"));
    }

    private ProviderArtifact artifact(JsonNode n) {
        if (n==null || n.isNull()) return null;
        JsonNode m=n.get("metadata"); Long size=m.path("sizeBytes").isNumber() ? m.get("sizeBytes").asLong() : null;
        return new ProviderArtifact(text(n,"reference"),new ContentMetadata(text(m,"fileName"),
                text(m,"mediaType"),size,text(m,"sha256")),instant(n,"expiresAt"));
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception e) { throw new IllegalStateException("Invalid video snapshot",e); }
    }
    private String text(JsonNode n,String key) {
        JsonNode value=n==null ? null : n.get(key); return value==null || value.isNull() ? null : value.asText();
    }
    private Instant instant(JsonNode n,String key) {
        String value=text(n,key); return value==null ? null : Instant.parse(value);
    }
}
