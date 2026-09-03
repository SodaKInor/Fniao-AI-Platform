package org.jeecg.modules.ai.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stable owner-scoped history ordered by createdAt DESC then requestId DESC. Null cursor ends page.
 */
public final class JobPage {
    private final List<JobRecord> items;
    private final String nextCursor;

    public JobPage(
            List<JobRecord> items,
            String nextCursor) {
        this.items = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(items, "items")));
        this.nextCursor = nextCursor;
    }

    public List<JobRecord> getItems() {
        return items;
    }

    public String getNextCursor() {
        return nextCursor;
    }
}
