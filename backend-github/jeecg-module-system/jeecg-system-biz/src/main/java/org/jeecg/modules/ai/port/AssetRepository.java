package org.jeecg.modules.ai.port;

import java.util.Optional;
import org.jeecg.modules.ai.domain.Asset;

/**
 * insert is atomic with a unique opaque assetId; existing data must never be replaced.
 * findOwned returns empty for missing OR other-owner IDs. Caller also checks expiry.
 * Only complete stored files may be referenced. Repository does not open files or call providers.
 */
public interface AssetRepository {
    void insert(Asset asset);

    Optional<Asset> findOwned(String assetId, String ownerId);
}
