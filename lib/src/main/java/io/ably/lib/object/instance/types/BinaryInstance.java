package io.ably.lib.object.instance.types;

import io.ably.lib.object.instance.LiveObjectInstance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link LiveObjectInstance} bound to a binary primitive value
 * (a {@code byte[]}).
 *
 * <p>{@link #getId()} always returns {@code null} for primitive instances, and
 * subscribe operations are not supported.
 */
public interface BinaryInstance extends LiveObjectInstance {

    /**
     * Returns the wrapped binary value.
     *
     * <p>Spec: RTINS4
     *
     * @return the wrapped bytes
     */
    byte @NotNull [] value();
}
