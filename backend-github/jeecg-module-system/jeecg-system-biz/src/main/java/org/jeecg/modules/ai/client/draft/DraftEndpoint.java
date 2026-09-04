package org.jeecg.modules.ai.client.draft;

import okhttp3.HttpUrl;

/** Only the configured exact origin is approved. Fixture HTTP is limited to literal loopback. */
public final class DraftEndpoint {
    private final HttpUrl origin;
    private final String inferPath;
    private final String videoPath;
    private final String streamSourcesPath;
    private final String streamSessionsPath;

    public DraftEndpoint(String baseUrl, String approvedOrigin, String inferPath, boolean fixtureHttp) {
        this(baseUrl, approvedOrigin, inferPath, "/video-jobs", "/stream-sources", "/stream-sessions", fixtureHttp);
    }

    public DraftEndpoint(
            String baseUrl,
            String approvedOrigin,
            String inferPath,
            String videoPath,
            String streamSourcesPath,
            String streamSessionsPath,
            boolean fixtureHttp) {
        HttpUrl base = HttpUrl.parse(baseUrl);
        HttpUrl approved = HttpUrl.parse(approvedOrigin);
        if (base == null || approved == null || !base.equals(approved)
                || !"/".equals(base.encodedPath()) || base.query() != null || base.fragment() != null
                || !base.username().isEmpty() || !base.password().isEmpty()
                || !(base.isHttps() || (fixtureHttp && ("127.0.0.1".equals(base.host()) || "::1".equals(base.host()))))
                || !validPath(inferPath) || !validPath(videoPath)
                || !validPath(streamSourcesPath) || !validPath(streamSessionsPath)) {
            throw new IllegalArgumentException("Unapproved provider endpoint");
        }
        this.origin = base;
        this.inferPath = inferPath;
        this.videoPath = videoPath;
        this.streamSourcesPath = streamSourcesPath;
        this.streamSessionsPath = streamSessionsPath;
    }

    HttpUrl inference() { return origin.resolve(inferPath); }

    HttpUrl videoJob() { return origin.resolve(videoPath); }

    HttpUrl startStream(String sourceId) {
        return resource(streamSourcesPath, sourceId).newBuilder().addPathSegment("sessions").build();
    }

    HttpUrl streamSession(String providerSessionId) {
        return resource(streamSessionsPath, providerSessionId);
    }

    HttpUrl streamEvents(String providerSessionId, String cursor, int limit) {
        HttpUrl.Builder builder = streamSession(providerSessionId).newBuilder()
                .addPathSegment("events").addQueryParameter("limit", Integer.toString(limit));
        if (cursor != null) builder.addQueryParameter("cursor", cursor);
        return builder.build();
    }

    HttpUrl stopStream(String providerSessionId) {
        return streamSession(providerSessionId).newBuilder().addPathSegment("stop").build();
    }

    HttpUrl artifact(String reference) {
        if (reference == null || !reference.matches("/?artifacts/[A-Za-z0-9_.-]{1,460}")) {
            throw new IllegalArgumentException("Invalid artifact reference");
        }
        return origin.resolve(reference.startsWith("/") ? reference : "/" + reference);
    }

    private HttpUrl resource(String root, String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,160}")) {
            throw new IllegalArgumentException("Invalid provider resource id");
        }
        return origin.resolve(root).newBuilder().addPathSegment(id).build();
    }

    private static boolean validPath(String value) {
        return value != null && value.matches("/[A-Za-z0-9_/-]+")
                && !value.contains("//") && !value.endsWith("/");
    }
}
