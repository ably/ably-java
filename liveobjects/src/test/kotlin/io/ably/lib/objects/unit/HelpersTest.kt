package io.ably.lib.objects.unit

import io.ably.lib.objects.*
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.transport.ConnectionManager
import io.ably.lib.types.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class HelpersTest {

  // sendAsync
  @Test
  fun testSendAsyncShouldQueueAccordingToClientOptions() = runTest {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val connManager = mockk<ConnectionManager>(relaxed = true)
    val clientOptions = ClientOptions().apply { queueMessages = false }

    every { adapter.connection } returns mockk(relaxed = true) {
      setPrivateField("connectionManager", connManager)
    }
    every { adapter.clientOptions } returns clientOptions

    every { connManager.send(any(), any(), any()) } answers {
      val listener = thirdArg<CompletionListener>()
      listener.onSuccess()
    }

    val pm = ProtocolMessage(ProtocolMessage.Action.message)
    adapter.sendAsync(pm)
    verify(exactly = 1) { connManager.send(any(), false, any()) }

    clientOptions.queueMessages = true
    adapter.sendAsync(pm)
    verify(exactly = 1) { connManager.send(any(), true, any()) }
  }

  @Test
  fun testSendAsyncErrorPropagatesAblyException() = runTest {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val connManager = mockk<ConnectionManager>(relaxed = true)
    val clientOptions = ClientOptions()

    every { adapter.connection } returns mockk(relaxed = true) {
      setPrivateField("connectionManager", connManager)
    }
    every { adapter.clientOptions } returns clientOptions

    every { connManager.send(any(), any(), any()) } answers {
      val listener = thirdArg<CompletionListener>()
      listener.onError(clientError("boom").errorInfo)
    }

    val ex = assertFailsWith<AblyException> {
      adapter.sendAsync(ProtocolMessage(ProtocolMessage.Action.message))
    }
    assertEquals(400, ex.errorInfo.statusCode)
    assertEquals(40000, ex.errorInfo.code)
  }

  @Test
  fun testSendAsyncThrowsWhenConnectionManagerThrows() = runTest {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val connManager = mockk<ConnectionManager>(relaxed = true)
    val clientOptions = ClientOptions()

    every { adapter.connection } returns mockk(relaxed = true) {
      setPrivateField("connectionManager", connManager)
    }
    every { adapter.clientOptions } returns clientOptions

    every { connManager.send(any(), any(), any()) } throws RuntimeException("send failed hard")

    val ex = assertFailsWith<RuntimeException> {
      adapter.sendAsync(ProtocolMessage(ProtocolMessage.Action.message))
    }
    assertEquals("send failed hard", ex.message)
  }

  // attachAsync
  @Test
  fun testAttachAsyncSuccess() = runTest {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.attach(any()) } answers {
      val listener = firstArg<CompletionListener>()
      listener.onSuccess()
    }

    adapter.attachAsync("ch")
    verify(exactly = 1) { channel.attach(any()) }
  }

  @Test
  fun testAttachAsyncError() = runTest {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.attach(any()) } answers {
      val listener = firstArg<CompletionListener>()
      listener.onError(serverError("attach failed").errorInfo)
    }

    val ex = assertFailsWith<AblyException> { adapter.attachAsync("ch") }
    assertEquals(50000, ex.errorInfo.code)
    assertEquals(500, ex.errorInfo.statusCode)
  }

  // getChannelModes
  @Test
  fun testGetChannelModesPrefersChannelModes() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.modes } returns arrayOf(ChannelMode.object_publish)
    every { channel.options } returns ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe) }

    val modes = adapter.getChannelModes("ch")
    assertArrayEquals(arrayOf(ChannelMode.object_publish), modes)
  }

  @Test
  fun testGetChannelModesFallsBackToOptions() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.modes } returns emptyArray()
    every { channel.options } returns ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe) }

    val modes = adapter.getChannelModes("ch")
    assertArrayEquals(arrayOf(ChannelMode.object_subscribe), modes)
  }

  @Test
  fun testGetChannelModesReturnsNullWhenNoModes() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.modes } returns null
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val modes = adapter.getChannelModes("ch")
    assertNull(modes)
  }

  @Test
  fun testGetChannelModesIgnoresEmptyModes() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    every { channel.modes } returns emptyArray()
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val modes = adapter.getChannelModes("ch")
    assertNull(modes)
  }

  // setChannelSerial
  @Test
  fun testSetChannelSerialSetsWhenObjectActionAndNonEmpty() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    val props = ChannelProperties()
    channel.properties = props
    every { adapter.getChannel("ch") } returns channel

    val msg = ProtocolMessage(ProtocolMessage.Action.`object`)
    msg.channelSerial = "abc:123"

    adapter.setChannelSerial("ch", msg)
    assertEquals("abc:123", props.channelSerial)
  }

  @Test
  fun testSetChannelSerialNoOpForNonObjectActionOrEmpty() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    val props = ChannelProperties()
    channel.properties = props
    every { adapter.getChannel("ch") } returns channel

    // Non-object action
    val msg1 = ProtocolMessage(ProtocolMessage.Action.message)
    msg1.channelSerial = "abc"
    adapter.setChannelSerial("ch", msg1)
    assertNull(props.channelSerial)

    // Object action but empty serial
    val msg2 = ProtocolMessage(ProtocolMessage.Action.`object`)
    msg2.channelSerial = ""
    adapter.setChannelSerial("ch", msg2)
    assertNull(props.channelSerial)
  }

  // ensureAttached
  @Test
  fun testEnsureAttachedFromInitializedAttaches() = runTest {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)

    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.initialized

    val attachCalled = slot<CompletionListener>()
    every { channel.attach(capture(attachCalled)) } answers {
      attachCalled.captured.onSuccess()
    }

    adapter.ensureAttached("ch")
    verify(exactly = 1) { channel.attach(any()) }
  }

  @Test
  fun testEnsureAttachedWhenAlreadyAttachedReturns() = runTest {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attached

    adapter.ensureAttached("ch")
    // no attach call
    verify(exactly = 0) { channel.attach(any()) }
  }

  @Test
  fun testEnsureAttachedWaitsForAttachingThenAttached() = runTest {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attaching

    every { channel.once(any()) } answers {
      val listener = firstArg<ChannelStateListener>()
      val stateChange = mockk<ChannelStateListener.ChannelStateChange>(relaxed = true) {
        setPrivateField("current", ChannelState.attached)
      }
      listener.onChannelStateChanged(stateChange)
    }

    adapter.ensureAttached("ch")
    verify(exactly = 1) { channel.once(any()) }
  }

   @Test
   fun testEnsureAttachedAttachingButReceivesNonAttachedEmitsError() = runTest {
     val adapter = mockk<ObjectsAdapter>(relaxed = true)
     val channel = mockk<Channel>(relaxed = true)
     every { adapter.getChannel("ch") } returns channel
     channel.state = ChannelState.attaching
     every { channel.once(any()) } answers {
       val listener = firstArg<ChannelStateListener>()
       val stateChange = mockk<ChannelStateListener.ChannelStateChange>(relaxed = true) {
         setPrivateField("current", ChannelState.suspended)
         setPrivateField("reason", clientError("Not attached").errorInfo)
       }
       listener.onChannelStateChanged(stateChange)
     }
     val ex = assertFailsWith<AblyException> { adapter.ensureAttached("ch") }
      assertEquals(ErrorCode.ChannelStateError.code, ex.errorInfo.code)
      assertTrue(ex.errorInfo.message.contains("Not attached"))
      verify(exactly = 1) { channel.once(any()) }
   }

  @Test
  fun testEnsureAttachedThrowsForInvalidState() = runTest {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.failed

    val ex = assertFailsWith<AblyException> { adapter.ensureAttached("ch") }
    assertEquals(ErrorCode.ChannelStateError.code, ex.errorInfo.code)
  }

  // throwIfInvalidAccessApiConfiguration
  @Test
  fun testThrowIfInvalidAccessApiConfigurationStateDetached() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.detached

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidAccessApiConfiguration("ch") }
    assertEquals(ErrorCode.ChannelStateError.code, ex.errorInfo.code)
  }

  @Test
  fun testThrowIfInvalidAccessApiConfigurationMissingMode() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attached
    every { channel.modes } returns emptyArray()
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidAccessApiConfiguration("ch") }
    assertEquals(ErrorCode.ChannelModeRequired.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("object_subscribe"))
  }

  // throwIfInvalidWriteApiConfiguration
  @Test
  fun testThrowIfInvalidWriteApiConfigurationEchoDisabled() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val clientOptions = ClientOptions().apply { echoMessages = false }
    every { adapter.clientOptions } returns clientOptions

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ErrorCode.BadRequest.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("echoMessages"))
  }

  @Test
  fun testThrowIfInvalidWriteApiConfigurationInvalidState() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    every { adapter.clientOptions } returns ClientOptions()
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.suspended

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ErrorCode.ChannelStateError.code, ex.errorInfo.code)
  }

  @Test
  fun testThrowIfInvalidWriteApiConfigurationMissingMode() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    every { adapter.clientOptions } returns ClientOptions()
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attached
    every { channel.modes } returns emptyArray()
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ErrorCode.ChannelModeRequired.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("object_publish"))
  }

  // throwIfUnpublishableState
  @Test
  fun testThrowIfUnpublishableStateInactiveConnection() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val connManager = mockk<ConnectionManager>(relaxed = true)
    every { adapter.connection } returns mockk(relaxed = true) {
      setPrivateField("connectionManager", connManager)
    }
    every { connManager.isActive } returns false
    every { connManager.stateErrorInfo } returns serverError("not active").errorInfo

    val ex = assertFailsWith<AblyException> { adapter.throwIfUnpublishableState("ch") }
    assertEquals(500, ex.errorInfo.statusCode)
    assertEquals(50000, ex.errorInfo.code)
  }

  @Test
  fun testThrowIfUnpublishableStateChannelFailed() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val connManager = mockk<ConnectionManager>(relaxed = true)
    every { adapter.connection } returns mockk(relaxed = true) {
      setPrivateField("connectionManager", connManager)
    }
    every { connManager.isActive } returns true
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.failed

    val ex = assertFailsWith<AblyException> { adapter.throwIfUnpublishableState("ch") }
    assertEquals(ErrorCode.ChannelStateError.code, ex.errorInfo.code)
  }

  @Test
  fun testAccessConfigThrowsWhenRequiredModeMissing() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attached
    // No modes anywhere
    every { channel.modes } returns null
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidAccessApiConfiguration("ch") }
    assertEquals(ErrorCode.ChannelModeRequired.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("object_subscribe"))
  }

  @Test
  fun testWriteConfigThrowsWhenRequiredModeMissing() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    every { adapter.clientOptions } returns ClientOptions() // echo enabled
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.attached
    every { channel.modes } returns emptyArray()
    every { channel.options } returns ChannelOptions().apply { modes = null }

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ErrorCode.ChannelModeRequired.code, ex.errorInfo.code)
    assertTrue(ex.errorInfo.message.contains("object_publish"))
  }

  @Test
  fun testAccessConfigThrowsOnInvalidChannelState() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel
    channel.state = ChannelState.detached

    val ex = assertFailsWith<AblyException> { adapter.throwIfInvalidAccessApiConfiguration("ch") }
    assertEquals(ErrorCode.ChannelStateError.code, ex.errorInfo.code)
  }

  @Test
  fun testWriteConfigThrowsOnInvalidChannelStates() {
    val adapter = mockk<ObjectsAdapter>(relaxed = true)
    every { adapter.clientOptions } returns ClientOptions()
    val channel = mockk<Channel>(relaxed = true)
    every { adapter.getChannel("ch") } returns channel

    // Suspended should be rejected for write config
    channel.state = ChannelState.suspended
    val ex1 = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ErrorCode.ChannelStateError.code, ex1.errorInfo.code)

    // Failed should also be rejected
    channel.state = ChannelState.failed
    val ex2 = assertFailsWith<AblyException> { adapter.throwIfInvalidWriteApiConfiguration("ch") }
    assertEquals(ErrorCode.ChannelStateError.code, ex2.errorInfo.code)
  }

  // Binary utilities
  @Test
  fun testBinaryEqualityHashCodeAndSize() {
    val data1 = byteArrayOf(1, 2, 3, 4)
    val data2 = byteArrayOf(1, 2, 3, 4)
    val data3 = byteArrayOf(4, 3, 2, 1)

    val b1 = Binary(data1)
    val b2 = Binary(data2)
    val b3 = Binary(data3)

    assertEquals(b1, b2)
    assertEquals(b1.hashCode(), b2.hashCode())
    assertNotEquals(b1, b3)

    assertEquals(4, b1.size())
  }
}
