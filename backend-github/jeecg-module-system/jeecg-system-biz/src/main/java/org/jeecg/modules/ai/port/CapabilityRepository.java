package org.jeecg.modules.ai.port;

import java.util.List;
import java.util.Optional;
import org.jeecg.modules.ai.domain.Capability;

/**
 * Read local bindings, never call the remote provider. Return disabled/unavailable descriptors too.
 * Application rejects new calls when disabled or unconfirmed; snapshot existing jobs before dispatch.
 */
public interface CapabilityRepository {
    Optional<Capability> find(String capabilityCode);

    List<Capability> list();
}
