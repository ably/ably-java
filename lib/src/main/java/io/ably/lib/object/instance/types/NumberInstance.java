package io.ably.lib.object.instance.types;

import io.ably.lib.object.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a {@code Number} primitive value.
 * Primitive instances are anonymous (no object id) and do not support subscribe.
 */
public interface NumberInstance extends Instance {

    /**
     * Returns the wrapped number.
     *
     * <p>Spec: RTINS4
     *
     * @return the wrapped numeric value
     */
    @NotNull
    Number value();
}
