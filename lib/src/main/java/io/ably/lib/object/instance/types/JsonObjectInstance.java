package io.ably.lib.object.instance.types;

import com.google.gson.JsonObject;
import io.ably.lib.object.instance.LiveObjectInstance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link LiveObjectInstance} bound to a {@link JsonObject} primitive value.
 *
 * <p>{@link #getId()} always returns {@code null} for primitive instances, and
 * subscribe operations are not supported.
 */
public interface JsonObjectInstance extends LiveObjectInstance {

    /**
     * Returns the wrapped JSON object.
     *
     * <p>Spec: RTINS4
     *
     * @return the wrapped JsonObject value
     */
    @NotNull
    JsonObject value();
}
