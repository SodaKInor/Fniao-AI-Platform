package org.jeecg.modules.ai.persistence.entity;

public final class StreamSessionRow {
    public String sessionId;
    public String ownerId;
    public String idempotencyKey;
    public String requestDigest;
    public String requestJson;
    public String streamSourceId;
    public String state;
    public long version;
    public String dispatchToken;
    public String providerSessionId;
    public String providerCursor;
    public String unknownReason;
    public String errorJson;
    public long createdAt;
    public long updatedAt;
}
