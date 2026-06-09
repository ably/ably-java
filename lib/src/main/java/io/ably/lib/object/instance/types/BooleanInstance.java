package io.ably.lib.object.instance.types;

import io.ably.lib.object.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a {@code Boolean} primitive value.
 * Primitive instances are anonymous (no object id) and do not support subscribe.
 */
public interface BooleanInstance extends Instance {

    /**
     * Returns the wrapped boolean.
     *
     * <p>Spec: RTINS4
     *
     * @return the wrapped boolean value
     */
    @NotNull
    Boolean value();
}
