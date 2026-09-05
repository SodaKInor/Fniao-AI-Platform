package org.jeecg.modules.ai.provider.adapter.draft;

import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.image.domain.DetectionData;
import org.jeecg.modules.ai.image.domain.ProviderRequest;
import org.jeecg.modules.ai.image.domain.ProviderResult;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.result.domain.ProviderArtifact;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jeecg.modules.ai.provider.adapter.TransferInputStream;

public final class DraftResponseDecoder {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final long outputLimit;

    public DraftResponseDecoder(long outputLimit) { this.outputLimit = outputLimit; }

    public ProviderResult decode(InputStream input, ProviderRequest request) throws ProviderException, IOException {
        try {
            JsonNode root = mapper.readTree(new TransferInputStream(input, 1048576, null));
            DraftFields.object(root, "contract_version", "request_id", "status", "simulated", "data", "artifacts");
            DraftFields.require("0.1-draft".equals(DraftFields.text(root, "contract_version", 30)));
            DraftFields.require(request.getRequestId().equals(DraftFields.text(root, "request_id", 80)));
            DraftFields.require("succeeded".equals(DraftFields.text(root, "status", 30)));
            boolean simulated = DraftFields.bool(root, "simulated");
            DetectionData data = new DraftDetectionDecoder().decode(root.get("data"), request.getParameters().getMaxDetections());
            JsonNode items = root.get("artifacts");
            DraftFields.require(items.isArray() && items.size() <= 1);
            DraftFields.require(request.getParameters().isAnnotate() || items.size() == 0);
            List<ProviderArtifact> artifacts = new ArrayList<>();
            for (JsonNode item : items) artifacts.add(artifact(item));
            return new ProviderResult(request.getRequestId(), simulated, data, artifacts);
        } catch (JsonProcessingException | RuntimeException error) {
            throw new ProviderException(ErrorCode.PROVIDER_PROTOCOL, ExecutionCertainty.UNKNOWN, "Provider response violates the draft contract");
        }
    }

    private ProviderArtifact artifact(JsonNode node) {
        DraftFields.object(node, "reference", "file_name", "media_type", "size_bytes", "sha256", "expires_at");
        String reference = DraftFields.text(node, "reference", 300);
        DraftFields.require(reference.matches("/artifacts/[A-Za-z0-9_-]+"));
        String file = DraftFields.text(node, "file_name", 255);
        DraftFields.require(!file.matches(".*[\\\\/\\r\\n].*"));
        String media = DraftFields.text(node, "media_type", 50);
        DraftFields.require("image/png".equals(media) || "image/jpeg".equals(media));
        long bytes = DraftFields.integer(node, "size_bytes", 1, outputLimit);
        String hash = DraftFields.text(node, "sha256", 64);
        DraftFields.require(hash.matches("[a-f0-9]{64}"));
        Instant expires = Instant.parse(DraftFields.text(node, "expires_at", 50));
        return new ProviderArtifact(reference, new ContentMetadata(file, media, bytes, hash), expires);
    }
}
