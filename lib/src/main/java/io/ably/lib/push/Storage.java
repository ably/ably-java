package io.ably.lib.push;

import java.lang.reflect.Field;

/**
 * Interface for an entity that supplies key value store
 * - methods getString and getInt have to return default value if requested key is not found
 */
public interface Storage {

    void putString(String key, String value);

    void putInt(String key, int value);

    String getString(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    void clear(Field[] fields);
}
