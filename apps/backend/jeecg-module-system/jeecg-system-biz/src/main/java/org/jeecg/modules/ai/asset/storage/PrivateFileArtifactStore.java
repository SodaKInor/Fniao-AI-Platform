package org.jeecg.modules.ai.asset.storage;

import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.asset.domain.StoredArtifact;
import org.jeecg.modules.ai.image.storage.ImageFileVerifier;
import org.jeecg.modules.ai.video.storage.VideoFileVerifier;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import org.jeecg.modules.ai.asset.port.ArtifactStore;

/** Publishes verified immutable files on the same filesystem; never persists business metadata. */
public final class PrivateFileArtifactStore implements ArtifactStore {
    private final Path root;
    private final ImageFileVerifier images;
    private final VideoFileVerifier videos=new VideoFileVerifier();

    public PrivateFileArtifactStore(Path directory, List<Path> publicRoots, int maxDimension) throws IOException {
        if (!directory.isAbsolute()) throw new IllegalArgumentException("Private asset root must be absolute");
        root=directory.normalize();
        noLinks(root);
        for (Path publicRoot : publicRoots) {
            Path other=publicRoot.toAbsolutePath().normalize();
            if (Files.exists(other)) other=other.toRealPath();
            if (root.startsWith(other) || other.startsWith(root))
                throw new IllegalArgumentException("Private assets overlap public files");
        }
        Files.createDirectories(root);
        noLinks(root);
        permissions(root,"rwx------");
        images=new ImageFileVerifier(maxDimension);
    }

    private static void noLinks(Path path) throws IOException {
        for (Path p=path; p != null; p=p.getParent())
            if (Files.isSymbolicLink(p)) throw new IOException("Symbolic links are not allowed in private storage");
    }

    private static void permissions(Path path, String mode) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(mode));
    }

    public StoredArtifact write(ContentMetadata expected, InputStream input, long maxBytes) throws IOException {
        Objects.requireNonNull(input,"input"); Objects.requireNonNull(expected,"metadata");
        if (maxBytes < 1) throw new IllegalArgumentException("Invalid byte limit");
        if (expected.getSizeBytes() != null && (expected.getSizeBytes()<1 || expected.getSizeBytes()>maxBytes))
            throw new IOException("Declared content length exceeds limit");
        noLinks(root);
        Path temporary=Files.createTempFile(root,"partial-",".tmp");
        boolean published=false;
        try {
            permissions(temporary,"rw-------");
            MessageDigest digest=sha256();
            long count=copy(input,temporary,maxBytes,digest);
            String hash=hex(digest.digest());
            if (count == 0 || (expected.getSizeBytes()!=null && count!=expected.getSizeBytes())
                    || (expected.getSha256()!=null && !hash.equals(expected.getSha256())))
                throw new IOException("Artifact integrity check failed");
            if ("video/mp4".equals(expected.getMediaType())) videos.verify(temporary);
            else images.verify(temporary,expected.getMediaType());
            String key=UUID.randomUUID().toString().replace("-","")+".bin";
            Files.move(temporary,path(key),StandardCopyOption.ATOMIC_MOVE);
            published=true;
            return new StoredArtifact(key,count,hash);
        } finally { if (!published) Files.deleteIfExists(temporary); }
    }

    private long copy(InputStream input, Path target, long maxBytes, MessageDigest digest) throws IOException {
        long count=0;
        byte[] buffer=new byte[65536];
        try (FileChannel out=FileChannel.open(target,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
            int n;
            while ((n=input.read(buffer,0,(int)Math.min(buffer.length,maxBytes-count+1)))!=-1) {
                if (n==0) continue;
                count+=n;
                if (count>maxBytes) throw new IOException("Content exceeds byte limit");
                digest.update(buffer,0,n);
                ByteBuffer bytes=ByteBuffer.wrap(buffer,0,n);
                while (bytes.hasRemaining()) out.write(bytes);
            }
            out.force(true);
        }
        return count;
    }

    private Path path(String key) throws IOException {
        if (key==null || !key.matches("[a-f0-9]{32}\\.bin")) throw new IOException("Invalid private storage key");
        noLinks(root);
        Path path=root.resolve(key);
        if (Files.isSymbolicLink(path)) throw new IOException("Invalid private storage file");
        return path;
    }

    public InputStream open(String key) throws IOException {
        Path file=path(key);
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new FileNotFoundException("Private file unavailable");
        return Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS);
    }
    public void delete(String key) throws IOException { Files.deleteIfExists(path(key)); }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder result=new StringBuilder(64);
        for (byte b:bytes) result.append(String.format("%02x",b & 255));
        return result.toString();
    }
}
