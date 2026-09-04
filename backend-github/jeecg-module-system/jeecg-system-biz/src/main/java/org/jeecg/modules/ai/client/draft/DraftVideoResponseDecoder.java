package org.jeecg.modules.ai.client.draft;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jeecg.modules.ai.client.TransferInputStream;
import org.jeecg.modules.ai.domain.ErrorCode;
import org.jeecg.modules.ai.domain.ExecutionCertainty;
import org.jeecg.modules.ai.domain.ProviderArtifact;
import org.jeecg.modules.ai.domain.ProviderException;
import org.jeecg.modules.ai.domain.ProviderVideoEvent;
import org.jeecg.modules.ai.domain.VideoProviderRequest;
import org.jeecg.modules.ai.domain.VideoProviderResult;

/** Converts only the bounded v0.2 video response; unknown fields are protocol failures. */
public final class DraftVideoResponseDecoder {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final DraftArtifactDecoder artifacts;

    public DraftVideoResponseDecoder(long imageLimit, long videoLimit) {
        this.artifacts = new DraftArtifactDecoder(imageLimit, videoLimit);
    }

    public VideoProviderResult decode(InputStream input, VideoProviderRequest request)
            throws ProviderException, IOException {
        try {
            JsonNode root = mapper.readTree(new TransferInputStream(input, 4 * 1024 * 1024, null));
            DraftFields.object(root, new String[]{"simulated", "events"},
                    "provider_request_id", "provider_version", "annotated_video");
            boolean simulated = DraftFields.bool(root, "simulated");
            String providerRequestId = DraftFields.nullableText(root, "provider_request_id", 160);
            DraftFields.nullableText(root, "provider_version", 120);
            JsonNode items = root.get("events");
            DraftFields.require(items.isArray() && items.size() <= request.getParameters().getMaxEvents());
            List<ProviderVideoEvent> events = events(items, request);
            ProviderArtifact annotated = null;
            if (root.has("annotated_video")) {
                DraftFields.require(request.getParameters().isAnnotate());
                annotated = artifacts.video(root.get("annotated_video"));
            }
            return new VideoProviderResult(providerRequestId, simulated, events, annotated);
        } catch (JsonProcessingException | RuntimeException error) {
            throw protocol();
        }
    }

    private List<ProviderVideoEvent> events(JsonNode items, VideoProviderRequest request) {
        List<ProviderVideoEvent> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        long previousOffset = -1;
        for (JsonNode item : items) {
            DraftFields.object(item, new String[]{"event_id", "offset_ms", "event_type"}, "score", "snapshot");
            String id = DraftFields.text(item, "event_id", 120);
            long offset = DraftFields.integer(item, "offset_ms", 0, Long.MAX_VALUE);
            DraftFields.require(ids.add(id) && offset >= previousOffset);
            previousOffset = offset;
            ProviderArtifact snapshot = null;
            if (item.has("snapshot")) {
                DraftFields.require(request.getParameters().isIncludeSnapshots());
                snapshot = artifacts.image(item.get("snapshot"));
            }
            result.add(new ProviderVideoEvent(id, offset, DraftFields.text(item, "event_type", 120),
                    DraftFields.nullableUnit(item, "score"), snapshot));
        }
        return result;
    }

    private ProviderException protocol() {
        return new ProviderException(ErrorCode.PROVIDER_PROTOCOL, ExecutionCertainty.UNKNOWN,
                "Provider video response violates the draft contract");
    }
}
