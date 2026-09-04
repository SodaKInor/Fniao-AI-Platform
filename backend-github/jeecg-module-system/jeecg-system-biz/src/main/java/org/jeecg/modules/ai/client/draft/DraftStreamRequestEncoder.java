package org.jeecg.modules.ai.client.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.RequestBody;
import org.jeecg.modules.ai.domain.ProviderStreamStartRequest;
import org.jeecg.modules.ai.domain.StreamParameters;

/** Exact JSON metadata for starting the v0.2 stream draft by registered source ID. */
final class DraftStreamRequestEncoder {
    RequestBody encode(ProviderStreamStartRequest request) {
        ObjectNode root = new ObjectMapper().createObjectNode();
        root.put("contract_version", "0.2-draft");
        root.put("request_id", request.getSessionId());
        root.put("capability", "video-stream-analysis.v1");
        StreamParameters value = request.getParameters();
        ObjectNode parameters = root.putObject("parameters");
        parameters.put("max_events_per_poll", value.getMaxEventsPerPoll());
        parameters.put("poll_interval_ms", value.getPollIntervalMillis());
        parameters.put("include_snapshots", value.isIncludeSnapshots());
        return DraftRequestBodies.json(root.toString());
    }
}
