package io.ably.lib.object.instance.types;

import io.ably.lib.object.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a binary primitive value
 * (a {@code byte[]}). Primitive instances are anonymous (no object id) and do not
 * support subscribe.
 */
public interface BinaryInstance extends Instance {

    /**
     * Returns the wrapped binary value.
     *
     * <p>Spec: RTINS4
     *
     * @return the wrapped bytes
     */
    byte @NotNull [] value();
}
