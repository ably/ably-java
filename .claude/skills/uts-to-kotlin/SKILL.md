---
description: "Translate a UTS pseudocode test spec into Kotlin tests in the uts module. Usage: /uts-to-kotlin <path-to-spec-file>"
allowed-tools: Bash, Read, Edit, Write
---

Translate the UTS pseudocode test spec at `$ARGUMENTS` into a runnable Kotlin test in the `uts` module.

Reference: [Writing Derived Tests](https://raw.githubusercontent.com/ably/specification/refs/heads/main/uts/docs/writing-derived-tests.md)

---

## Step 0 — Validate arguments

**If `$ARGUMENTS` is empty or blank**, stop immediately and tell the user:

```
Usage: /uts-to-kotlin <path-to-spec-file>

Example:
  /uts-to-kotlin lib/src/spec/uts/test/realtime/unit/connection/connection_state_machine_test.md

Please re-run the command with the path to a UTS pseudocode spec file.
```

Do not proceed to Step 1.

**If `$ARGUMENTS` is provided but does not end in `.md`**, stop and tell the user:

```
Error: "<value>" does not look like a spec file path (expected a .md file).

Usage: /uts-to-kotlin <path-to-spec-file>
```

Do not proceed to Step 1.

**If `$ARGUMENTS` ends in `.md` but the file does not exist** (check with `test -f "$ARGUMENTS"`), stop and tell the user:

```
Error: file not found: "<value>"

Check the path and try again.
```

Do not proceed to Step 1.

Only continue to Step 1 once the file is confirmed to exist.

---

## Step 1 — Read the spec

Read the file at `$ARGUMENTS`. Identify:
- All test cases — each has a structured ID like `realtime/unit/RSA4c2/callback-error-connecting-disconnected-0` and a description
- The protocol used (WebSocket for Realtime, HTTP for REST)
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

Read ALL of files in `uts/src/test/kotlin/io/ably/lib/uts/infra` before generating any code (you need exact method signatures).

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
    }
}
```

For HTTP:
```kotlin
val mockHttp = MockHttpClient { onRequest = { req -> req.respondWith(200, body) } }
val client = TestRestClient { install(mockHttp) }
```

### Inspecting outgoing frames

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
| `conn.respond_with_success()` | `conn.respondWithSuccess()` |
| `conn.respond_with_success(msg)` | `conn.respondWithSuccess(msg)` |
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
| `ErrorInfo(code: X, statusCode: Y, message: "...")` | `ErrorInfo("...", Y, X)` — arg order: message, statusCode, code |
| `ConnectionDetails(connectionKey: ..., maxIdleInterval: ..., connectionStateTtl: ...)` | `ConnectionDetails { connectionKey = "..."; maxIdleInterval = ...; connectionStateTtl = ... }` |
| `ConnectionState.connected` etc. | `ConnectionState.connected`, `.disconnected`, `.suspended`, `.failed`, `.connecting`, `.closing`, `.closed` |

### Awaiting state

`AWAIT_STATE client.connection.state == ConnectionState.X` → use the top-level `awaitState()` helper:

```kotlin
awaitState(client, ConnectionState.x)             // default 5s timeout
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

After `fakeClock.advance()` inside a coroutine, yield to let newly dispatched coroutines run:

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
- Missing imports
- Method names differ from what you read in the mock files (use the exact names from Step 3)
- `ErrorInfo` constructor arg order is `(message, statusCode, code)`

---

## Step 6 — Run tests

```bash
./gradlew :uts:test --tests "<package>.<ClassName>"
```

Handle test failures using this decision tree (see [reference doc](https://github.com/ably/specification/blob/main/uts/docs/writing-derived-tests.md) for full detail):

```
Test fails
  |
  +-- Does UTS spec match features spec?
  |     NO  → fix test, record UTS spec error in deviations file
  |     YES
  |       +-- Does test accurately translate the UTS spec?
  |             NO  → fix the test (no deviation entry needed)
  |             YES → SDK deviation — adapt test, record in deviations file
```

### Deviation patterns

**Env-gated skip (preferred)** — test contains spec-correct assertions but is skipped by default:

```kotlin
/**
 * @UTS realtime/unit/RSA4c2/callback-error-connecting-disconnected-0
 */
@Test
fun `RSA4c2 - callback error connecting disconnected`() = runTest {
    // DEVIATION: see deviations.md
    if (System.getenv("RUN_DEVIATIONS") == null) return@runTest

    // ... spec-correct setup and assertions ...
}
```

**Adapted assertion** — when you still want to assert on the SDK's actual behaviour to prevent regressions:

```kotlin
// DEVIATION: spec requires error code 40106, SDK returns 40160 — see deviations.md
assertEquals(40160, error.errorInfo.code)
```

**Never use the accommodate-both pattern** (accept either spec or SDK behaviour). Every test must assert either spec behaviour or the SDK's actual behaviour — never both at once.

### Deviations file

Append to `uts/src/test/kotlin/io/ably/lib/deviations.md`. Each entry needs:
1. The spec point (e.g. `RSA4c2`)
2. What the spec says
3. What the SDK does
4. Which test is affected and how it was adapted

---

## Step 7 — Review generated output against the spec

Re-read the original spec file and the generated Kotlin test file side-by-side and check every item below. Fix anything that fails a check before declaring the task done.

### Coverage check — every test case is present

For each test case ID in the spec:
- [ ] A `@Test` method exists with that ID in its `@UTS` KDoc tag
- [ ] The method name contains the spec ID and a meaningful description

Flag any spec test case that has no corresponding `@Test` method as **missing** and implement it.

### Assertion completeness — every `ASSERT` / `AWAIT` is translated

For each `ASSERT`, `AWAIT`, or observable outcome stated in the spec pseudocode:
- [ ] There is a direct Kotlin assertion (`assertEquals`, `assertNotNull`, `assertNull`, `assertIs`, `assertTrue`, `assertFailsWith`, etc.) or an `awaitState` / `awaitChannelState` call covering it
- [ ] No spec assertion has been silently dropped or weakened (e.g. replaced with a comment)

### Setup fidelity — preconditions match the spec

For each test case, verify:
- [ ] Client options (`autoConnect`, `recover`, timeouts, etc.) match the spec's `ClientOptions`
- [ ] Mock responses (success / refused / timeout / DNS error / custom messages) match the spec's prescribed network behaviour
- [ ] Timer setup (`enableFakeTimers`, `fakeClock.advance(...)`) matches every `enable_fake_timers` / `ADVANCE_TIME` in the spec
- [ ] Channel operations (attach, detach, publish) are performed in the order the spec requires

### Deviation honesty

For any place where the generated test diverges from the spec pseudocode (adapted assertion, env-gated skip, or omitted step):
- [ ] A `// DEVIATION:` comment explains why
- [ ] The deviation is recorded in `uts/src/test/kotlin/io/ably/lib/deviations.md`

If you find gaps during this review, fix them and re-run Steps 5–6 before finishing.
