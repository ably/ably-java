package io.ably.lib.objects

public class DefaultLiveObjectsPlugin(): LiveObjectsPlugin {
  override fun greetings(): String = "Hello from Live Objects module"

  override fun getInstance(channelName: String): LiveObjects = DefaultLiveObjects(channelName)

}
