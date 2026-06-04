package io.ably.lib.objects.path;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Payload delivered to {@link PathChangeListener} when a path subscription
 * fires.
 * <p>
 * Per @sacOO7's review note on dynamic types: read fresh values from
 * {@link #object()} (a {@link PathObject}) rather than holding a typed
 * accessor across event invocations, because the type at a path may change
 * between events.
 */
@ApiStatus.NonExtendable
public interface PathChangeEvent {

    /** The PathObject at which the change occurred. */
    @NotNull
    PathObject object();

    /** The wire message that caused this change. */
    @NotNull
    ObjectMessage message();
}
