package io.ably.lib.object.instance.types;

import io.ably.lib.object.instance.LiveObjectInstance;
import io.ably.lib.objects.type.map.LiveMapValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link LiveObjectInstance} bound to a {@code LiveMap}. Provides type-safe access to
 * map-specific operations such as {@link #get(String)}, {@link #entries()} and
 * {@link #set(String, LiveMapValue)}.
 *
 * <p>Operations are bound to the specific underlying {@code LiveMap}, dereferenced in
 * O(1), and do not perform any path resolution.
 */
public interface LiveMapInstance extends LiveObjectInstance {

    /**
     * Returns a {@link LiveObjectInstance} wrapping the value at {@code key} of the
     * wrapped {@code LiveMap}, or {@code null} when the key is absent / tombstoned.
     *
     * <p>Spec: RTINS5
     *
     * @param key the key to look up
     * @return an instance wrapping the value at {@code key}, or {@code null}
     */
    @Nullable
    LiveObjectInstance get(@NotNull String key);

    /**
     * Returns the entries (key, child {@link LiveObjectInstance}) of the wrapped
     * {@code LiveMap}.
     *
     * <p>Spec: RTINS6
     *
     * @return an unmodifiable iterable of entries
     */
    @NotNull
    @Unmodifiable
    Iterable<Map.Entry<String, LiveObjectInstance>> entries();

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
     * Returns the child {@link LiveObjectInstance}s for each value in the wrapped
     * {@code LiveMap}.
     *
     * <p>Spec: RTINS8
     *
     * @return an unmodifiable iterable of value instances
     */
    @NotNull
    @Unmodifiable
    Iterable<LiveObjectInstance> values();

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
}
