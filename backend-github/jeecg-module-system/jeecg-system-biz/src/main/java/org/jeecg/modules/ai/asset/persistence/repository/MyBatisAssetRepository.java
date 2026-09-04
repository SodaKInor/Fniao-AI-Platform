package org.jeecg.modules.ai.asset.persistence.repository;

import java.util.Optional;
import org.jeecg.modules.ai.asset.domain.Asset;
import org.jeecg.modules.ai.asset.port.AssetRepository;
import org.jeecg.modules.ai.job.persistence.converter.RecordConverter;
import org.jeecg.modules.ai.asset.persistence.mapper.AssetMapper;

public final class MyBatisAssetRepository implements AssetRepository {
    private final AssetMapper mapper;
    private final RecordConverter converter;
    public MyBatisAssetRepository(AssetMapper mapper, RecordConverter converter) {
        this.mapper=mapper; this.converter=converter;
    }
    public void insert(Asset asset) { mapper.insert(converter.asset(asset)); }
    public Optional<Asset> findOwned(String id, String owner) {
        return Optional.ofNullable(mapper.findOwned(id,owner)).map(converter::asset);
    }
}
