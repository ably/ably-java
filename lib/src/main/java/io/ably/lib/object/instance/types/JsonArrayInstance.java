package io.ably.lib.object.instance.types;

import com.google.gson.JsonArray;
import io.ably.lib.object.instance.LiveObjectInstance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link LiveObjectInstance} bound to a {@link JsonArray} primitive value.
 *
 * <p>{@link #getId()} always returns {@code null} for primitive instances, and
 * subscribe operations are not supported.
 */
public interface JsonArrayInstance extends LiveObjectInstance {

    /**
     * Returns the wrapped JSON array.
     *
     * <p>Spec: RTINS4
     *
     * @return the wrapped JsonArray value
     */
    @NotNull
    JsonArray value();
}
