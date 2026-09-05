package org.jeecg.modules.ai.job.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jeecg.modules.ai.job.domain.IdempotencyConflictException;
import org.jeecg.modules.ai.job.domain.JobPage;
import org.jeecg.modules.ai.job.domain.JobRecord;
import org.jeecg.modules.ai.job.domain.JobRequest;
import org.jeecg.modules.ai.job.domain.JobState;
import org.jeecg.modules.ai.job.domain.JobSubmission;
import org.jeecg.modules.ai.job.domain.JobUpdate;

/**
 * Durable atomic task boundary; detailed transaction/state obligations in v1/SEMANTICS.md.
 * createOrGet uses a database-unique (ownerId,idempotencyKey), comparing the persisted request digest.
 * claimPending atomically changes PENDING to DISPATCHING, stores a nonempty unique claim token,
 * and increments version. Only its winner may invoke the provider; never hold a DB lock across I/O.
 * updateClaimed checks token AND version AND legal transition; increments version on success.
 * A false result loses the race; it is never permission to dispatch again.
 * cancelPending competes atomically with claimPending; empty means not pending/owned/version-matched.
 * findPending is bounded and is a candidate scan only; it grants no dispatch rights.
 * Owner queries do not disclose other users. Final states and UNKNOWN cannot be replayed.
 * Recovery and optional remote operations require a later explicit contract change, not local signatures.
 */
public interface JobRepository {
    JobSubmission createOrGet(JobRequest request) throws IdempotencyConflictException;

    /** Read-only fast path; createOrGet still resolves all concurrent submission races. */
    Optional<JobRecord> findByKeyOwned(String ownerId, String idempotencyKey);

    Optional<JobRecord> findOwned(String requestId, String ownerId);

    JobPage listOwned(String ownerId, JobState state, String cursor, int limit);

    List<JobRecord> findPending(int limit);

    Optional<JobRecord> claimPending(String requestId, long expectedVersion,
                                    String dispatchToken, Instant now);

    boolean updateClaimed(String requestId, long expectedVersion, String dispatchToken, JobUpdate update);

    Optional<JobRecord> cancelPending(String requestId, String ownerId, long expectedVersion, Instant now);
}
