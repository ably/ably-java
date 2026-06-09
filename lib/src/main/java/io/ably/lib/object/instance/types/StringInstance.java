package io.ably.lib.object.instance.types;

import io.ably.lib.object.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a {@code String} primitive value.
 * Primitive instances are anonymous (no object id) and do not support subscribe.
 */
public interface StringInstance extends Instance {

    /**
     * Returns the wrapped string.
     *
     * <p>Spec: RTINS4
     *
     * @return the wrapped string value
     */
    @NotNull
    String value();
}
