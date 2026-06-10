package io.ably.lib.object.path.types;

import com.google.gson.JsonArray;
import io.ably.lib.object.path.PathObject;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link PathObject} whose underlying value is expected to be a {@link JsonArray}.
 *
 * <p>This is a terminal type. {@link PathObject#instance()} returns {@code null}
 * because a primitive resolution does not produce a wrapped LiveObject; navigation
 * via {@code at(...)} is not available here because it is only defined on
 * {@code LiveMapPathObject}. Only {@link #value()} and the inherited read APIs are
 * useful here.
 *
 * <p>Spec: RTTS6c
 */
public interface JsonArrayPathObject extends PathObject {

    /**
     * Returns the JSON array at this path, or {@code null} when the path does not
     * resolve or resolves to a non-JsonArray value.
     *
     * <p>Spec: RTPO7 / RTTS6c
     *
     * @return the resolved JsonArray, or {@code null}
     */
    @Nullable
    JsonArray value();
}
