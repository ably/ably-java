package io.ably.lib.objects.unit

import io.ably.lib.objects.*
import io.ably.lib.objects.assertWaiter
import io.ably.lib.types.AblyException
import io.ably.lib.types.Callback
import io.ably.lib.types.ErrorInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CancellationException

class UtilsTest {

  @Test
  fun testGenerateNonce() {
    // Test basic functionality
    val nonce1 = generateNonce()
    val nonce2 = generateNonce()

    assertEquals(16, nonce1.length)
    assertEquals(16, nonce2.length)
    assertNotEquals(nonce1, nonce2) // Should be random

    // Test character set
    val validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val nonce = generateNonce()
    nonce.forEach { char ->
      assertTrue("Nonce should only contain valid characters", validChars.contains(char))
    }
  }

  @Test
  fun testStringByteSize() {
    // Test ASCII strings
    assertEquals(5, "Hello".byteSize)
    assertEquals(0, "".byteSize)
    assertEquals(1, "A".byteSize)

    // Test non-ASCII strings
    assertEquals(3, "你".byteSize) // Chinese character
    assertEquals(4, "😊".byteSize) // Emoji
    assertEquals(6, "你好".byteSize) // Two Chinese characters
  }

  @Test
  fun testErrorCreationFunctions() {
    // Test clientError
    val clientEx = clientError("Bad request")
    assertEquals("Bad request", clientEx.errorInfo.message)
    assertEquals(ErrorCode.BadRequest.code, clientEx.errorInfo.code)
    assertEquals(HttpStatusCode.BadRequest.code, clientEx.errorInfo.statusCode)

    // Test serverError
    val serverEx = serverError("Internal error")
    assertEquals("Internal error", serverEx.errorInfo.message)
    assertEquals(ErrorCode.InternalError.code, serverEx.errorInfo.code)
    assertEquals(HttpStatusCode.InternalServerError.code, serverEx.errorInfo.statusCode)

    // Test objectError
    val objectEx = objectError("Invalid object")
    assertEquals("Invalid object", objectEx.errorInfo.message)
    assertEquals(ErrorCode.InvalidObject.code, objectEx.errorInfo.code)
    assertEquals(HttpStatusCode.InternalServerError.code, objectEx.errorInfo.statusCode)

    // Test objectError with cause
    val cause = RuntimeException("Original error")
    val objectExWithCause = objectError("Invalid object", cause)
    assertEquals("Invalid object", objectExWithCause.errorInfo.message)
    assertEquals(cause, objectExWithCause.cause)
  }

  @Test
  fun testAblyExceptionCreation() {
    // Test with error message and codes
    val ex = ablyException("Test error", ErrorCode.BadRequest, HttpStatusCode.BadRequest)
    assertEquals("Test error", ex.errorInfo.message)
    assertEquals(ErrorCode.BadRequest.code, ex.errorInfo.code)
    assertEquals(HttpStatusCode.BadRequest.code, ex.errorInfo.statusCode)

    // Test with ErrorInfo
    val errorInfo = ErrorInfo("Custom error", 400, 40000)
    val ex2 = ablyException(errorInfo)
    assertEquals("Custom error", ex2.errorInfo.message)
    assertEquals(400, ex2.errorInfo.statusCode)
    assertEquals(40000, ex2.errorInfo.code)

    // Test with cause
    val cause = RuntimeException("Cause")
    val ex3 = ablyException(errorInfo, cause)
    assertEquals(cause, ex3.cause)
  }

  @Test
  fun testObjectsAsyncScopeLaunchWithCallback() = runTest {
    val asyncScope = ObjectsAsyncScope("test-channel")
    var callbackExecuted = false
    var resultReceived: String? = null

    val callback = object : Callback<String> {
      override fun onSuccess(result: String) {
        callbackExecuted = true
        resultReceived = result
      }

      override fun onError(errorInfo: ErrorInfo?) {
        fail("Should not call onError for successful execution")
      }
    }

    asyncScope.launchWithCallback(callback) {
      delay(10) // Simulate async work
      "test result"
    }

    // Wait for callback to be executed
    assertWaiter { callbackExecuted }

    assertTrue("Callback should be executed", callbackExecuted)
    assertEquals("test result", resultReceived)
  }

  @Test
  fun testObjectsAsyncScopeLaunchWithCallbackError() = runTest {
    val asyncScope = ObjectsAsyncScope("test-channel")
    var errorReceived: ErrorInfo? = null

    val callback = object : Callback<String> {
      override fun onSuccess(result: String) {
        fail("Should not call onSuccess for error case")
      }

      override fun onError(errorInfo: ErrorInfo?) {
        errorReceived = errorInfo
      }
    }

    asyncScope.launchWithCallback(callback) {
      delay(10)
      throw AblyException.fromErrorInfo(ErrorInfo("Test error", 400, 40000))
    }

    // Wait for error to be received
    assertWaiter { errorReceived != null }

    assertNotNull("Error should be received", errorReceived)
    assertEquals("Test error", errorReceived?.message)
    assertEquals(400, errorReceived?.statusCode)
  }

  @Test
  fun testObjectsAsyncScopeLaunchWithVoidCallback() = runTest {
    val asyncScope = ObjectsAsyncScope("test-channel")
    var callbackExecuted = false

    val callback = object : Callback<Void> {
      override fun onSuccess(result: Void?) {
        callbackExecuted = true
      }

      override fun onError(errorInfo: ErrorInfo?) {
        fail("Should not call onError for successful execution")
      }
    }

    asyncScope.launchWithVoidCallback(callback) {
      delay(10) // Simulate async work
    }

    // Wait for callback to be executed
    assertWaiter { callbackExecuted }

    assertTrue("Callback should be executed", callbackExecuted)
  }

  @Test
  fun testObjectsAsyncScopeLaunchWithVoidCallbackError() = runTest {
    val asyncScope = ObjectsAsyncScope("test-channel")
    var errorReceived: ErrorInfo? = null

    val callback = object : Callback<Void> {
      override fun onSuccess(result: Void?) {
        fail("Should not call onSuccess for error case")
      }

      override fun onError(errorInfo: ErrorInfo?) {
        errorReceived = errorInfo
      }
    }

    asyncScope.launchWithVoidCallback(callback) {
      delay(10)
      throw AblyException.fromErrorInfo(ErrorInfo("Test error", 500, 50000))
    }

    // Wait for error to be received
    assertWaiter { errorReceived != null }

    assertNotNull("Error should be received", errorReceived)
    assertEquals("Test error", errorReceived?.message)
    assertEquals(500, errorReceived?.statusCode)
  }

  @Test
  fun testObjectsAsyncScopeCallbackExceptionHandling() = runTest {
    val asyncScope = ObjectsAsyncScope("test-channel")
    var callback1Called = false
    var callback2Called = false

    val callback1 = object : Callback<String> {
      override fun onSuccess(result: String) {
        callback1Called = true
        throw RuntimeException("Callback exception")
      }

      override fun onError(errorInfo: ErrorInfo?) {
        fail("Should not call onError when onSuccess throws")
      }
    }

    asyncScope.launchWithCallback(callback1) { "test result" }
    // Wait for callback to be called
    assertWaiter { callback1Called }

    val callback2 = object : Callback<String> {
      override fun onSuccess(result: String) {
        callback2Called = true
      }

      override fun onError(errorInfo: ErrorInfo?) {
        fail("Should not call onError when onSuccess throws")
      }
    }

    asyncScope.launchWithCallback(callback2) { "test result" }
    // Callback 2 should be called even if callback 1 throws an exception
    assertWaiter { callback2Called }
  }

  @Test
  fun testObjectsAsyncScopeCancel() = runTest {
    val asyncScope = ObjectsAsyncScope("test-channel")
    var errorReceived = false

    val callback = object : Callback<String> {
      override fun onSuccess(result: String) {
        fail("Should not call onSuccess")
      }

      override fun onError(errorInfo: ErrorInfo?) {
        errorReceived = true
      }
    }

    asyncScope.launchWithCallback(callback) {
      delay(100) // Long delay
      "test result"
    }

    // Cancel immediately
    asyncScope.cancel(CancellationException("Test cancellation"))

    // Wait a bit to ensure cancellation takes effect
    assertWaiter { errorReceived }
  }

  @Test
  fun testObjectsAsyncScopeNonAblyException() = runTest {
    val asyncScope = ObjectsAsyncScope("test-channel")
    var errorReceived = false
    var error: ErrorInfo? = null

    val callback = object : Callback<String> {
      override fun onSuccess(result: String) {
        fail("Should not call onSuccess for error case")
      }

      override fun onError(errorInfo: ErrorInfo?) {
        errorReceived = true
        error = errorInfo
      }
    }

    asyncScope.launchWithCallback(callback) {
      delay(10)
      throw RuntimeException("Non-Ably exception")
    }

    // Wait for error to be received
    assertWaiter { errorReceived }

    // Non-Ably exceptions should result in null errorInfo
    assertNull("Non-Ably exceptions should result in null errorInfo", error)
  }
}
