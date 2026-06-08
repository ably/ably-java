package io.ably.lib.object.path;

import com.google.gson.JsonElement;
import io.ably.lib.object.ObjectType;
import io.ably.lib.object.instance.LiveObjectInstance;
import io.ably.lib.object.path.types.BinaryPathObject;
import io.ably.lib.object.path.types.BooleanPathObject;
import io.ably.lib.object.path.types.JsonArrayPathObject;
import io.ably.lib.object.path.types.JsonObjectPathObject;
import io.ably.lib.object.path.types.LiveCounterPathObject;
import io.ably.lib.object.path.types.LiveMapPathObject;
import io.ably.lib.object.path.types.NumberPathObject;
import io.ably.lib.object.path.types.StringPathObject;
import io.ably.lib.objects.ObjectsSubscription;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides a path-based, navigational view over the LiveObjects graph rooted at the
 * channel's root {@code LiveMap}. A {@code PathObject} encapsulates a path expressed as
 * an ordered list of string segments and resolves the path lazily against the current
 * client-side state of the graph when read or write operations are invoked.
 *
 * <p>Resolution is best-effort: it observes the local object tree at the time the
 * operation is called. There is no global transaction primitive, so the value at a given
 * path can change between two calls on the same {@code PathObject} (e.g. between
 * {@link #exists()} and a subsequent write) as updates from other clients are applied.
 *
 * <p>For the strongly-typed flavour of the API in Java, callers normally interact with
 * type-specific sub-types ({@link LiveMapPathObject}, {@link LiveCounterPathObject}, and
 * the primitive {@code *PathObject} types). Use the {@code as*} helpers to obtain a
 * sub-type wrapper without performing type validation.
 *
 * <p>Spec: RTPO1, RTPO2
 */
public interface PathObject {

    /**
     * Returns the {@link ObjectType} of the value the resolved at this path currently.
     * Use this instead of dedicated {@code isLiveMap}/{@code isLiveCounter}/etc. checks.
     *
     * @return the resolved object type at this path
     */
    @NotNull ObjectType getType();

    /**
     * Returns a dot-delimited string representation of the stored path segments.
     * Dot characters inside individual segments are escaped with a backslash, so a
     * path with segments {@code ["a", "b.c", "d"]} is represented as {@code "a.b\.c.d"}.
     * An empty path (i.e. the root {@code PathObject}) returns the empty string.
     *
     * <p>Spec: RTPO4
     *
     * @return the dot-delimited path from the root to this position
     */
    @NotNull String path();

    /**
     * Returns a new {@code PathObject} whose path is this path with the segments parsed
     * from {@code path} appended. The {@code path} argument is a dot-delimited string;
     * a backslash-escaped dot ({@code \.}) is treated as a literal dot within a segment.
     *
     * <p>This is purely navigational - no resolution against the LiveObjects graph is
     * performed by this call. {@code pathObject.at("a.b.c")} is equivalent to
     * {@code pathObject.get("a").get("b").get("c")} on a {@link LiveMapPathObject}.
     *
     * <p>For primitive {@code *PathObject} sub-types and {@link LiveCounterPathObject},
     * deeper navigation is not meaningful; implementations may throw or return a
     * {@code PathObject} that will fail to resolve at read/write time.
     *
     * <p>Spec: RTPO6
     *
     * @param path dot-delimited path to append to this path
     * @return a new {@code PathObject} representing the deeper path
     */
    @NotNull PathObject at(@NotNull String path);

    /**
     * Resolves this path and returns a {@link LiveObjectInstance} wrapping the underlying
     * value if it is a {@code LiveMap} or {@code LiveCounter}.
     *
     * <p>Returns {@code null} when the resolved value is a primitive (LiveObjects with
     * no object id), when the path does not resolve, or when called on primitive
     * {@code *PathObject} sub-types.
     *
     * <p>Spec: RTPO8
     *
     * @return a {@link LiveObjectInstance} wrapping the resolved live object, or {@code null}
     */
    @Nullable LiveObjectInstance instance();

    /**
     * Returns a JSON-serializable, recursively compacted snapshot of the value at this
     * path. Behaves like the spec's {@code compact} except that {@code Binary} values
     * are base64-encoded and cyclic references are represented as
     * {@code { "objectId": ... }} markers, so the result is safe to serialise as JSON.
     *
     * <p>Returns {@code null} when the path does not resolve.
     *
     * <p>Spec: RTPO14
     *
     * @return the compacted JSON snapshot, or {@code null} if the path does not resolve
     */
    @Nullable JsonElement compactJson();

    /**
     * Subscribes a listener for path-based update events. The listener is invoked when
     * an operation modifies the value at this path. The same path may be subscribed by
     * multiple listeners independently.
     *
     * <p>Spec: RTPO19
     *
     * @param listener the listener to invoke on updates
     * @return a subscription handle that can be used to unsubscribe this listener
     */
    @NonBlocking
    @NotNull ObjectsSubscription subscribe(@NotNull Listener listener);

    /**
     * Subscribes a listener for path-based update events using the provided
     * {@link SubscriptionOptions}. Options control coverage rules such as the
     * {@code depth} of nested updates that trigger the listener.
     *
     * <p>Spec: RTPO19
     *
     * @param listener the listener to invoke on updates
     * @param options  optional subscription options, may be {@code null}
     * @return a subscription handle that can be used to unsubscribe this listener
     */
    @NonBlocking
    @NotNull ObjectsSubscription subscribe(@NotNull Listener listener, @Nullable SubscriptionOptions options);

    /**
     * Unsubscribes the specified listener previously registered via
     * {@link #subscribe(Listener)} or {@link #subscribe(Listener, SubscriptionOptions)}.
     * No-op if the listener is not currently subscribed for this path.
     *
     * @param listener the listener to remove
     */
    @NonBlocking
    void unsubscribe(@NotNull Listener listener);

    /**
     * Removes all listeners previously registered for this path.
     */
    @NonBlocking
    void unsubscribeAll();

    /**
     * Returns {@code true} if a value currently resolves at this path in the local
     * object graph. This is a best-effort check evaluated at call time; the answer may
     * change immediately afterwards as remote operations are applied. Useful as a
     * guard before performing operations whose semantics depend on existence.
     *
     * <p>Complexity is O(n) in the path length because the path must be resolved.
     *
     * @return {@code true} if the path resolves to a value, {@code false} otherwise
     */
    boolean exists();

    /**
     * Returns this {@code PathObject} wrapped as a {@link LiveMapPathObject}.
     *
     * <p>This is a best-effort cast - it does not validate that the underlying value
     * at this path is a {@code LiveMap}. Read operations are always permitted on the
     * returned wrapper; write or terminal operations that require resolution will fail
     * at call time if the resolved value is not a {@code LiveMap}.
     *
     * @return a {@link LiveMapPathObject} view of this path
     */
    @NotNull LiveMapPathObject asLiveMap();

    /**
     * Returns this {@code PathObject} wrapped as a {@link LiveCounterPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * @return a {@link LiveCounterPathObject} view of this path
     */
    @NotNull LiveCounterPathObject asLiveCounter();

    /**
     * Returns this {@code PathObject} wrapped as a {@link NumberPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * @return a {@link NumberPathObject} view of this path
     */
    @NotNull NumberPathObject asNumber();

    /**
     * Returns this {@code PathObject} wrapped as a {@link StringPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * @return a {@link StringPathObject} view of this path
     */
    @NotNull StringPathObject asString();

    /**
     * Returns this {@code PathObject} wrapped as a {@link BooleanPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * @return a {@link BooleanPathObject} view of this path
     */
    @NotNull BooleanPathObject asBoolean();

    /**
     * Returns this {@code PathObject} wrapped as a {@link BinaryPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * @return a {@link BinaryPathObject} view of this path
     */
    @NotNull BinaryPathObject asBinary();

    /**
     * Returns this {@code PathObject} wrapped as a {@link JsonObjectPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * @return a {@link JsonObjectPathObject} view of this path
     */
    @NotNull JsonObjectPathObject asJsonObject();

    /**
     * Returns this {@code PathObject} wrapped as a {@link JsonArrayPathObject}.
     * Best-effort cast; does not validate the underlying type at this path.
     *
     * @return a {@link JsonArrayPathObject} view of this path
     */
    @NotNull JsonArrayPathObject asJsonArray();

    /**
     * Listener interface for {@link PathObject#subscribe(Listener) path-based subscriptions}.
     *
     * <p>Spec: RTPO19a1
     */
    interface Listener {
        /**
         * Invoked when a change is applied at, or beneath, the subscribed path according
         * to the configured {@link SubscriptionOptions}.
         *
         * @param event the event describing the change
         */
        void onUpdated(@NotNull SubscriptionEvent event);
    }

    /**
     * Event delivered to {@link Listener#onUpdated(SubscriptionEvent)} when a change
     * affects the subscribed path.
     *
     * <p>Spec: RTPO19e
     */
    interface SubscriptionEvent {
        /**
         * Returns a {@link PathObject} pointing to the path where the change occurred.
         *
         * <p>Spec: RTPO19e1
         *
         * @return the {@code PathObject} at the changed path
         */
        @NotNull PathObject getObject();
    }

    /**
     * Optional subscription options accepted by
     * {@link PathObject#subscribe(Listener, SubscriptionOptions)}.
     *
     * <p>Spec: RTPO19c
     */
    final class SubscriptionOptions {

        private final Integer depth;

        /**
         * Creates options with the given {@code depth}.
         *
         * @param depth how many levels of path nesting below the subscribed path should
         *              trigger the listener; must be a positive integer if provided
         */
        public SubscriptionOptions(@Nullable Integer depth) {
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
}
