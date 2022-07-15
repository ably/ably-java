package io.ably.core.push;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.lang.reflect.Field;

public class SharedPreferenceStorage implements Storage{

    private final ActivationContext activationContext;

    public SharedPreferenceStorage(ActivationContext activationContext) {
        this.activationContext = activationContext;
    }

    private SharedPreferences sharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
    }

    @Override
    public void put(String key, String value) {
        sharedPreferences().edit().putString(key, value).apply();
    }

    @Override
    public void put(String key, int value) {
        sharedPreferences().edit().putInt(key, value).apply();
    }

    @Override
    public String get(String key, String defaultValue) {
        return sharedPreferences().getString(key, defaultValue);
    }

    @Override
    public int get(String key, int defaultValue) {
        return sharedPreferences().getInt(key, defaultValue);
    }

    @Override
    public void clear(Field[] fields) {
        SharedPreferences.Editor editor = activationContext.getPreferences().edit();
        for (Field f : fields) {
            try {
                editor.remove((String) f.get(null));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        editor.commit();
    }
}
