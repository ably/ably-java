package io.ably.lib.push;

import java.lang.reflect.Field;

public interface Storage {

    void putString(String key, String value);

    void putInt(String key, int value);

    String getString(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    void reset(Field[] fields);
}
