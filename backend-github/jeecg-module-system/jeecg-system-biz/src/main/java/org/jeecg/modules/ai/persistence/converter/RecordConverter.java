package org.jeecg.modules.ai.persistence.converter;

import java.time.Instant;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.persistence.entity.*;

public final class RecordConverter {
    private final SnapshotCodec codec;
    private final VideoSnapshotCodec video;
    public RecordConverter(SnapshotCodec codec) { this(codec,new VideoSnapshotCodec()); }
    public RecordConverter(SnapshotCodec codec,VideoSnapshotCodec video) { this.codec=codec; this.video=video; }

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
        JobRequest request=codec.request(r.requestJson);
        boolean videoJob=request.getJobType()==JobType.VIDEO_FILE_ANALYSIS;
        return new JobRecord(request,JobState.valueOf(r.state),r.version,r.dispatchToken,
                videoJob ? null : codec.checkpoint(r.checkpointJson),
                videoJob ? video.checkpoint(r.checkpointJson) : null,
                videoJob ? null : codec.result(r.resultJson),
                videoJob ? video.result(r.resultJson) : null,
                codec.error(r.errorJson),codec.unknownReason(r.errorJson),Instant.ofEpochMilli(r.updatedAt));
    }

    public JobRow create(JobRequest q) {
        JobRow r = new JobRow();
        r.requestId=q.getRequestId(); r.ownerId=q.getOwnerId(); r.idempotencyKey=q.getIdempotencyKey();
        r.requestDigest=q.getRequestDigest(); r.requestJson=codec.write(q); r.state="PENDING";
        r.createdAt=q.getCreatedAt().toEpochMilli(); r.updatedAt=r.createdAt;
        return r;
    }

    public void update(JobRow r, JobUpdate u) {
        r.state=u.getState().name();
        r.checkpointJson=u.getVideoProviderResult()==null ? codec.write(u.getProviderResult()) : video.write(u.getVideoProviderResult());
        r.resultJson=u.getVideoResult()==null ? codec.write(u.getResult()) : video.write(u.getVideoResult());
        UnknownOperationReason reason=u.getUnknownReason();
        if (u.getState()==JobState.UNKNOWN && reason==null && u.getError()!=null
                && u.getError().getCode()==ErrorCode.RESULT_UNKNOWN) {
            reason=UnknownOperationReason.PROVIDER_RESPONSE_LOST;
        }
        r.errorJson=codec.errorSnapshot(u.getError(),reason);
        r.updatedAt=u.getUpdatedAt().toEpochMilli(); r.version++;
    }
}
