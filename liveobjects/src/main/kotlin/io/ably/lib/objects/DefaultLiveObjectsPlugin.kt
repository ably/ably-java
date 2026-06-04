package io.ably.lib.objects

import io.ably.lib.objects.path.ChannelObject
import io.ably.lib.objects.path.DefaultChannelObject
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.ProtocolMessage
import java.util.concurrent.ConcurrentHashMap

public class DefaultLiveObjectsPlugin(private val adapter: ObjectsAdapter) : LiveObjectsPlugin {

  private val objects = ConcurrentHashMap<String, DefaultRealtimeObjects>()
  private val channelObjects = ConcurrentHashMap<String, DefaultChannelObject>()

  override fun getInstance(channelName: String): RealtimeObjects {
    return objects.getOrPut(channelName) { DefaultRealtimeObjects(channelName, adapter) }
  }

  override fun getChannelObject(channelName: String): ChannelObject {
    // Ensure the underlying RealtimeObjects exists; reuse it as the backing.
    val realtimeObjects = objects.getOrPut(channelName) { DefaultRealtimeObjects(channelName, adapter) }
    return channelObjects.getOrPut(channelName) { DefaultChannelObject(realtimeObjects) }
  }

  override fun handle(msg: ProtocolMessage) {
    val channelName = msg.channel
    objects[channelName]?.handle(msg)
  }

  override fun handleStateChange(channelName: String, state: ChannelState, hasObjects: Boolean) {
    objects[channelName]?.handleStateChange(state, hasObjects)
  }

  override fun dispose(channelName: String) {
    channelObjects.remove(channelName)
    objects.remove(channelName)
      ?.dispose(clientError("Channel has been released using channels.release()"))
  }

  override fun dispose() {
    objects.values.forEach {
      it.dispose(clientError("AblyClient has been closed using client.close()"))
    }
    objects.clear()
    channelObjects.clear()
  }
}
