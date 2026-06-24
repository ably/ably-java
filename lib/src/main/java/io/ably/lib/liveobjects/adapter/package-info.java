/**
 * Adapter layer bridging the path-based LiveObjects implementation to the core Ably client.
 * {@link io.ably.lib.liveobjects.adapter.AblyClientAdapter} is the abstraction the implementation
 * depends on; {@link io.ably.lib.liveobjects.adapter.Adapter} is the default implementation backed
 * by an {@link io.ably.lib.realtime.AblyRealtime} client.
 *
 * <p>This package is intentionally independent of the legacy {@code io.ably.lib.objects}
 * package so the path-based API can evolve on its own.
 */
package io.ably.lib.liveobjects.adapter;
