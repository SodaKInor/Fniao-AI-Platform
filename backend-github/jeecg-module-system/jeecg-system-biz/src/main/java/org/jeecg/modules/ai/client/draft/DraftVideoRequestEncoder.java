package org.jeecg.modules.ai.client.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.jeecg.modules.ai.domain.VideoParameters;
import org.jeecg.modules.ai.domain.VideoProviderRequest;

/** Exact multipart metadata for the unconfirmed v0.2 upload-video draft. */
final class DraftVideoRequestEncoder {
    RequestBody encode(VideoProviderRequest request, long maxBytes, int transferMillis) {
        ObjectNode metadata = new ObjectMapper().createObjectNode();
        metadata.put("contract_version", "0.2-draft");
        metadata.put("request_id", request.getRequestId());
        metadata.put("capability", "video-file-analysis.v1");
        VideoParameters value = request.getParameters();
        ObjectNode parameters = metadata.putObject("parameters");
        parameters.put("threshold", value.getThreshold());
        parameters.put("sample_interval_ms", value.getSampleIntervalMillis());
        parameters.put("max_events", value.getMaxEvents());
        parameters.put("include_snapshots", value.isIncludeSnapshots());
        parameters.put("annotate", value.isAnnotate());
        MultipartBody multipart = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("metadata", null,
                        RequestBody.create(MediaType.get("application/json"), metadata.toString()))
                .addFormDataPart("file", request.getInputMetadata().getFileName(),
                        DraftRequestBodies.input(request.getInput(), request.getInputMetadata(), maxBytes))
                .build();
        return DraftRequestBodies.oneShot(multipart, transferMillis);
    }
}
