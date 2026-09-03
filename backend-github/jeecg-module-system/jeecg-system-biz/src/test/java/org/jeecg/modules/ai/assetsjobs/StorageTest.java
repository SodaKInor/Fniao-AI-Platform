package org.jeecg.modules.ai.assetsjobs;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import static org.junit.Assert.*;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.storage.PrivateFileArtifactStore;
import org.jeecg.modules.ai.application.assets.AssetService;
import org.jeecg.modules.ai.application.jobs.AiRequestException;

public class StorageTest {
    private DbFixture f;
    @Before public void before() throws Exception { f=new DbFixture(); }
    @After public void after() throws Exception { f.close(); }

    @Test public void integrityLimitAndBorrowedStreamOwnership() throws Exception {
        AtomicBoolean closed=new AtomicBoolean();
        InputStream input=new ByteArrayInputStream(f.png) { public void close() { closed.set(true); } };
        StoredArtifact stored=f.store.write(new ContentMetadata("in.png","image/png",(long)f.png.length,null),input,1024*1024);
        assertFalse(closed.get()); input.close(); assertTrue(closed.get());
        assertEquals(f.png.length,stored.getSizeBytes()); assertTrue(stored.getSha256().matches("[a-f0-9]{64}"));
        for (ContentMetadata bad:Arrays.asList(new ContentMetadata("in.png","image/png",(long)f.png.length+1,null),
                new ContentMetadata("in.png","image/png",null,"bad-hash"))) {
            try { f.store.write(bad,new ByteArrayInputStream(f.png),1024*1024); fail(); } catch (IOException expected) { }
        }
        try { f.store.write(new ContentMetadata("in.png","image/png",null,null),new ByteArrayInputStream(f.png),1); fail(); }
        catch (IOException expected) { }
        assertEquals(1,f.countFiles());
    }

    @Test public void oversizedUnknownLengthAndWrongSignatureAreRejected() throws Exception {
        AssetService bounded=new AssetService(f.assets,f.store,f.clock,16,Duration.ofDays(7),Duration.ofDays(30));
        try { bounded.upload("a",new ContentMetadata("in.png","image/png",null,null),new ByteArrayInputStream(f.png)); fail(); }
        catch (AiRequestException expected) { assertEquals(ErrorCode.LIMIT_EXCEEDED,expected.getCode()); }
        try { f.files.upload("a",new ContentMetadata("fake.jpg","image/jpeg",(long)f.png.length,null),new ByteArrayInputStream(f.png)); fail(); }
        catch (AiRequestException expected) { assertEquals(ErrorCode.UNSUPPORTED_MEDIA,expected.getCode()); }
        assertEquals(0,f.countFiles());
    }

    @Test public void privateRootsAndStorageKeysCannotEscape() throws Exception {
        try { new PrivateFileArtifactStore(f.privateRoot,Collections.singletonList(f.privateRoot.getParent()),4096); fail(); }
        catch (IllegalArgumentException expected) { }
        try { f.store.open("../anything"); fail(); } catch (IOException expected) { }
        Path link=f.privateRoot.resolve("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.bin");
        Files.createSymbolicLink(link,Paths.get("/etc/passwd"));
        try { f.store.open(link.getFileName().toString()); fail(); } catch (IOException expected) { }
        try { new PrivateFileArtifactStore(link.resolve("child"),Collections.emptyList(),4096); fail(); }
        catch (IOException expected) { }
    }

    @Test public void truncatedImageCannotBecomeAnAsset() throws Exception {
        byte[] truncated=Arrays.copyOf(f.png,f.png.length-12);
        try { f.files.upload("a",new ContentMetadata("cut.png","image/png",(long)truncated.length,null),new ByteArrayInputStream(truncated)); fail(); }
        catch (AiRequestException expected) { assertEquals(ErrorCode.UNSUPPORTED_MEDIA,expected.getCode()); }
        assertEquals(0,f.countFiles());
    }

    @Test public void dimensionsAreCheckedBeforeDecode() throws Exception {
        Path directory=Files.createDirectory(f.privateRoot.resolve("small"));
        PrivateFileArtifactStore small=new PrivateFileArtifactStore(directory,Collections.emptyList(),1);
        try { small.write(new ContentMetadata("in.png","image/png",(long)f.png.length,null),new ByteArrayInputStream(f.png),1024*1024); fail(); }
        catch (IllegalArgumentException expected) { }
        try (java.util.stream.Stream<Path> files=Files.list(directory)) { assertEquals(0,files.count()); }
    }
}
