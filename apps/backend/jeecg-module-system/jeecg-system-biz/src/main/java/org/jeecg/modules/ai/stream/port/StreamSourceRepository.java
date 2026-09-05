package org.jeecg.modules.ai.stream.port;

import java.util.List;
import java.util.Optional;
import org.jeecg.modules.ai.stream.domain.StreamSource;

/** Authorized local source lookup; other-user and missing sources are indistinguishable to callers. */
public interface StreamSourceRepository {
    List<StreamSource> listOwned(String ownerId);

    Optional<StreamSource> findOwned(String streamSourceId, String ownerId);
}
