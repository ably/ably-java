package io.ably.lib.objects.unit.type.livemap

import io.ably.lib.objects.*
import io.ably.lib.objects.type.livemap.LiveMapEntry
import io.ably.lib.objects.type.livemap.LiveMapManager
import io.ably.lib.objects.type.map.LiveMapUpdate
import io.ably.lib.objects.unit.LiveMapManager
import io.ably.lib.objects.unit.getDefaultLiveMapWithMockedDeps
import io.ably.lib.types.AblyException
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.*
import kotlin.test.*

class LiveMapManagerTest {

  private val livemapManager = LiveMapManager(mockk(relaxed = true))

  @Test
  fun `(RTLM6, RTLM6b, RTLM6c) DefaultLiveMap should override map data with state from sync`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "1",
      data = ObjectData(value = ObjectValue("oldValue"))
    )

    val objectState = ObjectState(
      objectId = "map:testMap@1",
      map = ObjectMap(
        semantics = MapSemantics.LWW,
        entries = mapOf(
          "key1" to ObjectMapEntry(
            data = ObjectData(value = ObjectValue("newValue1")),
            timeserial = "serial1"
          ),
          "key2" to ObjectMapEntry(
            data = ObjectData(value = ObjectValue("value2")),
            timeserial = "serial2"
          )
        )
      ),
      siteTimeserials = mapOf("site3" to "serial3", "site4" to "serial4"),
      tombstone = false,
    )

    val update = liveMapManager.applyState(objectState)

    assertFalse(liveMap.createOperationIsMerged) // RTLM6b
    assertEquals(2, liveMap.data.size) // RTLM6c
    assertEquals("newValue1", liveMap.data["key1"]?.data?.value?.value) // RTLM6c
    assertEquals("value2", liveMap.data["key2"]?.data?.value?.value) // RTLM6c

    // Assert on update field - should show changes from old to new state
    val expectedUpdate = mapOf(
      "key1" to LiveMapUpdate.Change.UPDATED, // key1 was updated from "oldValue" to "newValue1"
      "key2" to LiveMapUpdate.Change.UPDATED  // key2 was added
    )
    assertEquals(expectedUpdate, update.update)
  }

  @Test
  fun `(RTLM6, RTLM6c) DefaultLiveMap should handle empty map entries in state`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "1",
      data = ObjectData(value = ObjectValue("oldValue"))
    )

    val objectState = ObjectState(
      objectId = "map:testMap@1",
      map = ObjectMap(
        semantics = MapSemantics.LWW,
        entries = emptyMap() // Empty map entries
      ),
      siteTimeserials = mapOf("site1" to "serial1"),
      tombstone = false,
    )

    val update = liveMapManager.applyState(objectState)

    assertFalse(liveMap.createOperationIsMerged) // RTLM6b
    assertEquals(0, liveMap.data.size) // RTLM6c - should be empty map

    // Assert on update field - should show that key1 was removed
    val expectedUpdate = mapOf("key1" to LiveMapUpdate.Change.REMOVED)
    assertEquals(expectedUpdate, update.update)
  }

  @Test
  fun `(RTLM6, RTLM6c) DefaultLiveMap should handle null map in state`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "1",
      data = ObjectData(value = ObjectValue("oldValue"))
    )

    val objectState = ObjectState(
      objectId = "map:testMap@1",
      map = null, // Null map
      siteTimeserials = mapOf("site1" to "serial1"),
      tombstone = false,
    )

    val update = liveMapManager.applyState(objectState)

    assertFalse(liveMap.createOperationIsMerged) // RTLM6b
    assertEquals(0, liveMap.data.size) // RTLM6c - should be empty map when map is null

    // Assert on update field - should show that key1 was removed
    val expectedUpdate = mapOf("key1" to LiveMapUpdate.Change.REMOVED)
    assertEquals(expectedUpdate, update.update)
  }

  @Test
  fun `(RTLM6, RTLM6d) DefaultLiveMap should merge initial data from create operation from state in sync`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "1",
      data = ObjectData(value = ObjectValue("existingValue"))
    )

    val createOp = ObjectOperation(
      action = ObjectOperationAction.MapCreate,
      objectId = "map:testMap@1",
      map = ObjectMap(
        semantics = MapSemantics.LWW,
        entries = mapOf(
          "key1" to ObjectMapEntry(
            data = ObjectData(value = ObjectValue("createValue")),
            timeserial = "serial1"
          ),
          "key2" to ObjectMapEntry(
            data = ObjectData(value = ObjectValue("newValue")),
            timeserial = "serial2"
          )
        )
      )
    )

    val objectState = ObjectState(
      objectId = "map:testMap@1",
      map = ObjectMap(
        semantics = MapSemantics.LWW,
        entries = mapOf(
          "key1" to ObjectMapEntry(
            data = ObjectData(value = ObjectValue("stateValue")),
            timeserial = "serial3"
          )
        )
      ),
      createOp = createOp,
      siteTimeserials = mapOf("site1" to "serial1"),
      tombstone = false,
    )

    // RTLM6d - Merge initial data from create operation
    val update = liveMapManager.applyState(objectState)

    assertEquals(2, liveMap.data.size) // Should have both state and create op entries
    assertEquals("stateValue", liveMap.data["key1"]?.data?.value?.value) // State value takes precedence
    assertEquals("newValue", liveMap.data["key2"]?.data?.value?.value) // Create op value

    // Assert on update field - should show changes from create operation
    val expectedUpdate = mapOf(
      "key1" to LiveMapUpdate.Change.UPDATED, // key1 was updated from "existingValue" to "stateValue"
      "key2" to LiveMapUpdate.Change.UPDATED  // key2 was added from create operation
    )
    assertEquals(expectedUpdate, update.update)
  }


  @Test
  fun `(RTLM15, RTLM15d1, RTLM16) LiveMapManager should apply map create operation`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapCreate,
      objectId = "map:testMap@1",
      map = ObjectMap(
        semantics = MapSemantics.LWW,
        entries = mapOf(
          "key1" to ObjectMapEntry(
            data = ObjectData(value = ObjectValue("value1")),
            timeserial = "serial1"
          ),
          "key2" to ObjectMapEntry(
            data = ObjectData(value = ObjectValue("value2")),
            timeserial = "serial2"
          )
        )
      )
    )

    // RTLM15d1 - Apply map create operation
    liveMapManager.applyOperation(operation, "serial1")

    assertEquals(2, liveMap.data.size) // Should have both entries
    assertEquals("value1", liveMap.data["key1"]?.data?.value?.value) // Should have value1
    assertEquals("value2", liveMap.data["key2"]?.data?.value?.value) // Should have value2
    assertTrue(liveMap.createOperationIsMerged) // Should be marked as merged
  }

  @Test
  fun `(RTLM15, RTLM15d2, RTLM7) LiveMapManager should apply map set operation`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "serial1",
      data = ObjectData(value = ObjectValue("oldValue"))
    )

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapSet,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(
        key = "key1",
        data = ObjectData(value = ObjectValue("newValue"))
      )
    )

    // RTLM15d2 - Apply map set operation
    liveMapManager.applyOperation(operation, "serial2")

    assertEquals("newValue", liveMap.data["key1"]?.data?.value?.value) // RTLM7a2a
    assertEquals("serial2", liveMap.data["key1"]?.timeserial) // RTLM7a2b
    assertFalse(liveMap.data["key1"]?.isTombstoned == true) // RTLM7a2c
  }

  @Test
  fun `(RTLM15, RTLM15d3, RTLM8) LiveMapManager should apply map remove operation`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "serial1",
      data = ObjectData(value = ObjectValue("value1"))
    )

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapRemove,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(key = "key1")
    )

    // RTLM15d3 - Apply map remove operation
    liveMapManager.applyOperation(operation, "serial2")

    assertNull(liveMap.data["key1"]?.data) // RTLM8a2a
    assertEquals("serial2", liveMap.data["key1"]?.timeserial) // RTLM8a2b
    assertTrue(liveMap.data["key1"]?.isTombstoned == true) // RTLM8a2c
  }

  @Test
  fun `(RTLM15, RTLM15d4) LiveMapManager should throw error for unsupported action`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.CounterCreate, // Unsupported action for map
      objectId = "map:testMap@1",
      counter = ObjectCounter(count = 20.0)
    )

    // RTLM15d4 - Should throw error for unsupported action
    val exception = assertFailsWith<AblyException> {
      liveMapManager.applyOperation(operation, "serial1")
    }

    val errorInfo = exception.errorInfo
    assertNotNull(errorInfo)
    assertEquals(92000, errorInfo?.code) // InvalidObject error code
    assertEquals(500, errorInfo?.statusCode) // InternalServerError status code
  }

  @Test
  fun `(RTLM16, RTLM16b) LiveMapManager should skip map create operation if already merged`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set create operation as already merged
    liveMap.createOperationIsMerged = true

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapCreate,
      objectId = "map:testMap@1",
      map = ObjectMap(
        semantics = MapSemantics.LWW,
        entries = mapOf(
          "key1" to ObjectMapEntry(
            data = ObjectData(value = ObjectValue("value1")),
            timeserial = "serial1"
          )
        )
      )
    )

    // RTLM16b - Should skip if already merged
    liveMapManager.applyOperation(operation, "serial1")

    assertEquals(0, liveMap.data.size) // Should not change (still empty)
    assertTrue(liveMap.createOperationIsMerged) // Should remain merged
  }



  @Test
  fun `(RTLM16, RTLM16d, RTLM17) LiveMapManager should merge initial data from create operation`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "serial1",
      data = ObjectData(value = ObjectValue("existingValue"))
    )

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapCreate,
      objectId = "map:testMap@1",
      map = ObjectMap(
        semantics = MapSemantics.LWW,
        entries = mapOf(
          "key1" to ObjectMapEntry(
            data = ObjectData(value = ObjectValue("createValue")),
            timeserial = "serial2"
          ),
          "key2" to ObjectMapEntry(
            data = ObjectData(value = ObjectValue("newValue")),
            timeserial = "serial3"
          ),
          "key3" to ObjectMapEntry(
            data = null,
            timeserial = "serial4",
            tombstone = true
          )
        )
      )
    )

    // RTLM16d - Merge initial data from create operation
    liveMapManager.applyOperation(operation, "serial1")

    assertEquals(3, liveMap.data.size) // Should have all entries
    assertEquals("createValue", liveMap.data["key1"]?.data?.value?.value) // RTLM17a1 - Should be updated
    assertEquals("newValue", liveMap.data["key2"]?.data?.value?.value) // RTLM17a1 - Should be added
    assertTrue(liveMap.data["key3"]?.isTombstoned == true) // RTLM17a2 - Should be tombstoned
    assertTrue(liveMap.createOperationIsMerged) // RTLM17b - Should be marked as merged
  }

  @Test
  fun `(RTLM7, RTLM7b) LiveMapManager should create new entry for map set operation`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapSet,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(
        key = "newKey",
        data = ObjectData(value = ObjectValue("newValue"))
      )
    )

    // RTLM7b - Create new entry
    liveMapManager.applyOperation(operation, "serial1")

    assertEquals(1, liveMap.data.size) // Should have one entry
    assertEquals("newValue", liveMap.data["newKey"]?.data?.value?.value) // RTLM7b1
    assertEquals("serial1", liveMap.data["newKey"]?.timeserial) // Should have serial
    assertFalse(liveMap.data["newKey"]?.isTombstoned == true) // RTLM7b2
  }

  @Test
  fun `(RTLM7, RTLM7a) LiveMapManager should skip map set operation with lower serial`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data with higher serial
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "serial2", // Higher than "serial1"
      data = ObjectData(value = ObjectValue("existingValue"))
    )

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapSet,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(
        key = "key1",
        data = ObjectData(value = ObjectValue("newValue"))
      )
    )

    // RTLM7a - Should skip operation with lower serial
    liveMapManager.applyOperation(operation, "serial1")

    assertEquals("existingValue", liveMap.data["key1"]?.data?.value?.value) // Should not change
    assertEquals("serial2", liveMap.data["key1"]?.timeserial) // Should keep original serial
  }

  @Test
  fun `(RTLM8, RTLM8b) LiveMapManager should create tombstoned entry for map remove operation`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapRemove,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(key = "nonExistingKey")
    )

    // RTLM8b - Create tombstoned entry for non-existing key
    liveMapManager.applyOperation(operation, "serial1")

    assertEquals(1, liveMap.data.size) // Should have one entry
    assertNull(liveMap.data["nonExistingKey"]?.data) // RTLM8b1
    assertEquals("serial1", liveMap.data["nonExistingKey"]?.timeserial) // Should have serial
    assertTrue(liveMap.data["nonExistingKey"]?.isTombstoned == true) // RTLM8b2
  }

  @Test
  fun `(RTLM8, RTLM8a) LiveMapManager should skip map remove operation with lower serial`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data with higher serial
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "serial2", // Higher than "serial1"
      data = ObjectData(value = ObjectValue("existingValue"))
    )

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapRemove,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(key = "key1")
    )

    // RTLM8a - Should skip operation with lower serial
    liveMapManager.applyOperation(operation, "serial1")

    assertEquals("existingValue", liveMap.data["key1"]?.data?.value?.value) // Should not change
    assertEquals("serial2", liveMap.data["key1"]?.timeserial) // Should keep original serial
    assertFalse(liveMap.data["key1"]?.isTombstoned == true) // Should not be tombstoned
  }

  @Test
  fun `(RTLM9, RTLM9b) LiveMapManager should handle null serials correctly`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data with null serial
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = null,
      data = ObjectData(value = ObjectValue("existingValue"))
    )

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapSet,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(
        key = "key1",
        data = ObjectData(value = ObjectValue("newValue"))
      )
    )

    // RTLM9b - Both null serials should be treated as equal
    liveMapManager.applyOperation(operation, null)

    assertEquals("existingValue", liveMap.data["key1"]?.data?.value?.value) // Should not change
  }

  @Test
  fun `(RTLM9, RTLM9d) LiveMapManager should apply operation with serial when entry has null serial`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data with null serial
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = null,
      data = ObjectData(value = ObjectValue("existingValue"))
    )

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapSet,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(
        key = "key1",
        data = ObjectData(value = ObjectValue("newValue"))
      )
    )

    // RTLM9d - Operation serial is greater than missing entry serial
    liveMapManager.applyOperation(operation, "serial1")

    assertEquals("newValue", liveMap.data["key1"]?.data?.value?.value) // Should be updated
    assertEquals("serial1", liveMap.data["key1"]?.timeserial) // Should have new serial
  }

  @Test
  fun `(RTLM9, RTLM9c) LiveMapManager should skip operation with null serial when entry has serial`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data with serial
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "serial1",
      data = ObjectData(value = ObjectValue("existingValue"))
    )

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapSet,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(
        key = "key1",
        data = ObjectData(value = ObjectValue("newValue"))
      )
    )

    // RTLM9c - Missing operation serial is lower than existing entry serial
    liveMapManager.applyOperation(operation, null)

    assertEquals("existingValue", liveMap.data["key1"]?.data?.value?.value) // Should not change
    assertEquals("serial1", liveMap.data["key1"]?.timeserial) // Should keep original serial
  }

  @Test
  fun `(RTLM9, RTLM9e) LiveMapManager should apply operation with higher serial`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data with lower serial
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "serial1",
      data = ObjectData(value = ObjectValue("existingValue"))
    )

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapSet,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(
        key = "key1",
        data = ObjectData(value = ObjectValue("newValue"))
      )
    )

    // RTLM9e - Higher serial should be applied
    liveMapManager.applyOperation(operation, "serial2")

    assertEquals("newValue", liveMap.data["key1"]?.data?.value?.value) // Should be updated
    assertEquals("serial2", liveMap.data["key1"]?.timeserial) // Should have new serial
  }

  @Test
  fun `(RTLM9, RTLM9e) LiveMapManager should skip operation with lower serial`() {
    val liveMap = getDefaultLiveMapWithMockedDeps()
    val liveMapManager = liveMap.LiveMapManager

    // Set initial data with higher serial
    liveMap.data["key1"] = LiveMapEntry(
      isTombstoned = false,
      timeserial = "serial2",
      data = ObjectData(value = ObjectValue("existingValue"))
    )

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapSet,
      objectId = "map:testMap@1",
      mapOp = ObjectMapOp(
        key = "key1",
        data = ObjectData(value = ObjectValue("newValue"))
      )
    )

    // RTLM9e - Lower serial should be skipped
    liveMapManager.applyOperation(operation, "serial1")

    assertEquals("existingValue", liveMap.data["key1"]?.data?.value?.value) // Should not change
    assertEquals("serial2", liveMap.data["key1"]?.timeserial) // Should keep original serial
  }

  @Test
  fun `(RTLM16, RTLM16c) DefaultLiveMap should throw error for mismatched semantics`() {
    val liveMap = getDefaultLiveMapWithMockedDeps("map:testMap@1")
    val liveMapManager = liveMap.LiveMapManager

    val operation = ObjectOperation(
      action = ObjectOperationAction.MapCreate,
      objectId = "map:testMap@1",
      map = ObjectMap(
        semantics = MapSemantics.Unknown, // This should match, but we'll test error case
        entries = emptyMap()
      )
    )

    val exception = assertFailsWith<AblyException> {
      liveMapManager.applyOperation(operation, "serial1")
    }

    val errorInfo = exception.errorInfo
    kotlin.test.assertNotNull(errorInfo) // RTLM16c

    // Assert on error codes
    kotlin.test.assertEquals(92000, exception.errorInfo?.code) // InvalidObject error code
    kotlin.test.assertEquals(500, exception.errorInfo?.statusCode) // InternalServerError status code
  }

  @Test
  fun shouldCalculateMapDifferenceCorrectly() {
    // Test case 1: No changes
    val prevData1 = mapOf<String, LiveMapEntry>()
    val newData1 = mapOf<String, LiveMapEntry>()
    val result1 = livemapManager.calculateUpdateFromDataDiff(prevData1, newData1)
    assertEquals("Should return empty map for no changes", emptyMap<String, LiveMapUpdate.Change>(), result1.update)

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
    assertEquals("Should detect added entry", mapOf("key1" to LiveMapUpdate.Change.UPDATED), result2.update)

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
    assertEquals("Should detect removed entry", mapOf("key1" to LiveMapUpdate.Change.REMOVED), result3.update)

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
    assertEquals("Should detect updated entry", mapOf("key1" to LiveMapUpdate.Change.UPDATED), result4.update)

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
    assertEquals("Should detect tombstoned entry", mapOf("key1" to LiveMapUpdate.Change.REMOVED), result5.update)

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
    assertEquals("Should detect untombstoned entry", mapOf("key1" to LiveMapUpdate.Change.UPDATED), result6.update)

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
    assertEquals("Should not detect change for both tombstoned entries", emptyMap<String, LiveMapUpdate.Change>(), result7.update)

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
    assertEquals("Should not detect change for new tombstoned entry", emptyMap<String, LiveMapUpdate.Change>(), result8.update)

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
      "key1" to LiveMapUpdate.Change.UPDATED,
      "key2" to LiveMapUpdate.Change.REMOVED,
      "key3" to LiveMapUpdate.Change.UPDATED
    )
    assertEquals("Should detect multiple changes correctly", expected9, result9.update)

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
    assertEquals("Should detect objectId change", mapOf("key1" to LiveMapUpdate.Change.UPDATED), result10.update)

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
    assertEquals("Should not detect change for same data", emptyMap<String, LiveMapUpdate.Change>(), result11.update)
  }
}
