package org.jeecg.modules.ai.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jeecg.modules.ai.domain.IdempotencyConflictException;
import org.jeecg.modules.ai.domain.StreamSession;
import org.jeecg.modules.ai.domain.StreamSessionRequest;
import org.jeecg.modules.ai.domain.StreamSessionSubmission;
import org.jeecg.modules.ai.domain.StreamSessionUpdate;

/** Durable stream identity and versioned state transitions; no provider calls occur under a DB lock. */
public interface StreamSessionRepository {
    StreamSessionSubmission createOrGet(StreamSessionRequest request)
            throws IdempotencyConflictException;

    Optional<StreamSession> findByKeyOwned(String ownerId, String idempotencyKey);

    Optional<StreamSession> findOwned(String sessionId, String ownerId);

    List<StreamSession> findPending(int limit);

    /** Non-terminal dispatched sessions that require provider reconciliation after restart. */
    List<StreamSession> findRecoverable(int limit);

    Optional<StreamSession> claimPending(
            String sessionId,
            long expectedVersion,
            String dispatchToken,
            Instant now);

    boolean updateClaimed(
            String sessionId,
            long expectedVersion,
            String dispatchToken,
            StreamSessionUpdate update);

    /**
     * Atomically verifies owner/version and records an explicit stop request. PENDING becomes
     * locally STOPPED; a dispatched session becomes STOP_REQUESTED and still requires provider
     * confirmation before STOPPED. Empty means not owned, version mismatch, or already terminal.
     */
    Optional<StreamSession> requestStopOwned(
            String sessionId,
            String ownerId,
            long expectedVersion,
            Instant now);
}
