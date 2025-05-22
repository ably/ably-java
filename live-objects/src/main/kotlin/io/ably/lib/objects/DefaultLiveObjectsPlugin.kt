package io.ably.lib.objects

import io.ably.lib.plugins.PluginConnectionAdapter
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

public class DefaultLiveObjectsPlugin(private val pluginConnectionAdapter: PluginConnectionAdapter) : LiveObjectsPlugin {

  private val liveObjects = ConcurrentHashMap<String, DefaultLiveObjects>()

  override fun getInstance(channelName: String): LiveObjects {
    return liveObjects.getOrPut(channelName) { DefaultLiveObjects(channelName) }
  }

  public suspend fun send(message: ProtocolMessage) {
    val deferred = CompletableDeferred<Unit>()
    pluginConnectionAdapter.send(message, object : CompletionListener {
      override fun onSuccess() {
        deferred.complete(Unit)
      }

      override fun onError(reason: ErrorInfo) {
        deferred.completeExceptionally(Exception(reason.message))
      }
    })
    deferred.await()
  }

  override fun handle(message: ProtocolMessage) {
    TODO("Not yet implemented")
  }

  override fun dispose(channelName: String) {
    liveObjects[channelName]?.dispose()
    liveObjects.remove(channelName)
  }

  override fun dispose() {
    liveObjects.values.forEach {
      it.dispose()
    }
    liveObjects.clear()
  }
}
