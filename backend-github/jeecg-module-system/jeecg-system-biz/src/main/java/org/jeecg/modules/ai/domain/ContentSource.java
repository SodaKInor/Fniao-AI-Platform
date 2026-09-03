package org.jeecg.modules.ai.domain;

import java.io.IOException;
import java.io.InputStream;

/**
 * Lazy content opener. Each successful open transfers stream ownership to its caller.
 * The caller closes it exactly once, including failure/cancellation; unopened sources need no close.
 * Implementations must not require buffering the complete file in memory.
 */
public interface ContentSource {
    InputStream openStream() throws IOException;
}
