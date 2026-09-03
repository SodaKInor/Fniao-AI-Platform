package org.jeecg.modules.ai.api.mapper.assets;

import org.jeecg.modules.ai.api.dto.AssetDto;
import org.jeecg.modules.ai.domain.Asset;

public final class AssetDtoMapper {
    public AssetDto map(Asset a) {
        AssetDto d=new AssetDto();
        d.setAssetId(a.getAssetId()); d.setFileName(a.getFileName()); d.setMediaType(a.getMediaType());
        d.setSizeBytes(a.getStored().getSizeBytes()); d.setSha256(a.getStored().getSha256());
        d.setCreatedAt(a.getCreatedAt()); d.setExpiresAt(a.getExpiresAt());
        return d;
    }
}
