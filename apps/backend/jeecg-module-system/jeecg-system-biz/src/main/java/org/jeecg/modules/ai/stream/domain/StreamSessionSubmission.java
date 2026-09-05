package org.jeecg.modules.ai.stream.domain;

/** Atomic stream create-or-return result. */
public final class StreamSessionSubmission {
    private final StreamSession session;
    private final boolean created;

    public StreamSessionSubmission(StreamSession session, boolean created) {
        this.session = session;
        this.created = created;
    }

    public StreamSession getSession() {
        return session;
    }

    public boolean isCreated() {
        return created;
    }
}
