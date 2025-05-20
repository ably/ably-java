package io.ably.lib.objects;

import io.ably.lib.types.Callback;
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
     *
     * @param keyName the key to be set.
     * @param value the value to be associated with the key.
     */
    void set(@NotNull String keyName, @NotNull Object value);

    /**
     * Removes the specified key and its associated value from the map.
     *
     * @param keyName the key to be removed.
     */
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
     *
     * @param keyName the key to be set.
     * @param value the value to be associated with the key.
     * @param callback the callback to handle the result or any errors.
     */
    void setAsync(@NotNull String keyName, @NotNull Object value, @NotNull Callback<Void> callback);

    /**
     * Asynchronously removes the specified key and its associated value from the map.
     *
     * @param keyName  the key to be removed.
     * @param callback the callback to handle the result or any errors.
     */
    void removeAsync(@NotNull String keyName, @NotNull Callback<Void> callback);
}
