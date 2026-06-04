package io.ably.lib.objects.path

import io.ably.lib.objects.DefaultRealtimeObjects
import io.ably.lib.objects.ObjectsCallback
import io.ably.lib.types.AblyException

/**
 * Default [ChannelObject] backed by the existing [DefaultRealtimeObjects].
 *
 * [get] / [getAsync] delegate to `RealtimeObjects.getRoot` (the one blocking
 * call — it waits for the initial sync) and wrap the resulting LiveMap in a
 * [DefaultRootPathObject].
 */
internal class DefaultChannelObject(
    private val realtimeObjects: DefaultRealtimeObjects,
) : ChannelObject {

    override fun get(): RootPathObject {
        // Trigger sync by calling getRoot(); the LiveMap is the root in the pool.
        realtimeObjects.getRoot()
        return DefaultRootPathObject(realtimeObjects)
    }

    override fun getAsync(callback: ObjectsCallback<RootPathObject>) {
        realtimeObjects.getRootAsync(object : ObjectsCallback<io.ably.lib.objects.type.map.LiveMap> {
            override fun onSuccess(result: io.ably.lib.objects.type.map.LiveMap) {
                callback.onSuccess(DefaultRootPathObject(realtimeObjects))
            }
            override fun onError(exception: AblyException) {
                callback.onError(exception)
            }
        })
    }
}
