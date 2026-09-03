package org.jeecg.modules.ai.client.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.jeecg.modules.ai.client.TransferInputStream;
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
                .addFormDataPart("file", "input", new InputBody(request, maxBytes)).build();
        // The outer body must be one-shot too: 408/503 follow-ups otherwise replay multipart POSTs.
        return new RequestBody() {
            @Override public MediaType contentType() { return multipart.contentType(); }
            @Override public boolean isOneShot() { return true; }
            @Override public void writeTo(BufferedSink sink) throws IOException {
                boolean prior = sink.timeout().hasDeadline();
                long previous = prior ? sink.timeout().deadlineNanoTime() : Long.MAX_VALUE;
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(transferMillis);
                sink.timeout().deadlineNanoTime(Math.min(previous, deadline));
                try { multipart.writeTo(sink); }
                finally {
                    if (prior) sink.timeout().deadlineNanoTime(previous);
                    else sink.timeout().clearDeadline();
                }
            }
        };
    }

    private static final class InputBody extends RequestBody {
        private final ProviderRequest request;
        private final long maxBytes;
        private final AtomicBoolean opened = new AtomicBoolean();
        private InputBody(ProviderRequest request, long maxBytes) { this.request = request; this.maxBytes = maxBytes; }
        @Override public MediaType contentType() { return MediaType.get(request.getInputMetadata().getMediaType()); }
        @Override public boolean isOneShot() { return true; }
        @Override public void writeTo(BufferedSink sink) throws IOException {
            if (!opened.compareAndSet(false, true)) throw new IOException("Inference replay refused");
            try (InputStream input = new TransferInputStream(request.getInput().openStream(), maxBytes,
                    request.getInputMetadata().getSizeBytes())) {
                byte[] bytes = new byte[8192];
                int read;
                while ((read = input.read(bytes)) != -1) sink.write(bytes, 0, read);
            }
        }
    }
}
