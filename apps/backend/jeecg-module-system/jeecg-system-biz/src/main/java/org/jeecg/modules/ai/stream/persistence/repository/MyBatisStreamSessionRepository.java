package org.jeecg.modules.ai.stream.persistence.repository;

import org.jeecg.modules.ai.job.domain.IdempotencyConflictException;
import org.jeecg.modules.ai.stream.domain.StreamSession;
import org.jeecg.modules.ai.stream.domain.StreamSessionRequest;
import org.jeecg.modules.ai.stream.domain.StreamSessionState;
import org.jeecg.modules.ai.stream.domain.StreamSessionSubmission;
import org.jeecg.modules.ai.stream.domain.StreamSessionUpdate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.jeecg.modules.ai.stream.port.StreamSessionRepository;
import org.jeecg.modules.ai.stream.persistence.converter.StreamRecordConverter;
import org.jeecg.modules.ai.stream.persistence.entity.StreamSessionRow;
import org.jeecg.modules.ai.stream.persistence.mapper.StreamSessionMapper;

public final class MyBatisStreamSessionRepository implements StreamSessionRepository {
    private final StreamSessionMapper mapper;
    private final StreamRecordConverter converter;
    private final TransactionTemplate transaction;
    private final int maxPending;
    private final int maxActive;
    public MyBatisStreamSessionRepository(StreamSessionMapper mapper,StreamRecordConverter converter,
            PlatformTransactionManager manager,int maxPending,int maxActive) {
        this.mapper=mapper; this.converter=converter; this.maxPending=maxPending; this.maxActive=maxActive;
        transaction=new TransactionTemplate(manager); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public StreamSessionSubmission createOrGet(StreamSessionRequest request) {
        return transaction.execute(status -> {
            capacity(); StreamSessionRow old=mapper.findByKey(request.getOwnerId(),request.getIdempotencyKey());
            if (old!=null) {
                if (!old.requestDigest.equals(request.getRequestDigest())) throw new IdempotencyConflictException();
                return new StreamSessionSubmission(converter.session(old),false);
            }
            if (mapper.pendingCount()>=maxPending) throw new RejectedExecutionException("AI stream queue is full");
            StreamSessionRow row=converter.create(request); mapper.insert(row);
            return new StreamSessionSubmission(converter.session(row),true);
        });
    }
    public Optional<StreamSession> findByKeyOwned(String owner,String key) {
        return Optional.ofNullable(mapper.findByKey(owner,key)).map(converter::session);
    }
    public Optional<StreamSession> findOwned(String id,String owner) {
        return Optional.ofNullable(mapper.findOwned(id,owner)).map(converter::session);
    }
    public List<StreamSession> findPending(int limit) { requireLimit(limit); return rows(mapper.pending(limit)); }
    public List<StreamSession> findRecoverable(int limit) { requireLimit(limit); return rows(mapper.recoverable(limit)); }
    public int pendingCount() { return mapper.pendingCount(); }
    public int activeCount() { return mapper.activeCount(); }

    public Optional<StreamSession> claimPending(String id,long version,String token,Instant now) {
        requireToken(token);
        return transaction.execute(status -> {
            capacity(); StreamSessionRow row=mapper.lock(id);
            if (row==null || !"PENDING".equals(row.state) || row.version!=version || mapper.activeCount()>=maxActive)
                return Optional.empty();
            row.state="STARTING"; row.version++; row.dispatchToken=token; row.updatedAt=now.toEpochMilli(); save(row);
            return Optional.of(converter.session(row));
        });
    }

    public Optional<StreamSession> claimRecoverable(String id,long version,String priorToken,String token,Instant now) {
        requireToken(token);
        return transaction.execute(status -> {
            StreamSessionRow row=mapper.lock(id);
            if (row==null || row.version!=version || !Objects.equals(row.dispatchToken,priorToken)
                    || !("STARTING".equals(row.state) || "RUNNING".equals(row.state)
                    || "STOP_REQUESTED".equals(row.state))) return Optional.empty();
            row.version++; row.dispatchToken=token; row.updatedAt=now.toEpochMilli(); save(row);
            return Optional.of(converter.session(row));
        });
    }

    public boolean updateClaimed(String id,long version,String token,StreamSessionUpdate update) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            StreamSessionRow row=mapper.lock(id);
            if (row==null || token==null || !token.equals(row.dispatchToken) || row.version!=version
                    || !allows(converter.session(row),update)) return false;
            converter.update(row,update); save(row); return true;
        }));
    }

    public Optional<StreamSession> requestStopOwned(String id,String owner,long version,Instant now) {
        return transaction.execute(status -> {
            StreamSessionRow row=mapper.lock(id);
            if (row==null || !owner.equals(row.ownerId) || row.version!=version) return Optional.empty();
            StreamSession current=converter.session(row);
            if (current.getState()==StreamSessionState.STOP_REQUESTED) return Optional.of(current);
            if (current.getState()==StreamSessionState.PENDING) row.state="STOPPED";
            else if ((current.getState()==StreamSessionState.STARTING || current.getState()==StreamSessionState.RUNNING)
                    && current.getRequest().getProviderFeatures().isStop()) row.state="STOP_REQUESTED";
            else return Optional.empty();
            row.version++; row.updatedAt=now.toEpochMilli(); save(row); return Optional.of(converter.session(row));
        });
    }

    private boolean allows(StreamSession old,StreamSessionUpdate update) {
        if (update==null || update.getState()==null || update.getUpdatedAt()==null
                || update.getUpdatedAt().isBefore(old.getUpdatedAt())) return false;
        StreamSessionState from=old.getState(),to=update.getState();
        boolean legal=(from==StreamSessionState.STARTING && (to==StreamSessionState.STARTING
                || to==StreamSessionState.RUNNING || to==StreamSessionState.FAILED || to==StreamSessionState.UNKNOWN))
                || (from==StreamSessionState.RUNNING && (to==StreamSessionState.RUNNING
                || to==StreamSessionState.STOPPED || to==StreamSessionState.FAILED || to==StreamSessionState.UNKNOWN))
                || (from==StreamSessionState.STOP_REQUESTED && (to==StreamSessionState.STOPPED
                || to==StreamSessionState.FAILED || to==StreamSessionState.UNKNOWN));
        if (!legal || (old.getProviderSessionId()!=null
                && !old.getProviderSessionId().equals(update.getProviderSessionId()))) return false;
        if ((to==StreamSessionState.RUNNING || to==StreamSessionState.STOPPED)
                && update.getProviderSessionId()==null) return false;
        if (to==StreamSessionState.UNKNOWN && update.getUnknownReason()==null) return false;
        if (to==StreamSessionState.FAILED && update.getError()==null) return false;
        return to==StreamSessionState.UNKNOWN || update.getUnknownReason()==null;
    }
    private List<StreamSession> rows(List<StreamSessionRow> rows) {
        return rows.stream().map(converter::session).collect(Collectors.toList());
    }
    private void requireLimit(int limit) {
        if (limit<1 || limit>100) throw new IllegalArgumentException("Invalid candidate limit");
    }
    private void capacity() { if (mapper.lockCapacity()==null) throw new IllegalStateException("AI migration missing"); }
    private void requireToken(String token) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid claim token");
    }
    private void save(StreamSessionRow row) {
        if (mapper.update(row)!=1) throw new IllegalStateException("Stream state update lost its version");
    }
}
