/**
 * The public, strongly-typed LiveObjects API: path-based and instance-based views
 * over the objects graph on a channel.
 *
 * <p>This root package holds the types shared by both view hierarchies:
 * {@link io.ably.lib.liveobjects.ValueType} (the categories a resolved value may have)
 * and {@link io.ably.lib.liveobjects.Subscription} (the handle returned by every
 * {@code subscribe} operation). The hierarchies themselves live in
 * {@link io.ably.lib.liveobjects.path} (lazy, path-addressed references) and
 * {@link io.ably.lib.liveobjects.instance} (O(1), identity-addressed references);
 * message metadata delivered to subscription listeners lives in
 * {@link io.ably.lib.liveobjects.message}, and write-side value types in
 * {@link io.ably.lib.liveobjects.value}.
 *
 * <p>Spec: RTTS1-RTTS10 (typed-SDK public API partition)
 */
package io.ably.lib.liveobjects;
