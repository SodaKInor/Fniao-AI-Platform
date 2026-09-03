package org.jeecg.modules.ai.persistence.converter;

import java.time.Instant;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.persistence.entity.*;

public final class RecordConverter {
    private final SnapshotCodec codec;
    public RecordConverter(SnapshotCodec codec) { this.codec = codec; }

    public Asset asset(AssetRow r) {
        return new Asset(r.assetId,r.ownerId,r.fileName,r.mediaType,
                new StoredArtifact(r.storageKey,r.sizeBytes,r.sha256),
                Instant.ofEpochMilli(r.createdAt),Instant.ofEpochMilli(r.expiresAt));
    }

    public AssetRow asset(Asset a) {
        AssetRow r = new AssetRow();
        r.assetId=a.getAssetId(); r.ownerId=a.getOwnerId(); r.fileName=a.getFileName(); r.mediaType=a.getMediaType();
        r.storageKey=a.getStored().getStorageKey(); r.sizeBytes=a.getStored().getSizeBytes(); r.sha256=a.getStored().getSha256();
        r.createdAt=a.getCreatedAt().toEpochMilli(); r.expiresAt=a.getExpiresAt().toEpochMilli();
        return r;
    }

    public JobRecord job(JobRow r) {
        return new JobRecord(codec.request(r.requestJson),JobState.valueOf(r.state),r.version,r.dispatchToken,
                codec.checkpoint(r.checkpointJson),codec.result(r.resultJson),codec.error(r.errorJson),
                Instant.ofEpochMilli(r.updatedAt));
    }

    public JobRow create(JobRequest q) {
        JobRow r = new JobRow();
        r.requestId=q.getRequestId(); r.ownerId=q.getOwnerId(); r.idempotencyKey=q.getIdempotencyKey();
        r.requestDigest=q.getRequestDigest(); r.requestJson=codec.write(q); r.state="PENDING";
        r.createdAt=q.getCreatedAt().toEpochMilli(); r.updatedAt=r.createdAt;
        return r;
    }

    public void update(JobRow r, JobUpdate u) {
        r.state=u.getState().name(); r.checkpointJson=codec.write(u.getProviderResult());
        r.resultJson=codec.write(u.getResult()); r.errorJson=codec.write(u.getError());
        r.updatedAt=u.getUpdatedAt().toEpochMilli(); r.version++;
    }
}
