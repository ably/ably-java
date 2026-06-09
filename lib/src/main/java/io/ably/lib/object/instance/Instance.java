package io.ably.lib.object.instance;

import com.google.gson.JsonElement;
import io.ably.lib.object.ValueType;
import io.ably.lib.object.instance.types.BinaryInstance;
import io.ably.lib.object.instance.types.BooleanInstance;
import io.ably.lib.object.instance.types.JsonArrayInstance;
import io.ably.lib.object.instance.types.JsonObjectInstance;
import io.ably.lib.object.instance.types.LiveCounterInstance;
import io.ably.lib.object.instance.types.LiveMapInstance;
import io.ably.lib.object.instance.types.NumberInstance;
import io.ably.lib.object.instance.types.StringInstance;
import io.ably.lib.objects.ObjectsSubscription;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;

/**
 * A direct-reference view of a single LiveObject (a {@code LiveMap} or {@code LiveCounter})
 * or a primitive value. Unlike {@code PathObject}, which resolves a path lazily against
 * the LiveObjects graph at every call, an {@code Instance} is bound to a specific
 * underlying value and dereferenced in O(1).
 *
 * <p>Java exposes type-specific sub-types ({@link LiveMapInstance},
 * {@link LiveCounterInstance}, and the primitive {@code *Instance} types). Use the
 * {@code as*} helpers to obtain a sub-type wrapper without performing type validation.
 * Only {@link LiveMapInstance} and {@link LiveCounterInstance} expose an object id
 * (via their own {@code getId()} methods); primitive instances are anonymous.
 *
 * <p>Spec: RTINS1
 */
public interface Instance {

    /**
     * Returns the {@link ValueType} of the value wrapped by this instance. Use this
     * instead of dedicated {@code isLiveMap}/{@code isLiveCounter}/etc. checks.
     *
     * @return the wrapped value type
     */
    @NotNull ValueType getType();

    /**
     * Returns a JSON-serializable, recursively compacted snapshot of the wrapped value.
     * Behaves identically to {@code PathObject#compactJson} except that it operates on
     * the wrapped value directly instead of resolving a path. An {@code Instance} is
     * always bound to a resolved value, so this always returns a non-null result;
     * failures of the access API preconditions are signalled via {@code AblyException}.
     *
     * <p>Spec: RTINS11
     *
     * @return the compacted JSON snapshot
     */
    @NotNull JsonElement compactJson();

    /**
     * Subscribes a listener for updates on the underlying LiveObject. The listener is
     * invoked whenever the wrapped object is changed by a local or remote operation.
     * Call {@link ObjectsSubscription#unsubscribe()} on the returned handle to stop
     * receiving events for this listener.
     *
     * <p>Subscribe is not supported on primitive instances; implementations may throw
     * when called on {@link NumberInstance}, {@link StringInstance},
     * {@link BooleanInstance}, {@link BinaryInstance}, {@link JsonObjectInstance} or
     * {@link JsonArrayInstance}.
     *
     * <p>Spec: RTINS16
     *
     * @param listener the listener to invoke on updates
     * @return a subscription handle that can be used to unsubscribe this listener
     */
    @NonBlocking
    @NotNull ObjectsSubscription subscribe(@NotNull Listener listener);

    /**
     * Returns this instance wrapped as a {@link LiveMapInstance}.
     *
     * <p>Best-effort cast; does not validate the underlying type. Read operations on
     * the returned wrapper are always permitted; write/terminal operations will fail
     * at call time if the wrapped value is not a {@code LiveMap}.
     *
     * @return a {@link LiveMapInstance} view of this instance
     */
    @NotNull LiveMapInstance asLiveMap();

    /**
     * Returns this instance wrapped as a {@link LiveCounterInstance}.
     * Best-effort cast; does not validate the underlying type.
     *
     * @return a {@link LiveCounterInstance} view of this instance
     */
    @NotNull LiveCounterInstance asLiveCounter();

    /**
     * Returns this instance wrapped as a {@link NumberInstance}.
     * Best-effort cast; does not validate the underlying type.
     *
     * @return a {@link NumberInstance} view of this instance
     */
    @NotNull NumberInstance asNumber();

    /**
     * Returns this instance wrapped as a {@link StringInstance}.
     * Best-effort cast; does not validate the underlying type.
     *
     * @return a {@link StringInstance} view of this instance
     */
    @NotNull StringInstance asString();

    /**
     * Returns this instance wrapped as a {@link BooleanInstance}.
     * Best-effort cast; does not validate the underlying type.
     *
     * @return a {@link BooleanInstance} view of this instance
     */
    @NotNull BooleanInstance asBoolean();

    /**
     * Returns this instance wrapped as a {@link BinaryInstance}.
     * Best-effort cast; does not validate the underlying type.
     *
     * @return a {@link BinaryInstance} view of this instance
     */
    @NotNull BinaryInstance asBinary();

    /**
     * Returns this instance wrapped as a {@link JsonObjectInstance}.
     * Best-effort cast; does not validate the underlying type.
     *
     * @return a {@link JsonObjectInstance} view of this instance
     */
    @NotNull JsonObjectInstance asJsonObject();

    /**
     * Returns this instance wrapped as a {@link JsonArrayInstance}.
     * Best-effort cast; does not validate the underlying type.
     *
     * @return a {@link JsonArrayInstance} view of this instance
     */
    @NotNull JsonArrayInstance asJsonArray();

    /**
     * Listener interface for {@link Instance#subscribe(Listener) instance
     * subscriptions}.
     *
     * <p>Spec: RTINS16a1
     */
    interface Listener {
        /**
         * Invoked when the wrapped LiveObject is modified.
         *
         * @param event the event describing the change
         */
        void onUpdated(@NotNull SubscriptionEvent event);
    }

    /**
     * Event delivered to {@link Listener#onUpdated(SubscriptionEvent)} when the wrapped
     * LiveObject is updated.
     *
     * <p>Spec: RTINS16e
     */
    interface SubscriptionEvent {
        /**
         * Returns the {@link Instance} that was updated.
         *
         * @return the updated instance
         */
        @NotNull Instance getInstance();
    }
}
