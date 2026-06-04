package io.ably.lib.objects.path;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * The single root of a channel's LiveObjects tree. Always backed by a
 * LiveMap, so {@link #instance()} is statically typed and never returns
 * {@code null}.
 * <p>
 * This is the only place in the path API where compile-time typing is
 * encoded on a {@link PathObject}, because the root's type is fixed by the
 * spec.
 */
@ApiStatus.NonExtendable
public interface RootPathObject extends PathObject {

    /** Statically known to be a LiveMap at the root. Non-blocking. */
    @Override
    @NotNull
    @Contract(pure = true)
    LiveMapInstance instance();
}
