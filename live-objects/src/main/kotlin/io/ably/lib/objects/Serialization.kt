package io.ably.lib.objects

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker

internal val gson: Gson = createGsonSerializer()

private fun createGsonSerializer(): Gson {
  return GsonBuilder().create() // Do not call serializeNulls() to omit null values
}

internal class DefaultLiveObjectSerializer : LiveObjectSerializer {
  override fun readMsgpackArray(unpacker: MessageUnpacker): Array<Any> {
    TODO("Not yet implemented")
  }

  override fun writeMsgpackArray(objects: Array<out Any>?, packer: MessagePacker) {
    TODO("Not yet implemented")
  }

  override fun readFromJsonArray(json: JsonArray): Array<Any> {
    TODO("Not yet implemented")
  }

  override fun asJsonArray(objects: Array<out Any>?): JsonArray {
    TODO("Not yet implemented")
  }
}
