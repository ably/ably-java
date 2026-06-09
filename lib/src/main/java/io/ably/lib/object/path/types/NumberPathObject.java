package io.ably.lib.object.path.types;

import io.ably.lib.object.path.PathObject;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link PathObject} whose underlying value is expected to be a {@code Number}.
 *
 * <p>This is a terminal type. {@link PathObject#instance()} returns {@code null}
 * because a primitive resolution does not produce a wrapped LiveObject; navigation
 * via {@code at(...)} is not available here because it is only defined on
 * {@code LiveMapPathObject}. Only {@link #value()} and the inherited read APIs are
 * useful here.
 */
public interface NumberPathObject extends PathObject {

    /**
     * Returns the number at this path, or {@code null} when the path does not resolve
     * or resolves to a non-numeric value.
     *
     * <p>Spec: RTPO7
     *
     * @return the resolved number, or {@code null}
     */
    @Nullable
    Number value();
}
