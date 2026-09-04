package org.jeecg.modules.ai.client;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;

/** Network fixture only; never packaged as a production provider. */
public final class ProtocolFixture implements AutoCloseable {
    public volatile int status = 200;
    public volatile String contentType = "application/json";
    public volatile byte[] response = new byte[0];
    public volatile String requestBody;
    public volatile String authorization;
    public volatile String method;
    public volatile String requestUri;
    public volatile long delayHeaders;
    public volatile long delayInput;
    public volatile long delayBody;
    public volatile boolean disconnect;
    public volatile boolean truncated;
    public final AtomicInteger calls = new AtomicInteger();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final boolean tls;

    public ProtocolFixture(boolean tls) throws Exception {
        this.tls = tls;
        if (tls) {
            HttpsServer https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(Paths.get("/validation/server.p12"))) { store.load(in, "fixture-only".toCharArray()); }
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, "fixture-only".toCharArray());
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(keys.getKeyManagers(), null, null);
            https.setHttpsConfigurator(new HttpsConfigurator(ssl));
            server = https;
        } else server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() { return (tls ? "https" : "http") + "://127.0.0.1:" + server.getAddress().getPort(); }
    public void json(String text) { response = text.getBytes(StandardCharsets.UTF_8); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (delayInput > 0) Thread.sleep(delayInput);
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            method = exchange.getRequestMethod();
            requestUri = exchange.getRequestURI().toString();
            requestBody = new String(read(exchange.getRequestBody()), StandardCharsets.UTF_8);
            calls.incrementAndGet();
            if (disconnect) return;
            if (delayHeaders > 0) Thread.sleep(delayHeaders);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Location", url() + "/redirect-target");
            exchange.getResponseHeaders().set("Retry-After", "0");
            exchange.sendResponseHeaders(status, response.length + (truncated ? 20 : 0));
            if (delayBody > 0) Thread.sleep(delayBody);
            exchange.getResponseBody().write(response);
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        finally { exchange.close(); }
    }

    public static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) result.write(buffer, 0, n);
        return result.toByteArray();
    }

    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
