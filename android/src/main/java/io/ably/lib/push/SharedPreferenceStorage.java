package io.ably.lib.push;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.lang.reflect.Field;

public class SharedPreferenceStorage implements Storage{

    private final ActivationContext activationContext;

    public SharedPreferenceStorage(ActivationContext activationContext) {
        this.activationContext = activationContext;
    }

    @Override
    public void putString(String key, String value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public void putInt(String key, int value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
        prefs.edit().putInt(key, value).apply();
    }

    @Override
    public String getString(String key, String defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
        return prefs.getString(key, defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activationContext.getContext());
        return prefs.getInt(key, defaultValue);
    }

    @Override
    public void reset(Field[] fields) {
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
