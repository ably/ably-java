package io.ably.lib.objects.path;

import org.jetbrains.annotations.NotNull;

/**
 * Options for {@link PathObject#subscribe(PathChangeListener, PathSubscriptionOptions)}.
 * <p>
 * Currently exposes a single dimension — subscription depth. {@code depth = -1}
 * means unlimited (default). {@code depth = N} (N &gt;= 0) limits the
 * subscription to changes whose path is at most N segments deeper than
 * the subscribed path.
 */
public final class PathSubscriptionOptions {

    public static final int UNLIMITED = -1;

    private final int depth;

    private PathSubscriptionOptions(int depth) {
        if (depth < UNLIMITED) {
            throw new IllegalArgumentException("depth must be -1 (unlimited) or >= 0");
        }
        this.depth = depth;
    }

    public int depth() {
        return depth;
    }

    /** Subscribe to all changes at and below this path. */
    @NotNull
    public static PathSubscriptionOptions unlimited() {
        return new PathSubscriptionOptions(UNLIMITED);
    }

    /** Subscribe to changes within the given depth. {@code depth(0)} = self only. */
    @NotNull
    public static PathSubscriptionOptions depth(int depth) {
        return new PathSubscriptionOptions(depth);
    }

    @Override
    public String toString() {
        return "PathSubscriptionOptions{depth=" + (depth == UNLIMITED ? "unlimited" : depth) + "}";
    }
}
