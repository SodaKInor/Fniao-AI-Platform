package org.jeecg.modules.ai.client.draft;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jeecg.modules.ai.client.TransferInputStream;
import org.jeecg.modules.ai.domain.ProviderArtifact;
import org.jeecg.modules.ai.domain.ProviderException;
import org.jeecg.modules.ai.domain.ProviderStreamEvent;
import org.jeecg.modules.ai.domain.ProviderStreamEventPage;
import org.jeecg.modules.ai.domain.ProviderStreamSession;
import org.jeecg.modules.ai.domain.StreamSessionState;
import org.jeecg.modules.ai.domain.StreamStopOutcome;
import org.jeecg.modules.ai.domain.StreamStopResult;

/** Strict v0.2 stream response decoder with bounded events and no vendor extension map. */
public final class DraftStreamResponseDecoder {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final DraftArtifactDecoder artifacts;

    public DraftStreamResponseDecoder(long imageLimit, long videoLimit) {
        this.artifacts = new DraftArtifactDecoder(imageLimit, videoLimit);
    }

    public ProviderStreamSession session(InputStream input, String expectedId)
            throws ProviderException, IOException {
        try {
            JsonNode root = read(input);
            DraftFields.object(root, new String[]{"provider_session_id", "state"}, "cursor", "provider_version");
            String id = DraftFields.text(root, "provider_session_id", 160);
            DraftFields.require(expectedId == null || expectedId.equals(id));
            StreamSessionState state = StreamSessionState.valueOf(DraftFields.text(root, "state", 20));
            DraftFields.require(state == StreamSessionState.STARTING || state == StreamSessionState.RUNNING
                    || state == StreamSessionState.STOPPED || state == StreamSessionState.FAILED);
            return new ProviderStreamSession(id, state,
                    DraftFields.nullableText(root, "cursor", 512),
                    DraftFields.nullableText(root, "provider_version", 120));
        } catch (JsonProcessingException | RuntimeException error) {
            throw DraftHttpErrors.protocol("stream session");
        }
    }

    public ProviderStreamEventPage events(InputStream input, int limit)
            throws ProviderException, IOException {
        try {
            JsonNode root = read(input);
            DraftFields.object(root, new String[]{"items"}, "next_cursor");
            JsonNode items = root.get("items");
            DraftFields.require(items.isArray() && items.size() <= limit);
            List<ProviderStreamEvent> result = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            long previousOffset = -1;
            Instant previousTime = null;
            for (JsonNode item : items) {
                DraftFields.object(item, new String[]{"event_id", "offset_ms", "occurred_at", "event_type"},
                        "score", "snapshot");
                String id = DraftFields.text(item, "event_id", 120);
                long offset = DraftFields.integer(item, "offset_ms", 0, Long.MAX_VALUE);
                Instant occurredAt = Instant.parse(DraftFields.text(item, "occurred_at", 50));
                DraftFields.require(ids.add(id) && offset >= previousOffset
                        && (previousTime == null || !occurredAt.isBefore(previousTime)));
                previousOffset = offset;
                previousTime = occurredAt;
                ProviderArtifact snapshot = item.has("snapshot") ? artifacts.image(item.get("snapshot")) : null;
                result.add(new ProviderStreamEvent(id, offset, occurredAt,
                        DraftFields.text(item, "event_type", 120),
                        DraftFields.nullableUnit(item, "score"), snapshot));
            }
            return new ProviderStreamEventPage(result, DraftFields.nullableText(root, "next_cursor", 512));
        } catch (JsonProcessingException | RuntimeException error) {
            throw DraftHttpErrors.protocol("stream events");
        }
    }

    public StreamStopResult stop(InputStream input, String expectedId)
            throws ProviderException, IOException {
        try {
            JsonNode root = read(input);
            DraftFields.object(root, "provider_session_id", "confirmed", "state");
            String id = DraftFields.text(root, "provider_session_id", 160);
            DraftFields.require(expectedId.equals(id) && DraftFields.bool(root, "confirmed")
                    && "STOPPED".equals(DraftFields.text(root, "state", 20)));
            return new StreamStopResult(id, StreamStopOutcome.CONFIRMED_STOPPED);
        } catch (JsonProcessingException | RuntimeException error) {
            throw DraftHttpErrors.protocol("stream stop");
        }
    }

    private JsonNode read(InputStream input) throws IOException {
        return mapper.readTree(new TransferInputStream(input, 4 * 1024 * 1024, null));
    }
}
