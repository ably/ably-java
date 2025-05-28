package io.ably.lib.objects

import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker

/**
 * Base class for serializing and deserializing live objects.
 * Initializes with a default serializer that uses MessagePack format.
 * Initialized by the LiveObjectsAdapter.
 */
internal class DefaultLiveObjectsSerializer: LiveObjectsSerializer() {
  override fun readMsgpack(unpacker: MessageUnpacker): Any {
    return getObjectMessageFromMsgpack(unpacker)
  }

  override fun writeMsgpack(obj: Any, packer: MessagePacker) {
    if (obj is ObjectMessage) {
      obj.writeMsgpack(packer)
    }
  }
}

/**
 * Extension function to deserialize an ObjectMessage from a MessageUnpacker.
 */
internal fun getObjectMessageFromMsgpack(unpacker: MessageUnpacker): ObjectMessage {
  TODO("Not yet implemented")
}

/**
 * Extension function to serialize an ObjectMessage to a MessagePacker.
 */
private fun ObjectMessage.writeMsgpack(packer: MessagePacker) {
  TODO("Not yet implemented")
}
