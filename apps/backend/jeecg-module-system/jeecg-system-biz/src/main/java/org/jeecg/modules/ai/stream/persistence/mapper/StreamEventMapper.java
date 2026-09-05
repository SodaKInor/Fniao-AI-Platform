package org.jeecg.modules.ai.stream.persistence.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.ai.stream.persistence.entity.StreamEventRow;

public interface StreamEventMapper {
    String COLUMNS = "session_id AS sessionId,provider_event_id AS providerEventId,event_id AS eventId,"
            + "offset_millis AS offsetMillis,occurred_at AS occurredAt,event_type AS eventType,score,"
            + "snapshot_asset_id AS snapshotAssetId";

    @Select({"<script>SELECT e.session_id AS sessionId,e.provider_event_id AS providerEventId,"
            + "e.event_id AS eventId,e.offset_millis AS offsetMillis,e.occurred_at AS occurredAt,"
            + "e.event_type AS eventType,e.score,e.snapshot_asset_id AS snapshotAssetId FROM ai_stream_event e "
            + "JOIN ai_stream_session s ON s.session_id=e.session_id WHERE e.session_id=#{session} AND s.owner_id=#{owner}",
            "<if test='beforeId != null'> AND (e.offset_millis &gt; #{beforeOffset} OR "
                    + "(e.offset_millis=#{beforeOffset} AND e.event_id &gt; #{beforeId}))</if>",
            "ORDER BY e.offset_millis,e.event_id LIMIT #{limit}</script>"})
    List<StreamEventRow> page(@Param("session") String session, @Param("owner") String owner,
            @Param("beforeOffset") Long beforeOffset, @Param("beforeId") String beforeId,
            @Param("limit") int limit);

    @Insert("INSERT IGNORE INTO ai_stream_event(session_id,provider_event_id,event_id,offset_millis,occurred_at,event_type,score,snapshot_asset_id) "
            + "VALUES(#{sessionId},#{providerEventId},#{eventId},#{offsetMillis},#{occurredAt},#{eventType},#{score},#{snapshotAssetId})")
    int insertIgnore(StreamEventRow row);
}
