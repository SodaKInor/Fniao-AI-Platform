package org.jeecg.modules.ai.application.streams;

import java.util.List;
import org.jeecg.modules.ai.application.jobs.AiRequestException;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.*;

public final class StreamQueryService {
    private final StreamSourceRepository sources;
    private final StreamSessionRepository sessions;
    private final StreamEventRepository events;
    public StreamQueryService(StreamSourceRepository sources,StreamSessionRepository sessions,StreamEventRepository events) {
        this.sources=sources; this.sessions=sessions; this.events=events;
    }
    public List<StreamSource> sources(String owner) { return sources.listOwned(owner); }
    public StreamSession owned(String id,String owner) {
        new StreamRequestFingerprint().id(id);
        return sessions.findOwned(id,owner)
                .orElseThrow(() -> new AiRequestException(ErrorCode.NOT_FOUND,"Stream session not found"));
    }
    public StreamEventPage events(String id,String owner,String cursor,int limit) {
        owned(id,owner); return events.listOwned(id,owner,cursor,limit);
    }
    public static boolean finished(StreamSession session) {
        return session.getState()==StreamSessionState.STOPPED || session.getState()==StreamSessionState.FAILED
                || session.getState()==StreamSessionState.UNKNOWN;
    }
}
