package org.jeecg.modules.ai.client.mock;

import java.io.InputStream;
import java.time.Clock;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.jeecg.modules.ai.client.ProviderRequestChecks;
import org.jeecg.modules.ai.client.TransferInputStream;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.InferenceProvider;

/** Explicit development simulation, with no algorithm/native library or external service. */
public final class MockInferenceProvider implements InferenceProvider {
    private final long inputLimit;
    private final long outputLimit;
    private final Clock clock;
    private final Semaphore permits;

    public MockInferenceProvider(long inputLimit, long outputLimit, int maxInflight, Clock clock) {
        this.inputLimit = inputLimit; this.outputLimit = outputLimit; this.clock = clock;
        this.permits = new Semaphore(maxInflight);
    }

    @Override public ProviderResult infer(ProviderRequest request) throws ProviderException {
        ProviderRequestChecks.validate(request, inputLimit);
        if (!ProviderRequestChecks.binding(request.getCapability(), "mock", "mock-v1")) {
            throw ProviderRequestChecks.unavailable("Unknown simulated provider binding");
        }
        if (!permits.tryAcquire()) {
            throw new ProviderException(ErrorCode.LIMIT_EXCEEDED, ExecutionCertainty.NOT_STARTED, "Simulated provider concurrency limit reached");
        }
        try {
            int[] dimensions = dimensions(request);
            boolean detected = request.getParameters().getThreshold().doubleValue() <= 0.95;
            List<Detection> detections = detected ? Collections.singletonList(
                    new Detection("simulated-square", 0.95, new BoundingBox(0.25, 0.25, 0.5, 0.5))) : Collections.emptyList();
            ProviderArtifact artifact = MockArtifactReader.descriptor(clock);
            List<ProviderArtifact> artifacts = detected && request.getParameters().isAnnotate()
                    ? Collections.singletonList(artifact) : Collections.emptyList();
            if (!artifacts.isEmpty() && artifact.getMetadata().getSizeBytes() > outputLimit) {
                throw new IllegalArgumentException("Output exceeds limit");
            }
            return new ProviderResult(request.getRequestId(), true,
                    new DetectionData("detection.v1", dimensions[0], dimensions[1], detections), artifacts);
        } catch (Exception error) {
            throw new ProviderException(ErrorCode.INVALID_REQUEST, ExecutionCertainty.NOT_STARTED, "Invalid simulated image input or output limit");
        } finally { permits.release(); }
    }

    private int[] dimensions(ProviderRequest request) throws Exception {
        try (InputStream input = new TransferInputStream(request.getInput().openStream(), inputLimit, request.getInputMetadata().getSizeBytes());
                MemoryCacheImageInputStream image = new MemoryCacheImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) throw new IllegalArgumentException();
            ImageReader reader = readers.next();
            int width;
            int height;
            try {
                reader.setInput(image, true, true);
                String media = "image/" + reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!media.equals(request.getInputMetadata().getMediaType())) throw new IllegalArgumentException();
                width = reader.getWidth(0); height = reader.getHeight(0);
            } finally { reader.dispose(); }
            if (width < 1 || height < 1 || width > 4096 || height > 4096) throw new IllegalArgumentException();
            byte[] drain = new byte[8192];
            while (input.read(drain) != -1) { /* Enforce the actual byte budget without retaining pixels. */ }
            return new int[]{width, height};
        }
    }
}
