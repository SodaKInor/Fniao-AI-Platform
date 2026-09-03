package org.jeecg.modules.ai.client;

import java.io.*;
import java.time.*;
import org.jeecg.modules.ai.client.draft.*;
import org.jeecg.modules.ai.config.provider.*;
import org.jeecg.modules.ai.domain.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.jeecg.modules.ai.client.ClientTestInputs.*;

public class DraftArtifactTest {
    private ProviderArtifact artifact(String reference, long bytes, Instant expiry) {
        return new ProviderArtifact(reference, new ContentMetadata("result.png", "image/png", bytes, null), expiry);
    }
    private DraftArtifactReader reader(ProviderProperties p) {
        return new DraftArtifactReader(DraftTransportFactory.create(p, true), Clock.systemUTC());
    }

    @Test public void closesResponseAndReleasesExactlyOnePermit() throws Exception {
        try (ProtocolFixture f = new ProtocolFixture(false)) {
            f.contentType = "image/png"; f.response = new byte[]{1, 2, 3};
            DraftArtifactReader reader = reader(properties(f.url()));
            ProviderArtifact artifact = artifact("/artifacts/output", 3, Instant.now().plusSeconds(60));
            InputStream first = reader.open(binding(false), artifact, 3);
            try { reader.open(binding(false), artifact, 3); fail(); }
            catch (ProviderException e) { assertEquals(ErrorCode.LIMIT_EXCEEDED, e.getErrorCode()); }
            assertArrayEquals(f.response, ProtocolFixture.read(first)); first.close(); first.close();
            try (InputStream second = reader.open(binding(false), artifact, 3)) {
                try { reader.open(binding(false), artifact, 3); fail(); }
                catch (ProviderException e) { assertEquals(ErrorCode.LIMIT_EXCEEDED, e.getErrorCode()); }
                assertArrayEquals(f.response, ProtocolFixture.read(second));
            }
            assertEquals(2, f.calls.get());
        }
    }

    @Test public void unapprovedReferencesExpiryAndLimitsNeverMakeRequests() throws Exception {
        try (ProtocolFixture f = new ProtocolFixture(false)) {
            DraftArtifactReader reader = reader(properties(f.url()));
            for (String ref : new String[]{"http://127.0.0.1/private", "//other/artifacts/x", "/artifacts/../x", "/artifacts/x?token=secret", "/artifacts/%2e%2e"}) {
                expectOpenFailure(reader, artifact(ref, 3, Instant.now().plusSeconds(60)), 10);
            }
            expectOpenFailure(reader, artifact("/artifacts/x", 3, Instant.EPOCH), 10);
            expectOpenFailure(reader, artifact("/artifacts/x", 11, Instant.now().plusSeconds(60)), 10);
            assertEquals(0, f.calls.get());
        }
    }

    @Test public void artifactRedirectAndRetryAfterDoNotFollowUp() throws Exception {
        for (int status : new int[]{301, 307, 408, 503}) {
            try (ProtocolFixture f = new ProtocolFixture(false)) {
                f.status = status; f.contentType = "image/png"; f.response = new byte[]{1};
                expectOpenFailure(reader(properties(f.url())), artifact("/artifacts/x", 1, Instant.now().plusSeconds(60)), 10);
                assertEquals(1, f.calls.get());
            }
        }
    }

    @Test public void truncatedAndStalledArtifactCannotComplete() throws Exception {
        for (boolean truncated : new boolean[]{true, false}) {
            try (ProtocolFixture f = new ProtocolFixture(false)) {
                f.contentType = "image/png"; f.response = new byte[]{1, 2, 3};
                f.truncated = truncated; f.delayBody = truncated ? 0 : 300;
                ProviderProperties p = properties(f.url()); p.setTransferTimeoutMs(100);
                long expectedLength = truncated ? 23 : 3;
                DraftArtifactReader reader = reader(p);
                try (InputStream input = reader.open(binding(false), artifact("/artifacts/x", expectedLength, Instant.now().plusSeconds(60)), 30)) {
                    try { ProtocolFixture.read(input); fail(); } catch (IOException expected) { }
                }
                assertEquals(1, f.calls.get());
            }
        }
    }

    @Test public void wrongArtifactMediaOrLengthIsRejected() throws Exception {
        try (ProtocolFixture f = new ProtocolFixture(false)) {
            f.json("xyz");
            expectOpenFailure(reader(properties(f.url())), artifact("/artifacts/x", 3, Instant.now().plusSeconds(60)), 10);
            f.contentType = "image/png";
            expectOpenFailure(reader(properties(f.url())), artifact("/artifacts/x", 4, Instant.now().plusSeconds(60)), 10);
        }
    }

    @Test public void tlsUsesTrustedCaAndNormalHostnameValidation() throws Exception {
        try (ProtocolFixture f = new ProtocolFixture(true)) {
            f.json(example("provider-empty.json"));
            ProviderProperties p = properties(f.url());
            try { provider(p).infer(request(new CountingSource(), false, true, null)); fail(); }
            catch (ProviderException e) { assertEquals(ExecutionCertainty.NOT_STARTED, e.getCertainty()); }
            assertEquals(0, f.calls.get());
            p.setCaFile("/validation/server.crt");
            assertTrue(provider(p).infer(request(new CountingSource(), false, true, null)).isSimulated());
            p.setBaseUrl(f.url().replace("127.0.0.1", "localhost")); p.setApprovedOrigin(p.getBaseUrl());
            try { provider(p).infer(request(new CountingSource(), false, true, null)); fail(); }
            catch (ProviderException e) { assertEquals(ExecutionCertainty.NOT_STARTED, e.getCertainty()); }
            assertEquals(1, f.calls.get());
        }
    }

    @Test public void endpointApprovalAndConfigurationAreConsumed() throws Exception {
        ProviderProperties p = properties("http://127.0.0.1:1234");
        try { DraftTransportFactory.create(p, false); fail(); } catch (IllegalArgumentException expected) { }
        p.setApprovedOrigin("http://127.0.0.1:4321");
        try { DraftTransportFactory.create(p, true); fail(); } catch (IllegalArgumentException expected) { }
        p.setApprovedOrigin(p.getBaseUrl()); p.setCaFile("/validation/missing-ca");
        try { DraftTransportFactory.create(p, true); fail(); } catch (IllegalStateException expected) { }
        p.setCaFile(""); p.setConnectTimeoutMs(0);
        try { DraftTransportFactory.create(p, true); fail(); } catch (IllegalArgumentException expected) { }
    }

    private void expectOpenFailure(DraftArtifactReader reader, ProviderArtifact artifact, long limit) throws Exception {
        try { reader.open(binding(false), artifact, limit); fail("Expected artifact rejection"); }
        catch (ProviderException e) { assertNull(e.getCause()); }
    }
}
