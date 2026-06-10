package io.ably.lib.object.instance.types;

import io.ably.lib.object.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a {@code Number} primitive value.
 * Primitive instances are anonymous (no object id) and deliberately do not expose
 * {@code subscribe}, {@code set}, {@code remove} or any other id/iteration/write
 * methods - only {@code value()} - per RTTS10c.
 *
 * <p>Spec: RTTS10c
 */
public interface NumberInstance extends Instance {

    /**
     * Returns the wrapped number.
     *
     * <p>Spec: RTINS4 / RTTS10c
     *
     * @return the wrapped numeric value
     */
    @NotNull
    Number value();
}
