---
description: "Translate a UTS pseudocode test spec into Kotlin tests in the uts module. Usage: /uts-to-kotlin <path-to-spec-file>"
allowed-tools: Bash, Read, Edit, Write
---

You are translating a UTS pseudocode test spec file into a runnable Kotlin test in the `uts` module. Follow these steps in order.

---

## Step 1 — Read the spec

Read the file at `$ARGUMENTS`. Identify:
- All test cases (each has an ID like `RTN4a`, `RSC1`, etc. and a description)
- The protocol/transport used (WebSocket for Realtime, HTTP for REST)
- Any timer usage (`enable_fake_timers`, `ADVANCE_TIME`)

---

## Step 2 — Determine output path and package

Map the spec path to a test path:

| Spec location | Test location |
|---|---|
| `.../uts/test/rest/unit/<name>.md` | `uts/src/test/kotlin/io/ably/lib/rest/unit/<Name>Test.kt` |
| `.../uts/test/realtime/unit/<sub>/<name>.md` | `uts/src/test/kotlin/io/ably/lib/realtime/unit/<sub>/<Name>Test.kt` |

Class name: take the file name, strip `_test` suffix, convert `snake_case` → `PascalCase`, append `Test`.

Example: `connection_state_machine_test.md` → `ConnectionStateMachineTest`

Package: derived from the output path under `kotlin/`.

---

## Step 3 — Read infrastructure files

Read ALL of these before generating any code (you need exact method signatures):

```
uts/src/test/kotlin/io/ably/lib/ClientFactories.kt
uts/src/test/kotlin/io/ably/lib/test/mock/MockWebSocket.kt
uts/src/test/kotlin/io/ably/lib/test/mock/MockHttpClient.kt
uts/src/test/kotlin/io/ably/lib/test/mock/PendingRequest.kt
uts/src/test/kotlin/io/ably/lib/test/mock/PendingConnection.kt
uts/src/test/kotlin/io/ably/lib/test/mock/FakeClock.kt
uts/src/test/kotlin/io/ably/lib/test/mock/MockEvent.kt
```

---

## Step 4 — Generate the Kotlin test file

Apply the translation rules below, then write the file.

### Client construction

Use `TestRealtimeClient { }` and `TestRestClient { }` from `io.ably.lib`. These are DSL builders that extend `DebugOptions` — all `ClientOptions` fields (`autoConnect`, `recover`, `disconnectedRetryTimeout`, etc.) are settable directly inside the block. The default API key is `"appId.keyId:keySecret"`.

| Pseudocode | Kotlin |
|---|---|
| `Rest(options: ClientOptions(key: "..."))` | `TestRestClient { key = "..." }` |
| `Realtime(options: ClientOptions(key: "...", autoConnect: false))` | `TestRealtimeClient { key = "..."; autoConnect = false }` |
| `Realtime(options: ClientOptions(autoConnect: false))` | `TestRealtimeClient { autoConnect = false }` |
| Installing mocks | `install(mock)` inside the builder block |
| Fake timers | Create `val fakeClock = FakeClock()` before the builder, then call `enableFakeTimers(fakeClock)` inside |

### Mock setup

**Prefer `onConnectionAttempt` callback** over `launch { awaitConnectionAttempt() }` for straightforward connections. Use `respondWithSuccess(message)` to open the socket and deliver the CONNECTED message in a single call.

```kotlin
val mock = MockWebSocket {
    onConnectionAttempt = { conn ->
        conn.respondWithSuccess(ProtocolMessage().apply {
            action = ProtocolMessage.Action.connected
            connectionId = "test-connection-id"
            connectionDetails = ConnectionDetails {
                connectionKey = "test-key"
                maxIdleInterval = 15_000L
                connectionStateTtl = 120_000L
            }
        })
    }
}
val client = TestRealtimeClient {
    autoConnect = false
    install(mock)
}
```

**Use `awaitConnectionAttempt()` only** when different connection attempts need different behaviour (e.g. first attempt succeeds, subsequent ones are refused). In that case, set up the initial connection in a `launch` block before calling `connect()`, then handle reconnections separately:

```kotlin
val mockWs = MockWebSocket()
val client = TestRealtimeClient {
    autoConnect = false
    install(mockWs)
}

launch {
    mockWs.awaitConnectionAttempt().respondWithSuccess(ProtocolMessage().apply { ... })
}

client.connect()
awaitState(client, ConnectionState.connected)

// handle reconnection attempts differently
val refuseJob = launch {
    repeat(10) {
        fakeClock.advance(2.seconds)
        mockWs.awaitConnectionAttempt().respondWithRefused()
        ...
    }
}
```

For HTTP:
```kotlin
val mockHttp = MockHttpClient { onRequest = { req -> req.respondWith(200, body) } }
val client = TestRestClient { install(mockHttp) }
```

### Inspecting outgoing frames (client → server)

`ClientOptionsBuilder` sets `useBinaryProtocol = false`, so the SDK sends JSON text frames. Every outgoing frame is captured as `MockEvent.MessageFromClient` and queued in `awaitNextMessageFromClient()`.

To assert on a message the client sent:

```kotlin
// After triggering the SDK action (e.g. attach):
channelOne.attach()
val msg = mock.awaitNextMessageFromClient()
assertEquals(ProtocolMessage.Action.attach, msg.action)
assertEquals("channel-one", msg.channel)
assertEquals("expected-serial", msg.channelSerial)

// Or filter the full event log when order doesn't matter:
val sent = mock.events
    .filterIsInstance<MockEvent.MessageFromClient>()
    .firstOrNull { it.message.action == ProtocolMessage.Action.attach && it.message.channel == "channel-one" }
assertNotNull(sent)
assertEquals("expected-serial", sent!!.message.channelSerial)
```

### Mock method reference

| Pseudocode | Kotlin |
|---|---|
| `conn.respond_with_success()` | `conn.respondWithSuccess()` (opens socket only) |
| `conn.respond_with_success(msg)` | `conn.respondWithSuccess(msg)` (opens socket + delivers message) |
| `conn.respond_with_refused()` | `conn.respondWithRefused()` |
| `conn.respond_with_timeout()` | `conn.respondWithTimeout()` |
| `conn.respond_with_dns_error()` | `conn.respondWithDnsError()` |
| `mock_ws.send_to_client(msg)` | `mock.sendToClient(msg)` |
| `mock_ws.send_to_client_and_close(msg)` | `mock.sendToClientAndClose(msg)` |
| `mock_ws.simulate_disconnect()` | `mock.simulateDisconnect()` |
| `req.respond_with(200, {...})` | `req.respondWith(200, mapOf(...))` |
| `req.respond_with_timeout()` | `req.respondWithTimeout()` |

### Protocol messages and types

| Pseudocode | Kotlin |
|---|---|
| `ProtocolMessage(action: CONNECTED, ...)` | `ProtocolMessage().apply { action = ProtocolMessage.Action.connected; ... }` |
| `CONNECTED` / `DISCONNECTED` / `ERROR` / `HEARTBEAT` / `ATTACH` / `DETACHED` | `.connected` / `.disconnected` / `.error` / `.heartbeat` / `.attach` / `.detached` |
| `ErrorInfo(code: X, statusCode: Y, message: "...")` | `ErrorInfo("...", Y, X)` — note arg order: message, statusCode, code |
| `ConnectionDetails(connectionKey: ..., maxIdleInterval: ..., connectionStateTtl: ...)` | `ConnectionDetails { connectionKey = "..."; maxIdleInterval = ...; connectionStateTtl = ... }` |
| `ConnectionState.connected` etc. | `ConnectionState.connected`, `.disconnected`, `.suspended`, `.failed`, `.connecting`, `.closing`, `.closed` |

### Awaiting state

`AWAIT_STATE client.connection.state == ConnectionState.X` → use the top-level `awaitState()` helper from `io.ably.lib`:

```kotlin
awaitState(client, ConnectionState.x)            // default 5s timeout
awaitState(client, ConnectionState.x, 10.seconds)
```

For channels: `awaitChannelState(channel, ChannelState.attached)`.

### Timer control

```kotlin
val fakeClock = FakeClock()
val client = TestRealtimeClient {
    autoConnect = false
    install(mock)
    enableFakeTimers(fakeClock)
}

// Advance time — timer callbacks fire synchronously within advance()
fakeClock.advance(30_000)
// or
fakeClock.advance(30.seconds)
```

After `fakeClock.advance()` inside a coroutine, yield to let any newly dispatched coroutines run:

```kotlin
fakeClock.advance(30.seconds)
yield()
```

### Assertions

| Pseudocode | Kotlin |
|---|---|
| `ASSERT x == y` | `assertEquals(y, x)` |
| `ASSERT x IS NOT null` | `assertNotNull(x)` |
| `ASSERT x IS null` | `assertNull(x)` |
| `ASSERT x IS Auth` | `assertIs<Auth>(x)` |
| `ASSERT "key" IN map` | `assertContains(map, "key")` |
| `ASSERT x matches pattern "..."` | `assertTrue(x.matches(Regex("...")))` |
| `ASSERT list CONTAINS_IN_ORDER [a, b, c]` | `val it = list.iterator(); assertEquals(a, it.next()); assertEquals(b, it.next()); ...` |
| `AWAIT expr FAILS WITH error` | `val error = assertFailsWith<AblyException> { expr }; assertEquals(..., error.errorInfo.code)` |
| `ASSERT list.length == N` | `assertEquals(N, list.size)` |

### Test naming and annotation

- KDoc comment immediately above `@Test` using `/** @UTS <spec-id> */` format
- Method name: backtick string `` `<spec-id> - <description>` ``
- Use `runTest { }` from `kotlinx.coroutines.test` for all async tests

```kotlin
/**
 * @UTS realtime/unit/RTN4a/some-description-0
 */
@Test
fun `RTN4a - description of what is being tested`() = runTest {
    ...
}
```

### File template

```kotlin
package io.ably.lib.<category>.unit[.<subcategory>]

import io.ably.lib.TestRealtimeClient          // or TestRestClient
import io.ably.lib.awaitChannelState           // if testing channels
import io.ably.lib.awaitState
import io.ably.lib.realtime.ChannelState       // if testing channels
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.test.mock.FakeClock         // if using fake timers
import io.ably.lib.test.mock.MockWebSocket     // or MockHttpClient
import io.ably.lib.types.ConnectionDetails
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds  // if using Duration literals

class <Name>Test {

    /**
     * @UTS realtime/unit/<spec-id>/<test-slug>
     */
    @Test
    fun `<spec-id> - <description>`() = runTest {
        val mock = MockWebSocket {
            onConnectionAttempt = { conn ->
                conn.respondWithSuccess(ProtocolMessage().apply {
                    action = ProtocolMessage.Action.connected
                    connectionId = "test-connection-id"
                    connectionDetails = ConnectionDetails {
                        connectionKey = "test-key"
                        maxIdleInterval = 15_000L
                        connectionStateTtl = 120_000L
                    }
                })
            }
        }
        val client = TestRealtimeClient {
            autoConnect = false
            install(mock)
        }

        client.connect()
        awaitState(client, ConnectionState.connected)

        assertEquals(ConnectionState.connected, client.connection.state)
        client.close()
    }
}
```

---

## Step 5 — Compile

```bash
./gradlew :uts:compileTestKotlin
```

Fix any compilation errors and recompile until clean. Common issues:
- Missing imports (add them)
- Method names differ from what you read in the mock files (use the exact names you read)
- `ErrorInfo` constructor arg order is `(message, statusCode, code)` — not `(code, statusCode, message)`

---

## Step 6 — Run tests

```bash
./gradlew :uts:test --tests "<package>.<ClassName>"
```

Handle test failures:

1. **UTS spec error** (pseudocode itself is wrong): fix the test to match what the spec intends, add a `// NOTE: spec pseudocode had X, corrected to Y` comment.
2. **Translation error** (you misread the pseudocode): fix silently.
3. **SDK deviation** (confirmed against `uts/spec/features.md` — SDK does not comply):
   - Wrap the failing assertion in an env gate:
     ```kotlin
     if (System.getenv("RUN_DEVIATIONS") != null) {
         assertEquals(specCorrectValue, actualValue)
     }
     ```
   - Add a comment explaining the deviation.
   - Append an entry to `uts/src/test/kotlin/io/ably/lib/deviations.md`:
     - Spec point, what spec requires, what SDK does, which test is affected.