package io.ably.lib.object.instance.types;

import io.ably.lib.object.instance.Instance;
import io.ably.lib.object.instance.InstanceListener;
import io.ably.lib.object.Subscription;
import io.ably.lib.object.value.LiveMapValue;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link Instance} bound to a {@code LiveMap}. Provides type-safe access to
 * map-specific operations such as {@link #get(String)}, {@link #entries()} and
 * {@link #set(String, LiveMapValue)}.
 *
 * <p>Operations are bound to the specific underlying {@code LiveMap}, dereferenced in
 * O(1), and do not perform any path resolution.
 *
 * <p>Spec: RTTS10a
 */
public interface LiveMapInstance extends Instance {

    /**
     * Returns the object id of the wrapped {@code LiveMap}.
     *
     * <p>Spec: RTINS3a
     *
     * @return the wrapped {@code LiveMap}'s object id
     */
    @NotNull
    String getId();

    /**
     * Returns a {@link Instance} wrapping the value at {@code key} of the
     * wrapped {@code LiveMap}, or {@code null} when the key is absent / tombstoned.
     *
     * <p>Spec: RTINS5
     *
     * @param key the key to look up
     * @return an instance wrapping the value at {@code key}, or {@code null}
     */
    @Nullable
    Instance get(@NotNull String key);

    /**
     * Returns the entries (key, child {@link Instance}) of the wrapped
     * {@code LiveMap}.
     *
     * <p>Spec: RTINS6
     *
     * @return an unmodifiable iterable of entries
     */
    @NotNull
    @Unmodifiable
    Iterable<Map.Entry<String, Instance>> entries();

    /**
     * Returns the keys of the wrapped {@code LiveMap}.
     *
     * <p>Spec: RTINS7
     *
     * @return an unmodifiable iterable of keys
     */
    @NotNull
    @Unmodifiable
    Iterable<String> keys();

    /**
     * Returns the child {@link Instance}s for each value in the wrapped
     * {@code LiveMap}.
     *
     * <p>Spec: RTINS8
     *
     * @return an unmodifiable iterable of value instances
     */
    @NotNull
    @Unmodifiable
    Iterable<Instance> values();

    /**
     * Returns the number of (non-tombstoned) entries in the wrapped {@code LiveMap}.
     *
     * <p>Spec: RTINS9
     *
     * @return the map size
     */
    @NotNull
    Long size();

    /**
     * Sets a key on the wrapped {@code LiveMap} to the provided value. Sends a
     * {@code MAP_SET} operation to the realtime system; the local state is updated when
     * the operation is echoed back.
     *
     * <p>Spec: RTINS12
     *
     * @param key   the key to set
     * @param value the value to associate with {@code key}
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> set(@NotNull String key, @NotNull LiveMapValue value);

    /**
     * Removes a key from the wrapped {@code LiveMap}. Sends a {@code MAP_REMOVE}
     * operation to the realtime system; the local state is updated when the operation
     * is echoed back.
     *
     * <p>Spec: RTINS13
     *
     * @param key the key to remove
     * @return a future that completes when the operation has been acknowledged
     */
    @NotNull
    CompletableFuture<Void> remove(@NotNull String key);

    /**
     * Subscribes a listener for updates on the wrapped {@code LiveMap}. The listener is
     * invoked whenever the wrapped map is changed by a local or remote operation. Call
     * {@link Subscription#unsubscribe()} on the returned handle to stop
     * receiving events for this listener.
     *
     * <p>The subscription is identity-based: it follows the specific underlying
     * {@code LiveMap}, regardless of where it sits in the LiveObjects graph.
     *
     * <p>Spec: RTTS10a / RTINS16
     *
     * @param listener the listener to invoke on updates
     * @return a subscription handle that can be used to unsubscribe this listener
     */
    @NonBlocking
    @NotNull Subscription subscribe(@NotNull InstanceListener listener);
}
