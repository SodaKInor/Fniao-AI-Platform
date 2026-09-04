package org.jeecg.modules.ai.client.draft;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.jeecg.modules.ai.client.TransferInputStream;
import org.jeecg.modules.ai.domain.ContentMetadata;
import org.jeecg.modules.ai.domain.ContentSource;

/** One-shot streaming bodies prevent OkHttp or callers from replaying a provider POST. */
final class DraftRequestBodies {
    private DraftRequestBodies() { }

    static RequestBody input(ContentSource source, ContentMetadata metadata, long maxBytes) {
        return new RequestBody() {
            private final AtomicBoolean opened = new AtomicBoolean();
            @Override public MediaType contentType() { return MediaType.get(metadata.getMediaType()); }
            @Override public boolean isOneShot() { return true; }
            @Override public void writeTo(BufferedSink sink) throws IOException {
                if (!opened.compareAndSet(false, true)) throw new IOException("Provider POST replay refused");
                try (InputStream input = new TransferInputStream(
                        source.openStream(), maxBytes, metadata.getSizeBytes())) {
                    byte[] bytes = new byte[8192];
                    int read;
                    while ((read = input.read(bytes)) != -1) sink.write(bytes, 0, read);
                }
            }
        };
    }

    static RequestBody oneShot(MultipartBody multipart, int transferMillis) {
        return new RequestBody() {
            @Override public MediaType contentType() { return multipart.contentType(); }
            @Override public boolean isOneShot() { return true; }
            @Override public void writeTo(BufferedSink sink) throws IOException {
                boolean prior = sink.timeout().hasDeadline();
                long previous = prior ? sink.timeout().deadlineNanoTime() : Long.MAX_VALUE;
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(transferMillis);
                sink.timeout().deadlineNanoTime(Math.min(previous, deadline));
                try { multipart.writeTo(sink); }
                finally {
                    if (prior) sink.timeout().deadlineNanoTime(previous);
                    else sink.timeout().clearDeadline();
                }
            }
        };
    }

    static RequestBody json(String value) {
        return new RequestBody() {
            private final AtomicBoolean written = new AtomicBoolean();
            @Override public MediaType contentType() { return MediaType.get("application/json"); }
            @Override public boolean isOneShot() { return true; }
            @Override public void writeTo(BufferedSink sink) throws IOException {
                if (!written.compareAndSet(false, true)) throw new IOException("Provider POST replay refused");
                sink.writeUtf8(value);
            }
        };
    }

    static RequestBody empty() {
        return new RequestBody() {
            private final AtomicBoolean written = new AtomicBoolean();
            @Override public MediaType contentType() { return null; }
            @Override public boolean isOneShot() { return true; }
            @Override public long contentLength() { return 0; }
            @Override public void writeTo(BufferedSink sink) throws IOException {
                if (!written.compareAndSet(false, true)) throw new IOException("Provider POST replay refused");
            }
        };
    }
}
