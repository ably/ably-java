package io.ably.lib.object.instance.types;

import com.google.gson.JsonObject;
import io.ably.lib.object.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a {@link JsonObject} primitive value.
 * Primitive instances are anonymous (no object id) and do not support subscribe.
 */
public interface JsonObjectInstance extends Instance {

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
