package org.jeecg.modules.ai.stream.port;

import java.time.Instant;
import java.util.List;
import org.jeecg.modules.ai.stream.domain.StreamEvent;
import org.jeecg.modules.ai.stream.domain.StreamEventPage;

/**
 * Event deduplication and cursor advancement are one atomic persistence operation.
 * appendAndAdvance returns false on version/cursor races or terminal sessions; callers never use
 * a false result to overwrite STOPPED, FAILED or UNKNOWN.
 */
public interface StreamEventRepository {
    StreamEventPage listOwned(String sessionId, String ownerId, String cursor, int limit);

    boolean appendAndAdvance(
            String sessionId,
            long expectedSessionVersion,
            String expectedCursor,
            List<StreamEvent> events,
            String nextCursor,
            Instant now);
}
