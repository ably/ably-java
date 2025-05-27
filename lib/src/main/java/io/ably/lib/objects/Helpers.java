package io.ably.lib.objects;

import io.ably.lib.util.Log;

public class Helpers {

    private static final String TAG = Helpers.class.getName();
    public static final LiveObjectsSerializer liveObjectsSerializer = getLiveObjectsSerializer();

    private static LiveObjectsSerializer getLiveObjectsSerializer() {
        try {
            // Replace with the fully qualified name of the implementing class
            Class<?> clazz = Class.forName("io.ably.lib.objects.DefaultLiveObjectsSerializer");
            return (LiveObjectsSerializer) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            // log the error using Log.e
            Log.e(TAG, ": Failed to create LiveObjectsSerializer instance", e);
            return null;
        }
    }
}
