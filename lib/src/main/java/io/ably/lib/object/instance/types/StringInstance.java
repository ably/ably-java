package io.ably.lib.object.instance.types;

import io.ably.lib.object.instance.LiveObjectInstance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link LiveObjectInstance} bound to a {@code String} primitive value.
 *
 * <p>{@link #getId()} always returns {@code null} for primitive instances, and
 * subscribe operations are not supported.
 */
public interface StringInstance extends LiveObjectInstance {

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
