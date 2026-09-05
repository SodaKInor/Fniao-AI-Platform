package org.jeecg.modules.ai.stream.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Stable local event page; an empty items list is a successful query. */
public final class StreamEventPage {
    private final String sessionId;
    private final List<StreamEvent> items;
    private final String nextCursor;

    public StreamEventPage(String sessionId, List<StreamEvent> items, String nextCursor) {
        this.sessionId = sessionId;
        this.items = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(items, "items")));
        this.nextCursor = nextCursor;
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<StreamEvent> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
