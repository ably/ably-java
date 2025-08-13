package io.ably.lib.objects.unit

import io.ably.lib.objects.*
import io.ably.lib.objects.DefaultRealtimeObjects
import io.ably.lib.objects.ObjectsManager
import io.ably.lib.objects.type.BaseRealtimeObject
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

internal fun getMockObjectsAdapter(): ObjectsAdapter {
  val mockkAdapter = mockk<ObjectsAdapter>(relaxed = true)
  every { mockkAdapter.getChannel(any()) } returns getMockRealtimeChannel("testChannelName")
  return mockkAdapter
}

internal fun getMockObjectsPool(): ObjectsPool {
  return mockk<ObjectsPool>(relaxed = true)
}

internal fun ObjectsPool.size(): Int {
  val pool = this.getPrivateField<Map<String, BaseRealtimeObject>>("pool")
  return pool.size
}

internal val BaseRealtimeObject.TombstonedAt: Long?
  get() = this.getPrivateField("tombstonedAt")

/**
 * ======================================
 * START - DefaultRealtimeObjects dep mocks
 * ======================================
 */
internal val ObjectsManager.SyncObjectsDataPool: Map<String, ObjectState>
  get() = this.getPrivateField("syncObjectsDataPool")

internal val ObjectsManager.BufferedObjectOperations: List<ObjectMessage>
  get() = this.getPrivateField("bufferedObjectOperations")

internal var DefaultRealtimeObjects.ObjectsManager: ObjectsManager
  get() = this.getPrivateField("objectsManager")
  set(value) = this.setPrivateField("objectsManager", value)

internal var DefaultRealtimeObjects.ObjectsPool: ObjectsPool
  get() = this.objectsPool
  set(value) = this.setPrivateField("objectsPool", value)

internal fun getDefaultRealtimeObjectsWithMockedDeps(
  channelName: String = "testChannelName",
  relaxed: Boolean = false
): DefaultRealtimeObjects {
  val defaultRealtimeObjects = DefaultRealtimeObjects(channelName, getMockObjectsAdapter())
  // mock objectsPool to allow verification of method calls
  if (relaxed) {
    defaultRealtimeObjects.ObjectsPool = mockk(relaxed = true)
  } else {
    defaultRealtimeObjects.ObjectsPool = spyk(defaultRealtimeObjects.objectsPool, recordPrivateCalls = true)
  }
  // mock objectsManager to allow verification of method calls
  if (relaxed) {
    defaultRealtimeObjects.ObjectsManager = mockk(relaxed = true)
  } else {
    defaultRealtimeObjects.ObjectsManager = spyk(defaultRealtimeObjects.ObjectsManager, recordPrivateCalls = true)
  }
  return defaultRealtimeObjects
}
/**
 * ======================================
 * END - DefaultRealtimeObjects dep mocks
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
  val defaultLiveCounter = DefaultLiveCounter.zeroValue(objectId, getDefaultRealtimeObjectsWithMockedDeps())
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
  val defaultLiveMap = DefaultLiveMap.zeroValue(objectId, getDefaultRealtimeObjectsWithMockedDeps())
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
