package org.jeecg.modules.ai.persistence.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.ai.persistence.entity.CapabilityRow;

public interface CapabilityMapper {
    @Select("SELECT capability_code AS capabilityCode,descriptor_json AS descriptorJson FROM ai_capability_binding WHERE capability_code=#{code}")
    CapabilityRow find(String code);

    @Select("SELECT capability_code AS capabilityCode,descriptor_json AS descriptorJson FROM ai_capability_binding ORDER BY capability_code")
    List<CapabilityRow> list();
}
