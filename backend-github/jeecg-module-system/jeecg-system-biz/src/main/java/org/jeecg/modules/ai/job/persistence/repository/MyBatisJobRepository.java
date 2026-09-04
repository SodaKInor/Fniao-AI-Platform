package org.jeecg.modules.ai.job.persistence.repository;

import org.jeecg.modules.ai.asset.persistence.mapper.AssetMapper;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.IdempotencyConflictException;
import org.jeecg.modules.ai.job.domain.JobError;
import org.jeecg.modules.ai.job.domain.JobPage;
import org.jeecg.modules.ai.job.domain.JobRecord;
import org.jeecg.modules.ai.job.domain.JobRequest;
import org.jeecg.modules.ai.job.domain.JobState;
import org.jeecg.modules.ai.job.domain.JobSubmission;
import org.jeecg.modules.ai.job.domain.JobUpdate;
import org.jeecg.modules.ai.job.domain.UnknownOperationReason;
import org.jeecg.modules.ai.job.persistence.converter.ClaimedUpdateRules;
import org.jeecg.modules.ai.job.persistence.converter.RecordConverter;
import org.jeecg.modules.ai.job.persistence.converter.SnapshotCodec;
import org.jeecg.modules.ai.job.persistence.mapper.JobMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.jeecg.modules.ai.job.port.JobRepository;
import org.jeecg.modules.ai.job.persistence.entity.JobRow;

/** All transactions end before the application can perform network or file I/O. */
public final class MyBatisJobRepository implements JobRepository {
    private final JobMapper mapper;
    private final AssetMapper assets;
    private final SnapshotCodec codec;
    private final RecordConverter converter;
    private final ClaimedUpdateRules rules = new ClaimedUpdateRules();
    private final TransactionTemplate transaction;
    private final int maxPending;
    private final int maxActive;

    public MyBatisJobRepository(JobMapper mapper, AssetMapper assets, SnapshotCodec codec,
                               PlatformTransactionManager manager, int maxPending, int maxActive) {
        this.mapper=mapper; this.assets=assets; this.codec=codec; this.converter=new RecordConverter(codec);
        this.maxPending=maxPending; this.maxActive=maxActive;
        transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private void capacity() {
        if (mapper.lockCapacity() == null) throw new IllegalStateException("AI migration has not been applied");
    }

    public JobSubmission createOrGet(JobRequest request) {
        return transaction.execute(status -> {
            capacity();
            JobRow old=mapper.findByKey(request.getOwnerId(),request.getIdempotencyKey());
            if (old != null) {
                if (!old.requestDigest.equals(request.getRequestDigest())) throw new IdempotencyConflictException();
                return new JobSubmission(converter.job(old),false);
            }
            if (mapper.pendingCount() >= maxPending) throw new RejectedExecutionException("AI queue is full");
            JobRow row=converter.create(request);
            mapper.insert(row); mapper.event(row);
            return new JobSubmission(converter.job(row),true);
        });
    }

    public Optional<JobRecord> findByKeyOwned(String owner, String key) {
        return Optional.ofNullable(mapper.findByKey(owner,key)).map(converter::job);
    }
    public Optional<JobRecord> findOwned(String id, String owner) {
        return Optional.ofNullable(mapper.findOwned(id,owner)).map(converter::job);
    }
    public List<JobRecord> findPending(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid candidate limit");
        return mapper.pending(limit).stream().map(converter::job).collect(Collectors.toList());
    }

    /** Stale result candidates retain their checkpoint; active downloads are never stolen. */
    public List<JobRecord> findFetchingResult(Instant staleBefore,int limit) {
        requireRecoveryArguments(staleBefore,limit);
        return mapper.fetching(staleBefore.toEpochMilli(),limit).stream().map(converter::job).collect(Collectors.toList());
    }

    public Optional<JobRecord> claimFetchingResult(String id,long version,String token,Instant staleBefore,Instant now) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid claim token");
        if (staleBefore==null || now==null || staleBefore.isAfter(now)) throw new IllegalArgumentException("Invalid recovery time");
        return transaction.execute(status -> {
            JobRow row=mapper.lock(id);
            if (row==null || !"FETCHING_RESULT".equals(row.state) || row.version!=version
                    || row.updatedAt>staleBefore.toEpochMilli()) return Optional.empty();
            row.version++; row.dispatchToken=token; row.updatedAt=now.toEpochMilli(); save(row);
            return Optional.of(converter.job(row));
        });
    }

    /** Dispatched work without a query contract is terminally uncertain after its lease expires. */
    public List<JobRecord> findUncertain(Instant staleBefore,int limit) {
        requireRecoveryArguments(staleBefore,limit);
        return mapper.uncertain(staleBefore.toEpochMilli(),limit).stream().map(converter::job).collect(Collectors.toList());
    }

    public boolean markUncertainUnknown(String id,long version,String token,Instant staleBefore,Instant now) {
        if (staleBefore==null || now==null || staleBefore.isAfter(now)) throw new IllegalArgumentException("Invalid recovery time");
        return Boolean.TRUE.equals(transaction.execute(status -> {
            JobRow row=mapper.lock(id);
            if (row==null || row.version!=version || token==null || !token.equals(row.dispatchToken)
                    || row.updatedAt>staleBefore.toEpochMilli()
                    || !("DISPATCHING".equals(row.state) || "WAITING".equals(row.state))) return false;
            JobRecord old=converter.job(row);
            JobUpdate update=new JobUpdate(JobState.UNKNOWN,null,null,null,null,
                    new JobError(ErrorCode.RESULT_UNKNOWN,"Provider execution cannot be confirmed after restart",
                            old.getRequest().isSimulated()),
                    UnknownOperationReason.PROVIDER_QUERY_UNAVAILABLE,now);
            if (!rules.allows(old,update,codec)) return false;
            converter.update(row,update); save(row); return true;
        }));
    }

    public Optional<JobRecord> claimPending(String id, long version, String token, Instant now) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid claim token");
        return transaction.execute(status -> {
            capacity();
            JobRow row=mapper.lock(id);
            if (row == null || !"PENDING".equals(row.state) || row.version != version || mapper.activeCount() >= maxActive)
                return Optional.empty();
            row.state="DISPATCHING"; row.version++; row.dispatchToken=token; row.updatedAt=now.toEpochMilli();
            save(row);
            return Optional.of(converter.job(row));
        });
    }

    public boolean updateClaimed(String id, long version, String token, JobUpdate update) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            JobRow row=mapper.lock(id);
            if (row == null || token == null || !token.equals(row.dispatchToken) || version != row.version) return false;
            if (!rules.allows(converter.job(row),update,codec)) return false;
            if (update.getState() == JobState.SUCCEEDED) {
                List<String> ids=new ArrayList<>();
                if (update.getResult()!=null) ids.addAll(update.getResult().getArtifactIds());
                if (update.getVideoResult()!=null) {
                    ids.addAll(update.getVideoResult().getSnapshotAssetIds());
                    if (update.getVideoResult().getAnnotatedVideoAssetId()!=null)
                        ids.add(update.getVideoResult().getAnnotatedVideoAssetId());
                }
                for (String assetId : ids)
                    if (assets.findOwned(assetId,row.ownerId) == null) return false;
            }
            converter.update(row,update); save(row);
            return true;
        }));
    }

    public Optional<JobRecord> cancelPending(String id, String owner, long version, Instant now) {
        return transaction.execute(status -> {
            JobRow row=mapper.lock(id);
            if (row == null || !row.ownerId.equals(owner) || row.version != version || !"PENDING".equals(row.state))
                return Optional.empty();
            row.state="CANCELLED"; row.version++; row.updatedAt=now.toEpochMilli(); save(row);
            return Optional.of(converter.job(row));
        });
    }

    public int pendingCount() { return mapper.pendingCount(); }
    public int activeCount() { return mapper.activeCount(); }

    private void requireRecoveryArguments(Instant staleBefore,int limit) {
        if (staleBefore==null || limit<1 || limit>100) throw new IllegalArgumentException("Invalid recovery candidates");
    }

    private void save(JobRow row) {
        if (mapper.update(row) != 1) throw new IllegalStateException("AI state update lost its version");
        mapper.event(row);
    }

    public JobPage listOwned(String owner, JobState state, String cursor, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid history limit");
        Long beforeTime=null; String beforeId=null;
        if (cursor != null) {
            if (cursor.length() > 512) throw new IllegalArgumentException("Invalid cursor");
            try {
                String[] fields=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split(":",-1);
                if (fields.length != 2 || !fields[1].matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException();
                beforeTime=Long.valueOf(fields[0]); beforeId=fields[1];
            } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid cursor"); }
        }
        List<JobRow> rows=mapper.history(owner,state == null ? null : state.name(),beforeTime,beforeId,limit+1);
        String next=null;
        if (rows.size() > limit) {
            rows=new ArrayList<>(rows.subList(0,limit));
            JobRow last=rows.get(rows.size()-1);
            next=Base64.getUrlEncoder().withoutPadding().encodeToString((last.createdAt+":"+last.requestId).getBytes(StandardCharsets.UTF_8));
        }
        return new JobPage(rows.stream().map(converter::job).collect(Collectors.toList()),next);
    }
}
