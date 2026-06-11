package io.ably.lib.`object`.unit

import io.ably.lib.`object`.DefaultLiveCounter
import io.ably.lib.`object`.DefaultLiveMap
import io.ably.lib.`object`.WireObjectOperationAction
import io.ably.lib.`object`.evaluate
import io.ably.lib.`object`.value.LiveCounter
import io.ably.lib.`object`.value.LiveMap
import io.ably.lib.`object`.value.LiveMapValue
import io.ably.lib.types.AblyException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Verifies the frozen reflective contract between the lib `LiveMap`/`LiveCounter`
 * value types and this package (RTLMV3/RTLCV3), and the evaluation procedures
 * (RTLMV4/RTLCV4).
 */
internal class ValueTypeContractTest {

  @Test
  fun `lib LiveCounter create reflectively instantiates DefaultLiveCounter`() {
    // exercises lib's Class.forName("io.ably.lib.object.DefaultLiveCounter") path
    val counter = LiveCounter.create(5)
    val impl = assertIs<DefaultLiveCounter>(counter)
    assertEquals(5, impl.count) // RTLCV3b

    val zero = assertIs<DefaultLiveCounter>(LiveCounter.create())
    assertEquals(0, zero.count) // RTLCV3a1 - defaults to 0
  }

  @Test
  fun `lib LiveMap create reflectively instantiates DefaultLiveMap`() {
    // exercises lib's Class.forName("io.ably.lib.object.DefaultLiveMap") path
    val map = LiveMap.create(mapOf("name" to LiveMapValue.of("sachin")))
    val impl = assertIs<DefaultLiveMap>(map)
    assertEquals(setOf("name"), impl.entries.keys) // RTLMV3b

    val empty = assertIs<DefaultLiveMap>(LiveMap.create())
    assertTrue(empty.entries.isEmpty())
  }

  @Test
  fun `counter evaluation produces COUNTER_CREATE message`(): Unit = runBlocking {
    val bridge = FakeBridge()
    val counter = LiveCounter.create(7) as DefaultLiveCounter

    val message = counter.evaluate(bridge) // RTLCV4

    val operation = message.operation!!
    assertEquals(WireObjectOperationAction.CounterCreate, operation.action) // RTLCV4g1
    assertTrue(operation.objectId.startsWith("counter:")) // RTO14
    val create = operation.counterCreateWithObjectId!!
    assertEquals(16, create.nonce.length) // RTLCV4d
    assertEquals(7.0, create.derivedFrom!!.count) // RTLCV4g5
    assertTrue(create.initialValue.contains("7.0")) // RTLCV4c
  }

  @Test
  fun `map evaluation orders nested creates before the parent MAP_CREATE`(): Unit = runBlocking {
    val bridge = FakeBridge()
    val map = LiveMap.create(
      mapOf(
        "score" to LiveMapValue.of(LiveCounter.create(1)),
        "title" to LiveMapValue.of("hello"),
      )
    ) as DefaultLiveMap

    val messages = map.evaluate(bridge) // RTLMV4

    assertEquals(2, messages.size)
    // RTLMV4k - nested COUNTER_CREATE first, the map's own MAP_CREATE last
    assertEquals(WireObjectOperationAction.CounterCreate, messages[0].operation!!.action)
    val mapCreateOperation = messages[1].operation!!
    assertEquals(WireObjectOperationAction.MapCreate, mapCreateOperation.action)
    assertTrue(mapCreateOperation.objectId.startsWith("map:"))
    // RTLMV4d1 - the entry references the nested counter's objectId
    val entries = mapCreateOperation.mapCreateWithObjectId!!.derivedFrom!!.entries
    assertEquals(messages[0].operation!!.objectId, entries["score"]!!.data!!.objectId)
    assertEquals("hello", entries["title"]!!.data!!.string) // RTLMV4d4
  }

  @Test
  fun `invalid counter value fails evaluation with 40003`(): Unit = runBlocking {
    val bridge = FakeBridge()
    val counter = LiveCounter.create(Double.NaN) as DefaultLiveCounter
    val exception = assertFailsWith<AblyException> { counter.evaluate(bridge) } // RTLCV4a
    assertEquals(400, exception.errorInfo.statusCode)
    assertEquals(40003, exception.errorInfo.code)
  }
}
