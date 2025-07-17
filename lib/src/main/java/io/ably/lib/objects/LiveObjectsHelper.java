package io.ably.lib.objects;

import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.util.Log;

import java.lang.reflect.InvocationTargetException;

public class LiveObjectsHelper {

    private static final String TAG = LiveObjectsHelper.class.getName();
    private static volatile LiveObjectSerializer liveObjectSerializer;

    public static LiveObjectsPlugin tryInitializeLiveObjectsPlugin(AblyRealtime ablyRealtime) {
        try {
            Class<?> liveObjectsImplementation = Class.forName("io.ably.lib.objects.DefaultLiveObjectsPlugin");
            LiveObjectsAdapter adapter = new Adapter(ablyRealtime);
            return (LiveObjectsPlugin) liveObjectsImplementation
                .getDeclaredConstructor(LiveObjectsAdapter.class)
                .newInstance(adapter);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            Log.i(TAG, "LiveObjects plugin not found in classpath. LiveObjects functionality will not be available.", e);
            return null;
        }
    }

    public static LiveObjectSerializer getLiveObjectSerializer() {
        if (liveObjectSerializer == null) {
            synchronized (LiveObjectsHelper.class) {
                if (liveObjectSerializer == null) { // Double-Checked Locking (DCL)
                    try {
                        Class<?> serializerClass = Class.forName("io.ably.lib.objects.serialization.DefaultLiveObjectSerializer");
                        liveObjectSerializer = (LiveObjectSerializer) serializerClass.getDeclaredConstructor().newInstance();
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                             NoSuchMethodException |
                             InvocationTargetException e) {
                        Log.w(TAG, "Failed to init LiveObjectSerializer, LiveObjects plugin not included in the classpath", e);
                        return null;
                    }
                }
            }
        }
        return liveObjectSerializer;
    }
}
