package org.jeecg.modules.ai.persistence.repository;

import java.util.*;
import java.util.stream.Collectors;
import org.jeecg.modules.ai.domain.StreamSource;
import org.jeecg.modules.ai.port.StreamSourceRepository;
import org.jeecg.modules.ai.persistence.converter.StreamRecordConverter;
import org.jeecg.modules.ai.persistence.mapper.StreamSourceMapper;

public final class MyBatisStreamSourceRepository implements StreamSourceRepository {
    private final StreamSourceMapper mapper;
    private final StreamRecordConverter converter;
    public MyBatisStreamSourceRepository(StreamSourceMapper mapper,StreamRecordConverter converter) {
        this.mapper=mapper; this.converter=converter;
    }
    public List<StreamSource> listOwned(String owner) {
        return mapper.listOwned(owner).stream().map(converter::source).collect(Collectors.toList());
    }
    public Optional<StreamSource> findOwned(String id,String owner) {
        return Optional.ofNullable(mapper.findOwned(id,owner)).map(converter::source);
    }
}
