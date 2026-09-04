package org.jeecg.modules.ai.client.draft;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.jeecg.modules.ai.domain.ContentMetadata;
import org.jeecg.modules.ai.domain.ProviderArtifact;

/** Strict v0.2 artifact conversion; references remain opaque until the matching reader opens them. */
final class DraftArtifactDecoder {
    private final long imageLimit;
    private final long videoLimit;

    DraftArtifactDecoder(long imageLimit, long videoLimit) {
        this.imageLimit = imageLimit;
        this.videoLimit = videoLimit;
    }

    ProviderArtifact image(JsonNode node) {
        return decode(node, new HashSet<>(Arrays.asList("image/png", "image/jpeg")), imageLimit);
    }

    ProviderArtifact video(JsonNode node) {
        return decode(node, new HashSet<>(Arrays.asList("video/mp4")), videoLimit);
    }

    private ProviderArtifact decode(JsonNode node, Set<String> mediaTypes, long maxBytes) {
        DraftFields.object(node, new String[]{"reference", "media_type"}, "size_bytes", "sha256");
        String reference = DraftFields.text(node, "reference", 500);
        DraftFields.require(reference.matches("/?artifacts/[A-Za-z0-9_.-]{1,460}"));
        String media = DraftFields.text(node, "media_type", 50);
        DraftFields.require(mediaTypes.contains(media));
        Long bytes = DraftFields.nullableInteger(node, "size_bytes", 1, maxBytes);
        String hash = DraftFields.nullableText(node, "sha256", 64);
        DraftFields.require(hash == null || hash.matches("[a-f0-9]{64}"));
        String fileName = reference.substring(reference.lastIndexOf('/') + 1);
        return new ProviderArtifact(reference, new ContentMetadata(fileName, media, bytes, hash), null);
    }
}
