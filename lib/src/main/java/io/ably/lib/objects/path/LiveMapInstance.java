package io.ably.lib.objects.path;

import io.ably.lib.objects.type.map.LiveMap;
import org.jetbrains.annotations.ApiStatus;

/**
 * Public path-API name for a LiveMap instance. Extends both the path-API
 * {@link LiveInstance} marker and the existing internal {@link LiveMap} so
 * that existing instance-level consumers can use it interchangeably.
 * <p>
 * Logically sealed.
 */
@ApiStatus.NonExtendable
public interface LiveMapInstance extends LiveInstance, LiveMap {
}
