package org.jeecg.modules.ai.asset.application;

import org.jeecg.modules.ai.asset.domain.Asset;
import org.jeecg.modules.ai.asset.domain.ContentMetadata;
import org.jeecg.modules.ai.asset.domain.ContentSource;
import org.jeecg.modules.ai.asset.domain.StoredArtifact;
import org.jeecg.modules.ai.asset.port.ArtifactStore;
import org.jeecg.modules.ai.asset.port.AssetRepository;
import org.jeecg.modules.ai.job.domain.ErrorCode;

import java.io.*;
import java.time.*;
import java.util.*;
import org.jeecg.modules.ai.job.application.AiRequestException;

/** Ownership and metadata orchestration; paths and media verification belong to the store. */
public final class AssetService {
    private final AssetRepository assets;
    private final ArtifactStore store;
    private final Clock clock;
    private final long maxImageBytes;
    private final long maxVideoBytes;
    private final Duration inputRetention;
    private final Duration outputRetention;

    public AssetService(AssetRepository assets, ArtifactStore store, Clock clock, long maxBytes,
                        Duration inputRetention, Duration outputRetention) {
        this(assets,store,clock,maxBytes,maxBytes,inputRetention,outputRetention);
    }

    public AssetService(AssetRepository assets, ArtifactStore store, Clock clock, long maxImageBytes,
                        long maxVideoBytes, Duration inputRetention, Duration outputRetention) {
        this.assets=assets; this.store=store; this.clock=clock;
        this.maxImageBytes=maxImageBytes; this.maxVideoBytes=maxVideoBytes;
        this.inputRetention=inputRetention; this.outputRetention=outputRetention;
    }

    public Asset owned(String id, String owner) {
        Asset asset=assets.findOwned(id,owner).orElseThrow(() -> new AiRequestException(ErrorCode.NOT_FOUND,"Asset not found"));
        if (!clock.instant().isBefore(asset.getExpiresAt())) throw new AiRequestException(ErrorCode.ASSET_EXPIRED,"Asset expired");
        return asset;
    }

    public Asset upload(String owner, ContentMetadata metadata, InputStream input) {
        long limit=mediaLimit(metadata);
        validateMetadata(metadata,limit);
        ContentMetadata safe=new ContentMetadata(fileName(metadata.getFileName()),metadata.getMediaType(),metadata.getSizeBytes(),null);
        try { return save(UUID.randomUUID().toString(),owner,safe,new UploadLimitStream(input,limit),limit,inputRetention); }
        catch (IllegalArgumentException e) { throw new AiRequestException(ErrorCode.UNSUPPORTED_MEDIA,"Invalid media content"); }
        catch (IOException e) { throw new AiRequestException(ErrorCode.INTERNAL_ERROR,"Could not save input file"); }
    }

    public Asset collect(String id, String owner, ContentMetadata metadata, InputStream input, long limit) throws IOException {
        Optional<Asset> prior=assets.findOwned(id,owner);
        if (prior.isPresent()) return owned(id,owner);
        long effective=Math.min(limit,mediaLimit(metadata));
        validateMetadata(metadata,effective);
        ContentMetadata safe=new ContentMetadata(fileName(metadata.getFileName()),metadata.getMediaType(),metadata.getSizeBytes(),metadata.getSha256());
        return save(id,owner,safe,input,effective,outputRetention);
    }

    public Optional<Asset> collected(String id, String owner) { return assets.findOwned(id,owner); }

    private Asset save(String id, String owner, ContentMetadata metadata, InputStream input,
                       long limit, Duration retention) throws IOException {
        StoredArtifact stored=store.write(metadata,input,limit);
        Instant now=Instant.ofEpochMilli(clock.millis());
        Asset asset=new Asset(id,owner,metadata.getFileName(),metadata.getMediaType(),stored,now,now.plus(retention));
        try { assets.insert(asset); return asset; }
        catch (RuntimeException failure) {
            // A lost commit acknowledgement may mean metadata already exists: never delete its file.
            try {
                Optional<Asset> existing=assets.findOwned(id,owner);
                if (existing.isPresent() && existing.get().getStored().getStorageKey().equals(stored.getStorageKey())) return existing.get();
                store.delete(stored.getStorageKey());
            } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public InputStream open(String id, String owner) throws IOException {
        return store.open(owned(id,owner).getStored().getStorageKey());
    }

    public ContentSource source(String id, String owner) {
        return () -> open(id,owner);
    }

    public void validateMetadata(ContentMetadata m, long limit) {
        if (m==null || !("image/png".equals(m.getMediaType()) || "image/jpeg".equals(m.getMediaType())
                || "video/mp4".equals(m.getMediaType())))
            throw new AiRequestException(ErrorCode.UNSUPPORTED_MEDIA,"Only PNG, JPEG and MP4/H.264 are supported");
        if (m.getSizeBytes()!=null && (m.getSizeBytes()<1 || m.getSizeBytes()>limit))
            throw new AiRequestException(ErrorCode.LIMIT_EXCEEDED,"File exceeds input limits");
    }

    private long mediaLimit(ContentMetadata metadata) {
        if (metadata==null) throw new AiRequestException(ErrorCode.UNSUPPORTED_MEDIA,"Missing media metadata");
        return "video/mp4".equals(metadata.getMediaType()) ? maxVideoBytes : maxImageBytes;
    }

    private String fileName(String name) {
        if (name==null) return "image";
        String safe=name.replace('\\','/');
        safe=safe.substring(safe.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_");
        return safe.isEmpty() ? "image" : safe.substring(0,Math.min(255,safe.length()));
    }

    private static final class UploadLimitStream extends FilterInputStream {
        private final long limit;
        private long count;
        UploadLimitStream(InputStream in,long limit) { super(in); this.limit=limit; }
        private int counted(int n) {
            if (n>0 && (count+=n)>limit) throw new AiRequestException(ErrorCode.LIMIT_EXCEEDED,"File exceeds input limits");
            return n;
        }
        public int read() throws IOException { int n=in.read(); counted(n<0 ? 0 : 1); return n; }
        public int read(byte[] b,int offset,int length) throws IOException { return counted(in.read(b,offset,length)); }
    }
}
