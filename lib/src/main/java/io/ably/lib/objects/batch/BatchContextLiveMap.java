package io.ably.lib.objects.batch;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;

/**
 * The BatchContextLiveMap interface provides methods to interact with a live map
 * in the context of batch operations. It allows retrieving, modifying, and querying
 * key-value pairs in the map.
 */
public interface BatchContextLiveMap {

    /**
     * Retrieves the value associated with the specified key.
     *
     * @param keyName the name of the key whose value is to be retrieved.
     * @return the value associated with the specified key, or null if the key does not exist.
     */
    @Nullable
    Object get(@NotNull String keyName);

    /**
     * Retrieves all entries (key-value pairs) in the live map.
     *
     * @return an unmodifiable iterable collection of map entries.
     */
    @NotNull
    @Unmodifiable
    Iterable<Map.Entry<String, Object>> entries();

    /**
     * Retrieves all keys in the live map.
     *
     * @return an unmodifiable iterable collection of keys.
     */
    @NotNull
    @Unmodifiable
    Iterable<String> keys();

    /**
     * Retrieves all values in the live map.
     *
     * @return an unmodifiable iterable collection of values.
     */
    @NotNull
    @Unmodifiable
    Iterable<Object> values();

    /**
     * Sets the specified key to the given value in the live map.
     *
     * @param keyName the name of the key to set.
     * @param value the value to associate with the specified key.
     */
    void set(@NotNull String keyName, @NotNull Object value);

    /**
     * Removes the specified key-value pair from the live map.
     *
     * @param keyName the name of the key to remove.
     */
    void remove(@NotNull String keyName);

    /**
     * Retrieves the number of entries in the live map.
     *
     * @return the size of the live map as a Long.
     */
    @NotNull
    @Contract(pure = true) // Indicates this method does not modify the state of the object.
    Long size();
}
