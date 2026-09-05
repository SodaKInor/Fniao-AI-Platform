package org.jeecg.modules.ai.stream.persistence.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.ai.stream.persistence.entity.StreamSessionRow;

public interface StreamSessionMapper {
    String COLUMNS = "session_id AS sessionId,owner_id AS ownerId,idempotency_key AS idempotencyKey,"
            + "request_digest AS requestDigest,request_json AS requestJson,stream_source_id AS streamSourceId,"
            + "state,version,dispatch_token AS dispatchToken,provider_session_id AS providerSessionId,"
            + "provider_cursor AS providerCursor,unknown_reason AS unknownReason,error_json AS errorJson,"
            + "created_at AS createdAt,updated_at AS updatedAt";

    @Select("SELECT id FROM ai_job_capacity WHERE id=1 FOR UPDATE")
    Integer lockCapacity();

    @Select("SELECT COUNT(*) FROM ai_stream_session WHERE state='PENDING'")
    int pendingCount();

    @Select("SELECT COUNT(*) FROM ai_stream_session WHERE state IN ('STARTING','RUNNING','STOP_REQUESTED')")
    int activeCount();

    @Select("SELECT " + COLUMNS + " FROM ai_stream_session WHERE owner_id=#{owner} AND idempotency_key=#{key}")
    StreamSessionRow findByKey(@Param("owner") String owner, @Param("key") String key);

    @Select("SELECT " + COLUMNS + " FROM ai_stream_session WHERE owner_id=#{owner} AND session_id=#{id}")
    StreamSessionRow findOwned(@Param("id") String id, @Param("owner") String owner);

    @Select("SELECT " + COLUMNS + " FROM ai_stream_session WHERE session_id=#{id} FOR UPDATE")
    StreamSessionRow lock(String id);

    @Select("SELECT " + COLUMNS + " FROM ai_stream_session WHERE state='PENDING' ORDER BY created_at,session_id LIMIT #{limit}")
    List<StreamSessionRow> pending(int limit);

    @Select("SELECT " + COLUMNS + " FROM ai_stream_session WHERE state IN ('STARTING','RUNNING','STOP_REQUESTED') "
            + "ORDER BY updated_at,session_id LIMIT #{limit}")
    List<StreamSessionRow> recoverable(int limit);

    @Insert("INSERT INTO ai_stream_session(session_id,owner_id,idempotency_key,request_digest,request_json,stream_source_id,"
            + "state,version,created_at,updated_at) VALUES(#{sessionId},#{ownerId},#{idempotencyKey},#{requestDigest},"
            + "#{requestJson},#{streamSourceId},'PENDING',0,#{createdAt},#{updatedAt})")
    void insert(StreamSessionRow row);

    @Update("UPDATE ai_stream_session SET state=#{state},version=#{version},dispatch_token=#{dispatchToken},"
            + "provider_session_id=#{providerSessionId},provider_cursor=#{providerCursor},unknown_reason=#{unknownReason},"
            + "error_json=#{errorJson},updated_at=#{updatedAt} WHERE session_id=#{sessionId} AND version=#{version}-1")
    int update(StreamSessionRow row);
}
