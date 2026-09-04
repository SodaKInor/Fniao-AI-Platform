package org.jeecg.modules.ai.persistence.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.jeecg.modules.ai.persistence.entity.JobRow;

public interface JobMapper {
    String COLUMNS = "request_id AS requestId,owner_id AS ownerId,idempotency_key AS idempotencyKey,"
            + "request_digest AS requestDigest,request_json AS requestJson,state,version,dispatch_token AS dispatchToken,"
            + "checkpoint_json AS checkpointJson,result_json AS resultJson,error_json AS errorJson,"
            + "created_at AS createdAt,updated_at AS updatedAt";

    @Select("SELECT id FROM ai_job_capacity WHERE id=1 FOR UPDATE")
    Integer lockCapacity();

    @Select("SELECT COUNT(*) FROM ai_job WHERE state='PENDING'")
    int pendingCount();

    @Select("SELECT COUNT(*) FROM ai_job WHERE state IN ('DISPATCHING','WAITING','FETCHING_RESULT')")
    int activeCount();

    @Select("SELECT " + COLUMNS + " FROM ai_job WHERE owner_id=#{owner} AND idempotency_key=#{key}")
    JobRow findByKey(@Param("owner") String owner, @Param("key") String key);

    @Select("SELECT " + COLUMNS + " FROM ai_job WHERE owner_id=#{owner} AND request_id=#{id}")
    JobRow findOwned(@Param("id") String id, @Param("owner") String owner);

    @Select("SELECT " + COLUMNS + " FROM ai_job WHERE request_id=#{id} FOR UPDATE")
    JobRow lock(String id);

    @Select("SELECT " + COLUMNS + " FROM ai_job WHERE state='PENDING' ORDER BY created_at,request_id LIMIT #{limit}")
    List<JobRow> pending(int limit);

    @Select("SELECT " + COLUMNS + " FROM ai_job WHERE state='FETCHING_RESULT' ORDER BY updated_at,request_id LIMIT #{limit}")
    List<JobRow> fetching(int limit);

    @Select({"<script>SELECT " + COLUMNS + " FROM ai_job WHERE owner_id=#{owner}",
            "<if test='state != null'> AND state=#{state}</if>",
            "<if test='beforeId != null'> AND (created_at &lt; #{beforeTime} OR (created_at=#{beforeTime} AND request_id &lt; #{beforeId}))</if>",
            "ORDER BY created_at DESC,request_id DESC LIMIT #{limit}</script>"})
    List<JobRow> history(@Param("owner") String owner, @Param("state") String state,
                        @Param("beforeTime") Long beforeTime, @Param("beforeId") String beforeId,
                        @Param("limit") int limit);

    @Insert("INSERT INTO ai_job(request_id,owner_id,idempotency_key,request_digest,request_json,state,version,created_at,updated_at) "
            + "VALUES(#{requestId},#{ownerId},#{idempotencyKey},#{requestDigest},#{requestJson},'PENDING',0,#{createdAt},#{updatedAt})")
    void insert(JobRow row);

    @Update("UPDATE ai_job SET state=#{state},version=#{version},dispatch_token=#{dispatchToken},"
            + "checkpoint_json=#{checkpointJson},result_json=#{resultJson},error_json=#{errorJson},updated_at=#{updatedAt} "
            + "WHERE request_id=#{requestId} AND version=#{version}-1")
    int update(JobRow row);

    @Insert("INSERT INTO ai_job_event(request_id,version,state,occurred_at) VALUES(#{requestId},#{version},#{state},#{updatedAt})")
    void event(JobRow row);
}
