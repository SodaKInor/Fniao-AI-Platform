package org.jeecg.modules.ai.persistence.repository;

import java.util.Optional;
import org.jeecg.modules.ai.domain.Asset;
import org.jeecg.modules.ai.port.AssetRepository;
import org.jeecg.modules.ai.persistence.converter.RecordConverter;
import org.jeecg.modules.ai.persistence.mapper.AssetMapper;

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
