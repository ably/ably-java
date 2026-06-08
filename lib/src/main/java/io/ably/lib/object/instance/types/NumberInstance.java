package io.ably.lib.object.instance.types;

import io.ably.lib.object.instance.LiveObjectInstance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link LiveObjectInstance} bound to a {@code Number} primitive value.
 *
 * <p>{@link #getId()} always returns {@code null} for primitive instances, and
 * subscribe operations are not supported.
 */
public interface NumberInstance extends LiveObjectInstance {

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
