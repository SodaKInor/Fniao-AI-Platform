package org.jeecg.modules.ai.provider.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;

final class ProviderTrust {
    private ProviderTrust() { }

    static void configure(OkHttpClient.Builder builder, String caFile) {
        if (caFile == null || caFile.isEmpty()) return; // JVM trust and normal hostname verification.
        try (InputStream input = Files.newInputStream(Paths.get(caFile))) {
            Collection<? extends Certificate> certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
            if (certificates.isEmpty()) throw new IllegalArgumentException();
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            int index = 0;
            for (Certificate certificate : certificates) store.setCertificateEntry("provider-ca-" + index++, certificate);
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            X509TrustManager trust = (X509TrustManager) factory.getTrustManagers()[0];
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new javax.net.ssl.TrustManager[]{trust}, null);
            builder.sslSocketFactory(ssl.getSocketFactory(), trust);
        } catch (Exception error) {
            throw new IllegalStateException("Provider CA is unavailable or invalid");
        }
    }
}
