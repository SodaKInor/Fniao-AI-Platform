package org.jeecg.modules.ai.stream.persistence.repository;

import java.util.*;
import java.util.stream.Collectors;
import org.jeecg.modules.ai.stream.domain.StreamSource;
import org.jeecg.modules.ai.stream.port.StreamSourceRepository;
import org.jeecg.modules.ai.stream.persistence.converter.StreamRecordConverter;
import org.jeecg.modules.ai.stream.persistence.mapper.StreamSourceMapper;

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
