package io.ably.lib.objects.path;

import io.ably.lib.objects.ObjectsSubscription;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

/**
 * Marker for live (collaborative) instances sitting at a path: either a
 * {@link LiveMapInstance} or a {@link LiveCounterInstance}.
 * <p>
 * Logically sealed.
 */
@ApiStatus.NonExtendable
public interface LiveInstance extends LiveValue {

    /**
     * Object ID assigned by the server when the object was created.
     * Example: {@code "counter:J7x6mAF8X5Ha60VBZb6GtXSgnKJQagNLgadUlgICjkk@1734628392000"}.
     */
    @NotNull
    String id();

    /**
     * Instance-pinned subscription — survives even if this instance is moved
     * or replaced at its old path. Implementations dispatch via the existing
     * {@link io.ably.lib.objects.type.map.LiveMapChange} /
     * {@link io.ably.lib.objects.type.counter.LiveCounterChange} machinery.
     */
    @NonBlocking
    @NotNull
    ObjectsSubscription subscribe(@NotNull PathChangeListener listener);
}
