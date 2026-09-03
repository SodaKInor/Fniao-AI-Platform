package org.jeecg.modules.ai.config.provider;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.ByteArrayOutputStream;
import org.jeecg.modules.ai.client.TransferInputStream;

final class ProviderCredentials {
    private ProviderCredentials() { }

    static String read(String path) {
        try (InputStream input = new TransferInputStream(Files.newInputStream(Paths.get(path)), 4096, null)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int n;
            while ((n = input.read(buffer)) >= 0) output.write(buffer, 0, n);
            String token = new String(output.toByteArray(), StandardCharsets.US_ASCII).trim();
            if (!token.matches("[!-~]+")) throw new IllegalArgumentException();
            return token;
        } catch (Exception error) {
            // Never attach the original exception, path, header or file contents.
            throw new IllegalStateException("Provider credential is unavailable");
        }
    }
}
