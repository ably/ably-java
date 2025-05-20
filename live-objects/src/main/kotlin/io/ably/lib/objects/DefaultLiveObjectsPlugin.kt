package io.ably.lib.objects

import java.util.concurrent.ConcurrentHashMap

public class DefaultLiveObjectsPlugin : LiveObjectsPlugin {

  private val liveObjects = ConcurrentHashMap<String, LiveObjects>()

  override fun getInstance(channelName: String): LiveObjects {
    return liveObjects.getOrPut(channelName) { DefaultLiveObjects(channelName) }
  }

  override fun dispose(channelName: String) {
    liveObjects.remove(channelName)
  }
}
