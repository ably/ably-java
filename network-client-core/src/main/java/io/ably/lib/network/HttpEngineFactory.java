package io.ably.lib.network;

import java.lang.reflect.InvocationTargetException;

public interface HttpEngineFactory {

    HttpEngine create(HttpEngineConfig config);
    EngineType getEngineType();

    static HttpEngineFactory getFirstAvailable() {
        HttpEngineFactory okHttpFactory = tryGetOkHttpFactory();
        if (okHttpFactory != null) return okHttpFactory;
        HttpEngineFactory defaultFactory = tryGetDefaultFactory();
        if (defaultFactory != null) return defaultFactory;
        throw new IllegalStateException("No engines are available");
    }

    static HttpEngineFactory tryGetOkHttpFactory() {
        try {
            Class<?> okHttpFactoryClass = Class.forName("io.ably.lib.network.OkHttpEngineFactory");
            return (HttpEngineFactory) okHttpFactoryClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            return null;
        }
    }

    static HttpEngineFactory tryGetDefaultFactory() {
        try {
            Class<?> defaultFactoryClass = Class.forName("io.ably.lib.network.DefaultHttpEngineFactory");
            return (HttpEngineFactory) defaultFactoryClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            return null;
        }
    }
}
