package io.ably.lib.objects.integration.helpers

import io.ably.lib.objects.*
import io.ably.lib.objects.DefaultRealtimeObjects
import io.ably.lib.objects.ObjectMessage
import io.ably.lib.objects.ObjectOperation
import io.ably.lib.objects.type.BaseRealtimeObject
import io.ably.lib.objects.type.counter.LiveCounter
import io.ably.lib.objects.type.map.LiveMap
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.types.ProtocolMessage

internal val LiveMap.ObjectId get() = (this as DefaultLiveMap).objectId

internal val LiveCounter.ObjectId get() = (this as DefaultLiveCounter).objectId

internal val RealtimeObjects.State get() = (this as DefaultRealtimeObjects).state

/**
 * Server runs periodic garbage collection (GC) to remove orphaned objects and will send
 * OBJECT_DELETE events for objects that are no longer referenced.
 * So, we simulate the deletion of an object by sending a ProtocolMessage.
 */
internal fun RealtimeObjects.simulateObjectDelete(baseObject: BaseRealtimeObject) {
  val defaultRealtimeObjects = this as DefaultRealtimeObjects
  val existingSiteCode = baseObject.siteTimeserials.keys.first()
  val existingSiteSerial = baseObject.siteTimeserials[existingSiteCode]!!

  val deleteObjectProtoMsg = ProtocolMessage(ProtocolMessage.Action.`object`, channelName)
  deleteObjectProtoMsg.state = arrayOf(ObjectMessage(
    siteCode = existingSiteCode,
    serial = existingSiteSerial + "1", // Increment serial to accept new operation
    operation = ObjectOperation(
      action = ObjectOperationAction.ObjectDelete,
      objectId = baseObject.objectId,
    )
  ))
  defaultRealtimeObjects.handle(deleteObjectProtoMsg)
}
