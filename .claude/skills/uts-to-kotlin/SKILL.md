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

## Step 3 — Read mock infrastructure files

Read ALL of these before generating any code (you need exact method signatures):

```
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

| Pseudocode | Kotlin |
|---|---|
| `Rest(options: ClientOptions(key: "..."))` | `AblyRest(DebugOptions("..."))` |
| `Realtime(options: ClientOptions(key: "...", autoConnect: false))` | `DebugOptions("...").apply { autoConnect = false }.let { AblyRealtime(it) }` |
| `ClientOptions(token: "...", autoConnect: false)` | `DebugOptions().apply { token = "..."; autoConnect = false }` |

### Mock setup — CRITICAL

The pseudocode uses callback-style (`onConnectionAttempt: (conn) => {...}`) but Kotlin mocks use **coroutine await-style**. Each callback body becomes a `launch { ... }` block started **before** the SDK client is created or connected.

| Pseudocode | Kotlin |
|---|---|
| `mock_http = MockHttpClient(...)` + `install_mock(mock_http)` | `val mock = MockHttpClient(); mock.installOn(options)` |
| `mock_ws = MockWebSocket(...)` + `install_mock(mock_ws)` | `val mock = MockWebSocket(); mock.installOn(options)` |
| `onConnectionAttempt: (conn) => { conn.respond_with_success() }` | `launch { val conn = mock.awaitConnectionAttempt(); conn.respondWithSuccess() }` |
| `onRequest: (req) => { req.respond_with(200, body) }` | `launch { val req = mock.awaitRequest(); req.respondWith(200, body) }` |
| Repeated connection attempts | `launch { repeat(N) { val conn = mock.awaitConnectionAttempt(); conn.respondWithRefused() } }` |
| `enable_fake_timers()` | `val clock = FakeClock(); options.clock = clock` (before client construction) |

### Connection/request actions

| Pseudocode | Kotlin |
|---|---|
| `conn.respond_with_success()` | `conn.respondWithSuccess()` |
| `conn.respond_with_refused()` | `conn.respondWithRefused()` |
| `conn.respond_with_timeout()` | `conn.respondWithTimeout()` |
| `conn.respond_with_dns_error()` | `conn.respondWithDnsError()` |
| `conn.send_to_client(msg)` | `mock.sendToClient(msg)` (after `respondWithSuccess()`) |
| `conn.send_to_client_and_close(msg)` | `mock.sendToClientAndClose(msg)` |
| `mock_ws.simulate_disconnect()` | `mock.simulateDisconnect()` |
| `req.respond_with(200, {...})` | `req.respondWith(200, mapOf(...))` |
| `req.respond_with_timeout()` | `req.respondWithTimeout()` |

### Protocol messages and types

| Pseudocode | Kotlin |
|---|---|
| `ProtocolMessage(action: CONNECTED, ...)` | `ProtocolMessage().apply { action = ProtocolMessage.Action.connected; ... }` |
| `CONNECTED` / `DISCONNECTED` / `ERROR` / `HEARTBEAT` / `ATTACH` / `DETACHED` | `.connected` / `.disconnected` / `.error` / `.heartbeat` / `.attach` / `.detached` |
| `ErrorInfo(code: X, statusCode: Y, message: "...")` | `ErrorInfo("...", X, Y)` |
| `ConnectionDetails(connectionKey: ..., maxIdleInterval: ..., connectionStateTtl: ...)` | `ConnectionDetails().apply { connectionKey = "..."; maxIdleInterval = ...; connectionStateTtl = ... }` |
| `ConnectionState.connected` etc. | `ConnectionState.connected`, `.disconnected`, `.suspended`, `.failed`, `.connecting`, `.closing`, `.closed` |

### Awaiting state

`AWAIT_STATE client.connection.state == ConnectionState.X WITH timeout: N seconds` → call the `awaitState()` helper (included in the file template below):

```kotlin
awaitState(client, ConnectionState.x, timeoutMs = N * 1000L)
```

### Timer control

| Pseudocode | Kotlin |
|---|---|
| `enable_fake_timers()` | `val clock = FakeClock()` then `options.clock = clock` |
| `ADVANCE_TIME(ms)` | `clock.advance(ms)` |

After `clock.advance()`, always yield to let the SDK's timer callbacks dispatch:

```kotlin
clock.advance(30_000)
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

### Test naming

- Method name: backtick string `` `<spec-id> - <description>` ``
- Add `// UTS: <test-id>` comment on the line immediately above `@Test`
- Use `runTest { }` from `kotlinx.coroutines.test` for all async tests

### File template

```kotlin
package io.ably.lib.<category>.unit[.<subcategory>]

import io.ably.lib.debug.DebugOptions
import io.ably.lib.realtime.AblyRealtime           // or AblyRest for REST tests
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.realtime.ConnectionStateListener
import io.ably.lib.test.mock.FakeClock
import io.ably.lib.test.mock.MockWebSocket          // or MockHttpClient
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.ErrorInfo
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.resume
import kotlin.test.*

class <Name>Test {

    @AfterTest
    fun tearDown() {
        // close any clients opened in each test (declare them at test scope, not class scope)
    }

    // UTS: <test-id>
    @Test
    fun `<spec-id> - <description>`() = runTest {
        val mock = MockWebSocket()
        val options = DebugOptions("appId.keyId:keySecret").apply {
            autoConnect = false
            mock.installOn(this)
        }

        launch {
            val conn = mock.awaitConnectionAttempt()
            conn.respondWithSuccess()
            mock.sendToClient(ProtocolMessage().apply {
                action = ProtocolMessage.Action.connected
                connectionId = "test-connection-id"
                connectionKey = "test-key"
            })
        }

        val client = AblyRealtime(options)
        client.connect()
        awaitState(client, ConnectionState.connected)

        assertEquals(ConnectionState.connected, client.connection.state)
        client.close()
    }

    private suspend fun awaitState(
        client: AblyRealtime,
        target: ConnectionState,
        timeoutMs: Long = 5000
    ) {
        if (client.connection.state == target) return
        withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = ConnectionStateListener { change ->
                    if (change.current == target && cont.isActive) cont.resume(Unit)
                }
                client.connection.on(listener)
                cont.invokeOnCancellation { client.connection.off(listener) }
            }
        }
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
- `Scheduled` is a top-level class in `FakeClock`, not nested inside `FakeNamedTimer`

---

## Step 6 — Run tests

```bash
./gradlew :uts:test --tests "<package>.<ClassName>Test"
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
