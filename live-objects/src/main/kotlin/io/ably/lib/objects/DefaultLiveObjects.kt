package io.ably.lib.objects

import io.ably.lib.types.Callback

internal class DefaultLiveObjects(private val channelName: String): LiveObjects {

  override fun getRoot(): Long = channelName.length.toLong()

  override fun getRootAsync(callback: Callback<Long>) {
    callback.onSuccess(channelName.length.toLong())
  }
}
