package io.ably.lib.objects.path;

import io.ably.lib.objects.type.counter.LiveCounter;
import org.jetbrains.annotations.ApiStatus;

/**
 * Public path-API name for a LiveCounter instance. Extends both the path-API
 * {@link LiveInstance} marker and the existing internal {@link LiveCounter}
 * so that existing instance-level consumers can use it interchangeably.
 * <p>
 * Logically sealed.
 */
@ApiStatus.NonExtendable
public interface LiveCounterInstance extends LiveInstance, LiveCounter {
}
