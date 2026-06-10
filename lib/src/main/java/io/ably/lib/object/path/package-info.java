/**
 * The path-addressed view of the LiveObjects graph.
 * {@link io.ably.lib.object.path.PathObject} stores a path from the channel's
 * root {@code LiveMap} and re-resolves it lazily on every call, so a reference
 * survives object replacement at its path. Type-specific operations live on the
 * sub-types in {@link io.ably.lib.object.path.types}; path-based subscriptions
 * use {@link io.ably.lib.object.path.PathObjectListener},
 * {@link io.ably.lib.object.path.PathObjectSubscriptionEvent} and
 * {@link io.ably.lib.object.path.PathObjectSubscriptionOptions}.
 *
 * <p>Spec: RTPO1-RTPO19, RTTS3-RTTS5
 */
package io.ably.lib.object.path;
