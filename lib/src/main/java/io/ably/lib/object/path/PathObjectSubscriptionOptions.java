package io.ably.lib.object.path;

import org.jetbrains.annotations.Nullable;

/**
 * Optional subscription options accepted by
 * {@link PathObject#subscribe(PathObjectListener, PathObjectSubscriptionOptions)}.
 *
 * <p>Spec: RTPO19c
 */
public final class PathObjectSubscriptionOptions {

    private final Integer depth;

    /**
     * Creates options with the given {@code depth}.
     *
     * @param depth how many levels of path nesting below the subscribed path should
     *              trigger the listener; must be a positive integer if provided
     */
    public PathObjectSubscriptionOptions(@Nullable Integer depth) {
        this.depth = depth;
    }

    /**
     * Returns the configured nesting depth, or {@code null} if not set.
     *
     * <p>Spec: RTPO19c1
     *
     * @return the depth value, or {@code null}
     */
    @Nullable
    public Integer getDepth() {
        return depth;
    }
}
