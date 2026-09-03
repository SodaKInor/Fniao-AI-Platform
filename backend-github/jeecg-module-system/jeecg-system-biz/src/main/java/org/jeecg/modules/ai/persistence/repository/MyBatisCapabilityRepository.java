package org.jeecg.modules.ai.persistence.repository;

import java.util.*;
import java.util.stream.Collectors;
import org.jeecg.modules.ai.domain.Capability;
import org.jeecg.modules.ai.port.CapabilityRepository;
import org.jeecg.modules.ai.persistence.converter.SnapshotCodec;
import org.jeecg.modules.ai.persistence.mapper.CapabilityMapper;

public final class MyBatisCapabilityRepository implements CapabilityRepository {
    private final CapabilityMapper mapper;
    private final SnapshotCodec codec;
    public MyBatisCapabilityRepository(CapabilityMapper mapper, SnapshotCodec codec) {
        this.mapper=mapper; this.codec=codec;
    }
    public Optional<Capability> find(String code) {
        return Optional.ofNullable(mapper.find(code)).map(r -> codec.capability(r.descriptorJson));
    }
    public List<Capability> list() {
        return mapper.list().stream().map(r -> codec.capability(r.descriptorJson)).collect(Collectors.toList());
    }
}
