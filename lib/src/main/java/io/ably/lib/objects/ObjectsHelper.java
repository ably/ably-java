package io.ably.lib.objects;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.util.Log;

import java.lang.reflect.InvocationTargetException;

public class ObjectsHelper {

    private static final String TAG = ObjectsHelper.class.getName();
    private static volatile ObjectsSerializer objectsSerializer;

    public static ObjectsPlugin tryInitializeObjectsPlugin(AblyRealtime ablyRealtime) {
        try {
            Class<?> objectsImplementation = Class.forName("io.ably.lib.objects.DefaultObjectsPlugin");
            ObjectsAdapter adapter = new Adapter(ablyRealtime);
            return (ObjectsPlugin) objectsImplementation
                .getDeclaredConstructor(ObjectsAdapter.class)
                .newInstance(adapter);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            Log.i(TAG, "LiveObjects plugin not found in classpath. LiveObjects functionality will not be available.", e);
            return null;
        }
    }

    public static ObjectsSerializer getSerializer() {
        if (objectsSerializer == null) {
            synchronized (ObjectsHelper.class) {
                if (objectsSerializer == null) { // Double-Checked Locking (DCL)
                    try {
                        Class<?> serializerClass = Class.forName("io.ably.lib.objects.serialization.DefaultObjectsSerializer");
                        objectsSerializer = (ObjectsSerializer) serializerClass.getDeclaredConstructor().newInstance();
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                             NoSuchMethodException |
                             InvocationTargetException e) {
                        Log.w(TAG, "Failed to init ObjectsSerializer, LiveObjects plugin not included in the classpath", e);
                        return null;
                    }
                }
            }
        }
        return objectsSerializer;
    }
}
