package io.ably.lib.objects.path.ext

import io.ably.lib.objects.ObjectsCallback
import io.ably.lib.objects.path.ChannelObject
import io.ably.lib.objects.path.RootPathObject
import io.ably.lib.realtime.Channel
import io.ably.lib.types.AblyException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Idiomatic Kotlin access to the path-based LiveObjects API.
 *
 * ```kotlin
 * val root: RootPathObject = channel.realtimeObject.getRoot()
 * ```
 */
public val Channel.realtimeObject: ChannelObject
    get() = this.`object`()

/**
 * Suspending counterpart of [ChannelObject.get] / [ChannelObject.getAsync]
 * — waits for the initial Objects sync without blocking the calling thread.
 */
public suspend fun ChannelObject.getRoot(): RootPathObject =
    suspendCancellableCoroutine { cont ->
        getAsync(object : ObjectsCallback<RootPathObject> {
            override fun onSuccess(result: RootPathObject) = cont.resume(result)
            override fun onError(exception: AblyException)  = cont.resumeWithException(exception)
        })
    }
