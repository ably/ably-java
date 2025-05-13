package io.ably.lib.objects;

import io.ably.lib.types.Callback;

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
    Object get(String keyName);

    /**
     * Retrieves all entries (key-value pairs) in the map.
     *
     * @return an iterable collection of all entries in the map.
     */
    Iterable<Map.Entry<String, Object>> entries();

    /**
     * Retrieves all keys in the map.
     *
     * @return an iterable collection of all keys in the map.
     */
    Iterable<String> keys();

    /**
     * Retrieves all values in the map.
     *
     * @return an iterable collection of all values in the map.
     */
    Iterable<Object> values();

    /**
     * Sets the specified key to the given value in the map.
     *
     * @param keyName the key to be set.
     * @param value the value to be associated with the key.
     */
    void set(String keyName, Object value);

    /**
     * Removes the specified key and its associated value from the map.
     *
     * @param keyName the key to be removed.
     * @param value the value associated with the key to be removed.
     */
    void remove(String keyName, Object value);

    /**
     * Retrieves the number of entries in the map.
     *
     * @return the size of the map.
     */
    Long size();

    /**
     * Asynchronously sets the specified key to the given value in the map.
     *
     * @param keyName the key to be set.
     * @param value the value to be associated with the key.
     * @param callback the callback to handle the result or any errors.
     */
    void setAsync(String keyName, Object value, Callback<Void> callback);

    /**
     * Asynchronously removes the specified key and its associated value from the map.
     *
     * @param keyName the key to be removed.
     * @param value the value associated with the key to be removed.
     * @param callback the callback to handle the result or any errors.
     */
    void removeAsync(String keyName, Object value, Callback<Void> callback);
}
