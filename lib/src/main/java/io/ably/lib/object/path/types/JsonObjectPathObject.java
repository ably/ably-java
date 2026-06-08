package io.ably.lib.object.path.types;

import com.google.gson.JsonObject;
import io.ably.lib.object.path.PathObject;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link PathObject} whose underlying value is expected to be a {@link JsonObject}.
 *
 * <p>This is a terminal type. {@link PathObject#at(String)} remains purely
 * navigational and will return a new {@link PathObject} whose later read/write
 * operations fail to resolve. {@link PathObject#instance()} returns {@code null}
 * because a primitive resolution does not produce a wrapped LiveObject.
 * Only {@link #value()} and the inherited read APIs are useful here.
 */
public interface JsonObjectPathObject extends PathObject {

    /**
     * Returns the JSON object at this path, or {@code null} when the path does not
     * resolve or resolves to a non-JsonObject value.
     *
     * <p>Spec: RTPO7
     *
     * @return the resolved JsonObject, or {@code null}
     */
    @Nullable
    JsonObject value();
}
