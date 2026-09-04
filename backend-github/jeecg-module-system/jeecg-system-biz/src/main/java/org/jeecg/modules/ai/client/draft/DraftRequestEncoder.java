package org.jeecg.modules.ai.client.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.jeecg.modules.ai.domain.ProviderRequest;

final class DraftRequestEncoder {
    RequestBody encode(ProviderRequest request, long maxBytes, int transferMillis) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("contract_version", "0.1-draft");
        metadata.put("request_id", request.getRequestId());
        metadata.put("capability", request.getCapability().getProviderCapabilityCode());
        ObjectNode parameters = metadata.putObject("parameters");
        parameters.put("threshold", request.getParameters().getThreshold());
        parameters.put("max_detections", request.getParameters().getMaxDetections());
        parameters.put("annotate", request.getParameters().isAnnotate());
        MultipartBody multipart = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("metadata", null, RequestBody.create(MediaType.get("application/json"), metadata.toString()))
                .addFormDataPart("file", request.getInputMetadata().getFileName(),
                        DraftRequestBodies.input(request.getInput(), request.getInputMetadata(), maxBytes))
                .build();
        return DraftRequestBodies.oneShot(multipart, transferMillis);
    }
}
