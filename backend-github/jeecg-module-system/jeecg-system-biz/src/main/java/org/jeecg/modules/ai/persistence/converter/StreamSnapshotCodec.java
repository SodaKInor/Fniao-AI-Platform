package org.jeecg.modules.ai.persistence.converter;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.jeecg.modules.ai.domain.*;

public final class StreamSnapshotCodec {
    private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot encode stream snapshot",e); }
    }

    public StreamSessionRequest request(String value) {
        JsonNode n=read(value); JsonNode p=n.get("parameters"); JsonNode f=n.get("providerFeatures");
        return new StreamSessionRequest(text(n,"sessionId"),text(n,"ownerId"),text(n,"idempotencyKey"),
                text(n,"requestDigest"),text(n,"streamSourceId"),snapshot(n.get("capability")),
                new StreamProviderFeatures(f.path("sessionQuery").asBoolean(),f.path("eventQuery").asBoolean(),
                        f.path("stop").asBoolean(),f.path("deduplication").asBoolean()),
                new StreamParameters(p.get("maxEventsPerPoll").asInt(),p.get("pollIntervalMillis").asLong(),
                        p.get("includeSnapshots").asBoolean()),
                Instant.parse(text(n,"createdAt")));
    }

    private CapabilitySnapshot snapshot(JsonNode n) {
        JsonNode f=n.get("features");
        return new CapabilitySnapshot(text(n,"capabilityCode"),text(n,"capabilityVersion"),
                text(n,"providerKey"),text(n,"adapterId"),text(n,"providerCapabilityCode"),
                text(n,"providerVersion"),new ProviderFeatures(f.path("query").asBoolean(),
                        f.path("cancel").asBoolean(),f.path("deduplication").asBoolean()));
    }
    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception e) { throw new IllegalStateException("Invalid stream snapshot",e); }
    }
    private String text(JsonNode n,String key) {
        JsonNode value=n==null ? null : n.get(key); return value==null || value.isNull() ? null : value.asText();
    }
}
