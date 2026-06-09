package io.ably.lib.object.instance.types;

import com.google.gson.JsonArray;
import io.ably.lib.object.instance.Instance;
import org.jetbrains.annotations.NotNull;

/**
 * A read-only {@link Instance} bound to a {@link JsonArray} primitive value.
 * Primitive instances are anonymous (no object id) and do not support subscribe.
 */
public interface JsonArrayInstance extends Instance {

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
