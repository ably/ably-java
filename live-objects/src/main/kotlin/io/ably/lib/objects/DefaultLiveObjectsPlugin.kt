package io.ably.lib.objects

public class DefaultLiveObjectsPlugin : LiveObjectsPlugin {

  private val cache = mutableMapOf<String, LiveObjects>()

  override fun getInstance(channelName: String): LiveObjects {
    return cache.getOrPut(channelName) { DefaultLiveObjects(channelName) }
  }

  override fun dispose(channelName: String) {
    cache.remove(channelName)
  }
}
