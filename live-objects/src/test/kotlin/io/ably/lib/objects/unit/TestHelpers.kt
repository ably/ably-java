package io.ably.lib.objects.unit

import io.ably.lib.objects.*
import io.ably.lib.objects.DefaultLiveObjects
import io.ably.lib.objects.ObjectsManager
import io.ably.lib.objects.type.BaseLiveObject
import io.ably.lib.objects.type.livecounter.DefaultLiveCounter
import io.ably.lib.objects.type.livecounter.LiveCounterManager
import io.ably.lib.objects.type.livemap.DefaultLiveMap
import io.ably.lib.objects.type.livemap.LiveMapManager
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ClientOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

internal fun getMockRealtimeChannel(
  channelName: String,
  clientId: String = "client1",
  channelModes: Array<ChannelMode> = arrayOf(ChannelMode.object_publish, ChannelMode.object_subscribe)): Channel {
    val client = AblyRealtime(ClientOptions().apply {
      autoConnect = false
      key = "keyName:Value"
      this.clientId = clientId
    })
    val channelOpts = ChannelOptions().apply { modes = channelModes }
    val channel = client.channels.get(channelName, channelOpts)
    return spyk(channel) {
      every { attach() } answers {
        state = ChannelState.attached
      }
      every { detach() } answers {
        state = ChannelState.detached
      }
      every { subscribe(any<String>(), any()) } returns mockk(relaxUnitFun = true)
      every { subscribe(any<Array<String>>(), any()) } returns mockk(relaxUnitFun = true)
      every { subscribe(any()) } returns mockk(relaxUnitFun = true)
    }.apply {
      state = ChannelState.attached
    }
}

internal fun getMockLiveObjectsAdapter(): LiveObjectsAdapter {
  return mockk<LiveObjectsAdapter>(relaxed = true)
}

internal fun getMockObjectsPool(): ObjectsPool {
  return mockk<ObjectsPool>(relaxed = true)
}

internal fun ObjectsPool.size(): Int {
  val pool = this.getPrivateField<Map<String, BaseLiveObject>>("pool")
  return pool.size
}

/**
 * ======================================
 * START - DefaultLiveObjects dep mocks
 * ======================================
 */
internal val ObjectsManager.SyncObjectsDataPool: Map<String, ObjectState>
  get() = this.getPrivateField("syncObjectsDataPool")

internal val ObjectsManager.BufferedObjectOperations: List<ObjectMessage>
  get() = this.getPrivateField("bufferedObjectOperations")

internal var DefaultLiveObjects.ObjectsManager: ObjectsManager
  get() = this.getPrivateField("objectsManager")
  set(value) = this.setPrivateField("objectsManager", value)

internal var DefaultLiveObjects.ObjectsPool: ObjectsPool
  get() = this.objectsPool
  set(value) = this.setPrivateField("objectsPool", value)

internal fun getDefaultLiveObjectsWithMockedDeps(
  channelName: String = "testChannelName",
  relaxed: Boolean = false
): DefaultLiveObjects {
  val defaultLiveObjects = DefaultLiveObjects(channelName, getMockLiveObjectsAdapter())
  // mock objectsPool to allow verification of method calls
  if (relaxed) {
    defaultLiveObjects.ObjectsPool = mockk(relaxed = true)
  } else {
    defaultLiveObjects.ObjectsPool = spyk(defaultLiveObjects.objectsPool, recordPrivateCalls = true)
  }
  // mock objectsManager to allow verification of method calls
  if (relaxed) {
    defaultLiveObjects.ObjectsManager = mockk(relaxed = true)
  } else {
    defaultLiveObjects.ObjectsManager = spyk(defaultLiveObjects.ObjectsManager, recordPrivateCalls = true)
  }
  return defaultLiveObjects
}
/**
 * ======================================
 * END - DefaultLiveObjects dep mocks
 * ======================================
 */

/**
 * ======================================
 * START - DefaultLiveCounter dep mocks
 * ======================================
 */
internal var DefaultLiveCounter.LiveCounterManager: LiveCounterManager
  get() = this.getPrivateField("liveCounterManager")
  set(value) = this.setPrivateField("liveCounterManager", value)

internal fun getDefaultLiveCounterWithMockedDeps(
  objectId: String = "counter:testCounter@1",
  relaxed: Boolean = false
): DefaultLiveCounter {
  val defaultLiveCounter = DefaultLiveCounter.zeroValue(objectId, getDefaultLiveObjectsWithMockedDeps())
  if (relaxed) {
    defaultLiveCounter.LiveCounterManager = mockk(relaxed = true)
  } else {
    defaultLiveCounter.LiveCounterManager = spyk(defaultLiveCounter.LiveCounterManager, recordPrivateCalls = true)
  }
  return defaultLiveCounter
}
/**
 * ======================================
 * END - DefaultLiveCounter dep mocks
 * ======================================
 */

/**
 * ======================================
 * START - DefaultLiveMap dep mocks
 * ======================================
 */
internal var DefaultLiveMap.LiveMapManager: LiveMapManager
  get() = this.getPrivateField("liveMapManager")
  set(value) = this.setPrivateField("liveMapManager", value)

internal fun getDefaultLiveMapWithMockedDeps(
  objectId: String = "map:testMap@1",
  relaxed: Boolean = false
): DefaultLiveMap {
  val defaultLiveMap = DefaultLiveMap.zeroValue(objectId, getDefaultLiveObjectsWithMockedDeps())
  if (relaxed) {
    defaultLiveMap.LiveMapManager = mockk(relaxed = true)
  } else {
    defaultLiveMap.LiveMapManager = spyk(defaultLiveMap.LiveMapManager, recordPrivateCalls = true)
  }
  return defaultLiveMap
}
/**
 * ======================================
 * END - DefaultLiveMap dep mocks
 * ======================================
 */
