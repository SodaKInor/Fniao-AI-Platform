package org.jeecg.modules.ai.stream.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Bounded provider page; caller persists events before committing nextCursor. */
public final class ProviderStreamEventPage {
    private final List<ProviderStreamEvent> items;
    private final String nextCursor;

    public ProviderStreamEventPage(List<ProviderStreamEvent> items, String nextCursor) {
        this.items = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(items, "items")));
        this.nextCursor = nextCursor;
    }

    public List<ProviderStreamEvent> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
