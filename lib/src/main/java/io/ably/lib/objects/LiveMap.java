package io.ably.lib.objects;

import io.ably.lib.types.Callback;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

/**
 * The LiveMap interface provides methods to interact with a live, real-time map structure.
 * It supports both synchronous and asynchronous operations for managing key-value pairs.
 */
public interface LiveMap {

    /**
     * Retrieves the value associated with the specified key.
     * If this map object is tombstoned (deleted), `undefined` is returned.
     * If no entry is associated with the specified key, `undefined` is returned.
     * If map entry is tombstoned (deleted), `undefined` is returned.
     * If the value associated with the provided key is an objectId string of another LiveObject, a reference to that LiveObject
     * is returned, provided it exists in the local pool and is not tombstoned. Otherwise, `undefined` is returned.
     * If the value is not an objectId, then that value is returned.
     *
     * @param keyName the key whose associated value is to be returned.
     * @return the value associated with the specified key, or null if the key does not exist.
     */
    @Nullable
    Object get(@NotNull String keyName);

    /**
     * Retrieves all entries (key-value pairs) in the map.
     *
     * @return an iterable collection of all entries in the map.
     */
    @NotNull
    @Unmodifiable
    Iterable<Map.Entry<String, Object>> entries();

    /**
     * Retrieves all keys in the map.
     *
     * @return an iterable collection of all keys in the map.
     */
    @NotNull
    @Unmodifiable
    Iterable<String> keys();

    /**
     * Retrieves all values in the map.
     *
     * @return an iterable collection of all values in the map.
     */
    @NotNull
    @Unmodifiable
    Iterable<Object> values();

    /**
     * Sets the specified key to the given value in the map.
     * Send a MAP_SET operation to the realtime system to set a key on this LiveMap object to a specified value.
     * This does not modify the underlying data of this LiveMap object. Instead, the change will be applied when
     * the published MAP_SET operation is echoed back to the client and applied to the object following the regular
     * operation application procedure.
     *
     * @param keyName the key to be set.
     * @param value the value to be associated with the key.
     */
    @Blocking
    void set(@NotNull String keyName, @NotNull Object value);

    /**
     * Removes the specified key and its associated value from the map.
     * Send a MAP_REMOVE operation to the realtime system to tombstone a key on this LiveMap object.
     * This does not modify the underlying data of this LiveMap object. Instead, the change will be applied when
     * the published MAP_REMOVE operation is echoed back to the client and applied to the object following the regular
     * operation application procedure.
     *
     * @param keyName the key to be removed.
     */
    @Blocking
    void remove(@NotNull String keyName);

    /**
     * Retrieves the number of entries in the map.
     *
     * @return the size of the map.
     */
    @Contract(pure = true) // Indicates this method does not modify the state of the object.
    @NotNull
    Long size();

    /**
     * Asynchronously sets the specified key to the given value in the map.
     * Send a MAP_SET operation to the realtime system to set a key on this LiveMap object to a specified value.
     * This does not modify the underlying data of this LiveMap object. Instead, the change will be applied when
     * the published MAP_SET operation is echoed back to the client and applied to the object following the regular
     * operation application procedure.
     *
     * @param keyName the key to be set.
     * @param value the value to be associated with the key.
     * @param callback the callback to handle the result or any errors.
     */
    @NonBlocking
    void setAsync(@NotNull String keyName, @NotNull Object value, @NotNull Callback<Void> callback);

    /**
     * Asynchronously removes the specified key and its associated value from the map.
     * Send a MAP_REMOVE operation to the realtime system to tombstone a key on this LiveMap object.
     * This does not modify the underlying data of this LiveMap object. Instead, the change will be applied when
     * the published MAP_REMOVE operation is echoed back to the client and applied to the object following the regular
     * operation application procedure.
     *
     * @param keyName  the key to be removed.
     * @param callback the callback to handle the result or any errors.
     */
    @NonBlocking
    void removeAsync(@NotNull String keyName, @NotNull Callback<Void> callback);
}
