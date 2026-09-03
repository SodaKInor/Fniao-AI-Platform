package org.jeecg.modules.ai.client.draft;

import okhttp3.HttpUrl;

/** Only the configured exact origin is approved. Fixture HTTP is limited to literal loopback. */
public final class DraftEndpoint {
    private final HttpUrl origin;
    private final String inferPath;

    public DraftEndpoint(String baseUrl, String approvedOrigin, String inferPath, boolean fixtureHttp) {
        HttpUrl base = HttpUrl.parse(baseUrl);
        HttpUrl approved = HttpUrl.parse(approvedOrigin);
        if (base == null || approved == null || !base.equals(approved)
                || !"/".equals(base.encodedPath()) || base.query() != null || base.fragment() != null
                || !base.username().isEmpty() || !base.password().isEmpty()
                || !(base.isHttps() || (fixtureHttp && ("127.0.0.1".equals(base.host()) || "::1".equals(base.host()))))
                || inferPath == null || !inferPath.matches("/[A-Za-z0-9_/-]+") || inferPath.contains("//")) {
            throw new IllegalArgumentException("Unapproved provider endpoint");
        }
        this.origin = base;
        this.inferPath = inferPath;
    }

    HttpUrl inference() { return origin.resolve(inferPath); }

    HttpUrl artifact(String reference) {
        if (reference == null || !reference.matches("/artifacts/[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid artifact reference");
        }
        return origin.resolve(reference);
    }
}
