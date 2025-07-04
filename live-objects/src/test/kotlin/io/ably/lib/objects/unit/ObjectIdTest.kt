package io.ably.lib.objects.unit

import io.ably.lib.objects.ObjectId
import io.ably.lib.objects.ObjectType
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObjectIdTest {

  @Test
  fun testValidMapObjectId() {
    val objectIdString = "map:abc123@1640995200000"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Map, objectId.type)
    assertEquals("map:abc123@1640995200000", objectId.toString())
  }

  @Test
  fun testValidCounterObjectId() {
    val objectIdString = "counter:def456@1640995200000"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Counter, objectId.type)
    assertEquals("counter:def456@1640995200000", objectId.toString())
  }

  @Test
  fun testObjectIdWithComplexHash() {
    val objectIdString = "map:abc123-def456_ghi789@1640995200000"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Map, objectId.type)
    assertEquals("map:abc123-def456_ghi789@1640995200000", objectId.toString())
  }

  @Test
  fun testObjectIdWithLargeTimestamp() {
    val objectIdString = "counter:test@9999999999999"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Counter, objectId.type)
    assertEquals("counter:test@9999999999999", objectId.toString())
  }

  @Test
  fun testObjectIdWithZeroTimestamp() {
    val objectIdString = "map:test@0"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Map, objectId.type)
    assertEquals("map:test@0", objectId.toString())
  }

  @Test
  fun testObjectIdWithNegativeTimestamp() {
    val objectIdString = "counter:test@-1640995200000"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Counter, objectId.type)
    assertEquals("counter:test@-1640995200000", objectId.toString())
  }

  @Test
  fun testNullObjectId() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString(null)
    }

    assertTrue(exception.message?.contains("Invalid object id: null") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testEmptyObjectId() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("")
    }

    assertTrue(exception.message?.contains("Invalid object id: ") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testBlankObjectId() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("   ")
    }

    assertTrue(exception.message?.contains("Invalid object id:    ") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithoutColon() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("mapabc123@1640995200000")
    }

    assertTrue(exception.message?.contains("Invalid object id: mapabc123@1640995200000") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithMultipleColons() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:abc:123@1640995200000")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:abc:123@1640995200000") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testInvalidObjectType() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("invalid:abc123@1640995200000")
    }

    assertTrue(exception.message?.contains("Invalid object type in object id: invalid:abc123@1640995200000") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithoutAtSymbol() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:abc1231640995200000")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:abc1231640995200000") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithMultipleAtSymbols() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:abc123@1640995200000@extra")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:abc123@1640995200000@extra") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithEmptyHash() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:@1640995200000")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:@1640995200000") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithEmptyTimestamp() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:abc123@")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:abc123@") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithNonNumericTimestamp() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:abc123@invalid")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:abc123@invalid") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithDecimalTimestamp() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:abc123@1640995200000.5")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:abc123@1640995200000.5") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithTimestampExceedingLongMaxValue() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:abc123@9223372036854775808")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:abc123@9223372036854775808") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithUnicodeCharactersInHash() {
    val objectIdString = "counter:测试hash@1640995200000"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Counter, objectId.type)
    assertEquals("counter:测试hash@1640995200000", objectId.toString())
  }

  @Test
  fun testObjectIdWithVeryLongHash() {
    val longHash = "a".repeat(1000)
    val objectIdString = "map:$longHash@1640995200000"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Map, objectId.type)
    assertEquals("map:$longHash@1640995200000", objectId.toString())
  }

  @Test
  fun testObjectIdWithVeryLongTimestamp() {
    val objectIdString = "counter:test@1234567890123456789"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Counter, objectId.type)
    assertEquals("counter:test@1234567890123456789", objectId.toString())
  }

  @Test
  fun testObjectIdCaseSensitivity() {
    // Test that object types are case-sensitive
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("MAP:abc123@1640995200000")
    }

    assertTrue(exception.message?.contains("Invalid object type in object id: MAP:abc123@1640995200000") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithWhitespace() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString(" map:abc123@1640995200000")
    }

    assertTrue(exception.message?.contains("Invalid object id:  map:abc123@1640995200000") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithTrailingWhitespace() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:abc123@1640995200000 ")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:abc123@1640995200000 ") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithOnlyType() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithOnlyTypeAndHash() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:abc123")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:abc123") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithOnlyTypeAndAtSymbol() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:@")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:@") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithOnlyTypeAndTimestamp() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("map:1640995200000")
    }

    assertTrue(exception.message?.contains("Invalid object id: map:1640995200000") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }

  @Test
  fun testObjectIdWithHashContainingAtSymbol() {
    val objectIdString = "map:abc@123@1640995200000"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Map, objectId.type)
    assertEquals("map:abc@123@1640995200000", objectId.toString())
  }

  @Test
  fun testObjectIdWithHashContainingColon() {
    val objectIdString = "map:abc:123@1640995200000"
    val objectId = ObjectId.fromString(objectIdString)

    assertEquals(ObjectType.Map, objectId.type)
    assertEquals("map:abc:123@1640995200000", objectId.toString())
  }

  @Test
  fun testObjectIdRoundTrip() {
    val originalString = "map:abc123@1640995200000"
    val objectId = ObjectId.fromString(originalString)
    val roundTripString = objectId.toString()

    assertEquals(originalString, roundTripString)
  }


  @Test
  fun testObjectIdRoundTripWithUnicode() {
    val originalString = "map:测试hash@1640995200000"
    val objectId = ObjectId.fromString(originalString)
    val roundTripString = objectId.toString()

    assertEquals(originalString, roundTripString)
  }
}
