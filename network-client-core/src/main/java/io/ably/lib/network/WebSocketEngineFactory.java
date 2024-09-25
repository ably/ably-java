package io.ably.lib.network;

import java.lang.reflect.InvocationTargetException;

public interface WebSocketEngineFactory {
    WebSocketEngine create(WebSocketEngineConfig config);
    EngineType getEngineType();

    static WebSocketEngineFactory getFirstAvailable() {
        WebSocketEngineFactory okWebSocketFactory = tryGetOkWebSocketFactory();
        if (okWebSocketFactory != null) return okWebSocketFactory;
        WebSocketEngineFactory defaultFactory = tryGetDefaultFactory();
        if (defaultFactory != null) return defaultFactory;
        throw new IllegalStateException("No engines are available");
    }

    static WebSocketEngineFactory tryGetOkWebSocketFactory() {
        try {
            Class<?> okWebSocketFactoryClass = Class.forName("io.ably.lib.network.OkHttpWebSocketEngineFactory");
            return (WebSocketEngineFactory) okWebSocketFactoryClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            return null;
        }
    }

    static WebSocketEngineFactory tryGetDefaultFactory() {
        try {
            Class<?> defaultFactoryClass = Class.forName("io.ably.lib.network.DefaultWebSocketEngineFactory");
            return (WebSocketEngineFactory) defaultFactoryClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            return null;
        }
    }
}
