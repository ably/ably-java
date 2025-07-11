package io.ably.lib.objects.unit.type.livemap

import io.ably.lib.objects.*
import io.ably.lib.objects.type.livemap.LiveMapEntry
import io.ably.lib.objects.type.livemap.LiveMapManager
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.*

class LiveMapManagerTest {

  private val livemapManager = LiveMapManager(mockk(relaxed = true))

  @Test
  fun shouldCalculateMapDifferenceCorrectly() {
    // Test case 1: No changes
    val prevData1 = mapOf<String, LiveMapEntry>()
    val newData1 = mapOf<String, LiveMapEntry>()
    val result1 = livemapManager.calculateUpdateFromDataDiff(prevData1, newData1)
    assertEquals("Should return empty map for no changes", emptyMap<String, String>(), result1)

    // Test case 2: Entry added
    val prevData2 = mapOf<String, LiveMapEntry>()
    val newData2 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "1",
        data = ObjectData(value = ObjectValue("value1"))
      )
    )
    val result2 = livemapManager.calculateUpdateFromDataDiff(prevData2, newData2)
    assertEquals("Should detect added entry", mapOf("key1" to "updated"), result2)

    // Test case 3: Entry removed
    val prevData3 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "1",
        data = ObjectData(value = ObjectValue("value1"))
      )
    )
    val newData3 = mapOf<String, LiveMapEntry>()
    val result3 = livemapManager.calculateUpdateFromDataDiff(prevData3, newData3)
    assertEquals("Should detect removed entry", mapOf("key1" to "removed"), result3)

    // Test case 4: Entry updated
    val prevData4 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "1",
        data = ObjectData(value = ObjectValue("value1"))
      )
    )
    val newData4 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "2",
        data = ObjectData(value = ObjectValue("value2"))
      )
    )
    val result4 = livemapManager.calculateUpdateFromDataDiff(prevData4, newData4)
    assertEquals("Should detect updated entry", mapOf("key1" to "updated"), result4)

    // Test case 5: Entry tombstoned
    val prevData5 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "1",
        data = ObjectData(value = ObjectValue("value1"))
      )
    )
    val newData5 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = true,
        timeserial = "2",
        data = null
      )
    )
    val result5 = livemapManager.calculateUpdateFromDataDiff(prevData5, newData5)
    assertEquals("Should detect tombstoned entry", mapOf("key1" to "removed"), result5)

    // Test case 6: Entry untombstoned
    val prevData6 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = true,
        timeserial = "1",
        data = null
      )
    )
    val newData6 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "2",
        data = ObjectData(value = ObjectValue("value1"))
      )
    )
    val result6 = livemapManager.calculateUpdateFromDataDiff(prevData6, newData6)
    assertEquals("Should detect untombstoned entry", mapOf("key1" to "updated"), result6)

    // Test case 7: Both entries tombstoned (noop)
    val prevData7 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = true,
        timeserial = "1",
        data = null
      )
    )
    val newData7 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = true,
        timeserial = "2",
        data = ObjectData(value = ObjectValue("value1"))
      )
    )
    val result7 = livemapManager.calculateUpdateFromDataDiff(prevData7, newData7)
    assertEquals("Should not detect change for both tombstoned entries", emptyMap<String, String>(), result7)

    // Test case 8: New tombstoned entry (noop)
    val prevData8 = mapOf<String, LiveMapEntry>()
    val newData8 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = true,
        timeserial = "1",
        data = null
      )
    )
    val result8 = livemapManager.calculateUpdateFromDataDiff(prevData8, newData8)
    assertEquals("Should not detect change for new tombstoned entry", emptyMap<String, String>(), result8)

    // Test case 9: Multiple changes
    val prevData9 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "1",
        data = ObjectData(value = ObjectValue("value1"))
      ),
      "key2" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "1",
        data = ObjectData(value = ObjectValue("value2"))
      )
    )
    val newData9 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "2",
        data = ObjectData(value = ObjectValue("value1_updated"))
      ),
      "key3" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "1",
        data = ObjectData(value = ObjectValue("value3"))
      )
    )
    val result9 = livemapManager.calculateUpdateFromDataDiff(prevData9, newData9)
    val expected9 = mapOf(
      "key1" to "updated",
      "key2" to "removed",
      "key3" to "updated"
    )
    assertEquals("Should detect multiple changes correctly", expected9, result9)

    // Test case 10: ObjectId references
    val prevData10 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "1",
        data = ObjectData(objectId = "obj1")
      )
    )
    val newData10 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "1",
        data = ObjectData(objectId = "obj2")
      )
    )
    val result10 = livemapManager.calculateUpdateFromDataDiff(prevData10, newData10)
    assertEquals("Should detect objectId change", mapOf("key1" to "updated"), result10)

    // Test case 11: Same data, no change
    val prevData11 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "1",
        data = ObjectData(value = ObjectValue("value1"))
      )
    )
    val newData11 = mapOf(
      "key1" to LiveMapEntry(
        isTombstoned = false,
        timeserial = "2",
        data = ObjectData(value = ObjectValue("value1"))
      )
    )
    val result11 = livemapManager.calculateUpdateFromDataDiff(prevData11, newData11)
    assertEquals("Should not detect change for same data", emptyMap<String, String>(), result11)
  }
}
