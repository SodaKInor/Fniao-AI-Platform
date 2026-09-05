package org.jeecg.modules.ai.stream.persistence.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.ai.stream.persistence.entity.StreamSourceRow;

public interface StreamSourceMapper {
    String COLUMNS = "stream_source_id AS streamSourceId,owner_id AS ownerId,display_name AS displayName,"
            + "provider_source_ref AS providerSourceRef,enabled,unavailable_reason AS unavailableReason,"
            + "created_at AS createdAt,updated_at AS updatedAt";

    @Select("SELECT " + COLUMNS + " FROM ai_stream_source WHERE owner_id=#{owner} ORDER BY display_name,stream_source_id")
    List<StreamSourceRow> listOwned(String owner);

    @Select("SELECT " + COLUMNS + " FROM ai_stream_source WHERE owner_id=#{owner} AND stream_source_id=#{id}")
    StreamSourceRow findOwned(@Param("id") String id, @Param("owner") String owner);
}
