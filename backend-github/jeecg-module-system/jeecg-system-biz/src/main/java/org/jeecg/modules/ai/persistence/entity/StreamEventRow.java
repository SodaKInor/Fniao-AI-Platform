package org.jeecg.modules.ai.persistence.entity;

import java.math.BigDecimal;

public final class StreamEventRow {
    public String sessionId;
    public String providerEventId;
    public String eventId;
    public long offsetMillis;
    public long occurredAt;
    public String eventType;
    public BigDecimal score;
    public String snapshotAssetId;
}
