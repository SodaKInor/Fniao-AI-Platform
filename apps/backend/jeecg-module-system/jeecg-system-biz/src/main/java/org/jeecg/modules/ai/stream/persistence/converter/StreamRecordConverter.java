package org.jeecg.modules.ai.stream.persistence.converter;

import org.jeecg.modules.ai.job.domain.UnknownOperationReason;
import org.jeecg.modules.ai.job.persistence.converter.SnapshotCodec;
import org.jeecg.modules.ai.stream.domain.StreamEvent;
import org.jeecg.modules.ai.stream.domain.StreamSession;
import org.jeecg.modules.ai.stream.domain.StreamSessionRequest;
import org.jeecg.modules.ai.stream.domain.StreamSessionState;
import org.jeecg.modules.ai.stream.domain.StreamSessionUpdate;
import org.jeecg.modules.ai.stream.domain.StreamSource;
import org.jeecg.modules.ai.stream.persistence.entity.StreamEventRow;
import org.jeecg.modules.ai.stream.persistence.entity.StreamSessionRow;
import org.jeecg.modules.ai.stream.persistence.entity.StreamSourceRow;

import java.time.Instant;

public final class StreamRecordConverter {
    private final StreamSnapshotCodec stream;
    private final SnapshotCodec common;
    public StreamRecordConverter(StreamSnapshotCodec stream,SnapshotCodec common) {
        this.stream=stream; this.common=common;
    }

    public StreamSource source(StreamSourceRow row) {
        return new StreamSource(row.streamSourceId,row.ownerId,row.displayName,row.providerSourceRef,
                row.enabled,row.unavailableReason);
    }

    public StreamSession session(StreamSessionRow row) {
        return new StreamSession(stream.request(row.requestJson),StreamSessionState.valueOf(row.state),row.version,
                row.dispatchToken,row.providerSessionId,row.providerCursor,
                row.unknownReason==null ? null : UnknownOperationReason.valueOf(row.unknownReason),
                common.error(row.errorJson),Instant.ofEpochMilli(row.updatedAt));
    }

    public StreamSessionRow create(StreamSessionRequest request) {
        StreamSessionRow row=new StreamSessionRow();
        row.sessionId=request.getSessionId(); row.ownerId=request.getOwnerId();
        row.idempotencyKey=request.getIdempotencyKey(); row.requestDigest=request.getRequestDigest();
        row.requestJson=stream.write(request); row.streamSourceId=request.getStreamSourceId(); row.state="PENDING";
        row.createdAt=request.getCreatedAt().toEpochMilli(); row.updatedAt=row.createdAt;
        return row;
    }

    public void update(StreamSessionRow row,StreamSessionUpdate update) {
        row.state=update.getState().name(); row.providerSessionId=update.getProviderSessionId();
        row.providerCursor=update.getCursor();
        row.unknownReason=update.getUnknownReason()==null ? null : update.getUnknownReason().name();
        row.errorJson=common.write(update.getError()); row.updatedAt=update.getUpdatedAt().toEpochMilli(); row.version++;
    }

    public StreamEvent event(StreamEventRow row) {
        return new StreamEvent(row.eventId,row.providerEventId,row.offsetMillis,Instant.ofEpochMilli(row.occurredAt),
                row.eventType,row.score,row.snapshotAssetId);
    }

    public StreamEventRow event(String sessionId,StreamEvent event) {
        StreamEventRow row=new StreamEventRow(); row.sessionId=sessionId;
        row.providerEventId=event.getProviderEventId(); row.eventId=event.getEventId();
        row.offsetMillis=event.getOffsetMillis(); row.occurredAt=event.getOccurredAt().toEpochMilli();
        row.eventType=event.getEventType(); row.score=event.getScore(); row.snapshotAssetId=event.getSnapshotAssetId();
        return row;
    }
}
