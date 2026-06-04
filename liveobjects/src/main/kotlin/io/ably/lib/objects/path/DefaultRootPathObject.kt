package io.ably.lib.objects.path

import io.ably.lib.objects.DefaultRealtimeObjects
import io.ably.lib.objects.path.LiveMapInstance
import io.ably.lib.objects.path.RootPathObject

/**
 * The channel root — always a LiveMap. Inherits all of [DefaultPathObject]
 * and tightens [instance] to never return null.
 */
internal class DefaultRootPathObject(
    realtimeObjects: DefaultRealtimeObjects,
) : DefaultPathObject(realtimeObjects, emptyList()), RootPathObject {

    override fun instance(): LiveMapInstance {
        // root is always a LiveMap (spec: every channel has one); `as LiveMapInstance`
        // is safe because DefaultLiveMap now implements LiveMapInstance.
        return realtimeObjects.getRoot() as LiveMapInstance
    }
}
