package org.jeecg.modules.ai.persistence.entity;

/** Indexed identity and state, with explicitly converted immutable snapshots. */
public class JobRow {
    public String requestId;
    public String ownerId;
    public String idempotencyKey;
    public String requestDigest;
    public String requestJson;
    public String state;
    public long version;
    public String dispatchToken;
    public String checkpointJson;
    public String resultJson;
    public String errorJson;
    public long createdAt;
    public long updatedAt;
}
