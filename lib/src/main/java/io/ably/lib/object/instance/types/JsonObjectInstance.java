package io.ably.lib.object.instance.types;

import com.google.gson.JsonObject;
import io.ably.lib.object.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a {@link JsonObject} primitive value.
 * Primitive instances are anonymous (no object id) and deliberately do not expose
 * {@code subscribe}, {@code set}, {@code remove} or any other id/iteration/write
 * methods - only {@code value()} - per RTTS10c.
 *
 * <p>Spec: RTTS10c
 */
public interface JsonObjectInstance extends Instance {

    /**
     * Returns the wrapped JSON object.
     *
     * <p>Spec: RTINS4 / RTTS10c
     *
     * @return the wrapped JsonObject value
     */
    @NotNull
    JsonObject value();
}
