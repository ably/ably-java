package io.ably.lib.objects.batch;

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
    Object get(String keyName);

    /**
     * Retrieves all entries (key-value pairs) in the live map.
     *
     * @return an iterable collection of map entries.
     */
    Iterable<Map.Entry<String, Object>> entries();

    /**
     * Retrieves all keys in the live map.
     *
     * @return an iterable collection of keys.
     */
    Iterable<String> keys();

    /**
     * Retrieves all values in the live map.
     *
     * @return an iterable collection of values.
     */
    Iterable<Object> values();

    /**
     * Sets the specified key to the given value in the live map.
     *
     * @param keyName the name of the key to set.
     * @param value the value to associate with the specified key.
     */
    void set(String keyName, Object value);

    /**
     * Removes the specified key-value pair from the live map.
     *
     * @param keyName the name of the key to remove.
     * @param value the value associated with the key to remove.
     */
    void remove(String keyName, Object value);

    /**
     * Retrieves the number of entries in the live map.
     *
     * @return the size of the live map as a Long.
     */
    Long size();
}
