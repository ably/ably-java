package io.ably.lib.objects

import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.ProtocolMessage
import java.util.concurrent.ConcurrentHashMap

public class DefaultObjectsPlugin(private val adapter: ObjectsAdapter) : ObjectsPlugin {

  private val objects = ConcurrentHashMap<String, DefaultRealtimeObjects>()

  override fun getInstance(channelName: String): RealtimeObjects {
    return objects.getOrPut(channelName) { DefaultRealtimeObjects(channelName, adapter) }
  }

  override fun handle(msg: ProtocolMessage) {
    val channelName = msg.channel
    objects[channelName]?.handle(msg)
  }

  override fun handleStateChange(channelName: String, state: ChannelState, hasObjects: Boolean) {
    objects[channelName]?.handleStateChange(state, hasObjects)
  }

  override fun dispose(channelName: String) {
    objects[channelName]?.dispose(clientError("Channel has been released using channels.release()"))
    objects.remove(channelName)
  }

  override fun dispose() {
    objects.values.forEach {
      it.dispose(clientError("AblyClient has been closed using client.close()"))
    }
    objects.clear()
  }
}
