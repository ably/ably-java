package io.ably.lib.objects.unit

import io.ably.lib.objects.ObjectId
import io.ably.lib.objects.type.ObjectType
import io.ably.lib.types.AblyException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
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
  fun testInvalidObjectType() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString("invalid:abc123@1640995200000")
    }
    assertAblyExceptionError(exception)
  }

  @Test
  fun testNullOrEmptyObjectId() {
    val exception = assertThrows(AblyException::class.java) {
      ObjectId.fromString(null)
    }
    assertAblyExceptionError(exception)
    val exception1 = assertThrows(AblyException::class.java) {
      ObjectId.fromString("")
    }
    assertAblyExceptionError(exception1)
  }

  private fun assertAblyExceptionError(
    exception: AblyException
  ) {
    assertTrue(exception.errorInfo?.message?.contains("Invalid object id:") == true ||
               exception.errorInfo?.message?.contains("Invalid object type in object id:") == true)
    assertEquals(92_000, exception.errorInfo?.code)
    assertEquals(500, exception.errorInfo?.statusCode)
  }
}
