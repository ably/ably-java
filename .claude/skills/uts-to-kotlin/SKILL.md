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

Map the spec path to a test path. **Tests are organised tier-first** (`unit/` vs `integration/standard/`
vs `integration/proxy/`), then **by module** (`realtime`, `rest`, `liveobjects`, …), all under the
`io.ably.lib.uts` package. (`…/uts/` below is shorthand for `uts/src/test/kotlin/io/ably/lib/uts/`.)

| Spec location | Test location | Package |
|---|---|---|
| `.../<module>/unit/<…>/<name>.md` | `…/uts/unit/<module>/<Name>Test.kt` | `io.ably.lib.uts.unit.<module>` |
| `.../<module>/integration/<…>/<name>.md` (direct sandbox) | `…/uts/integration/standard/<module>/<Name>Test.kt` | `io.ably.lib.uts.integration.standard.<module>` |
| `.../<module>/integration/<…>/<name>.md` (proxy) | `…/uts/integration/proxy/<module>/<Name>Test.kt` | `io.ably.lib.uts.integration.proxy.<module>` |

`<module>` is the SDK area — `realtime`, `rest`, `liveobjects`, … (existing folders: `unit/realtime/`,
`unit/liveobjects/`). The spec's own `<sub>` grouping (e.g. `connection/`) is **not** carried into a
sub-package — tests sit directly under the tier/module folder (e.g. `connection_recovery_test.md` →
`…/uts/unit/realtime/ConnectionRecoveryTest.kt`).

Class name: take the file name, strip `_test` suffix, convert `snake_case` → `PascalCase`, append `Test`.

**Integration specs come in two kinds:**
- Those that **inject faults** (reference `create_proxy_session()`, proxy `rules`, `trigger_action`, `get_log`, or `uts/test/realtime/integration/helpers/proxy.md`) are **proxy** tests under `integration/proxy/<module>/` — follow the **Proxy integration tests** section at the end of this skill instead of the unit-test rules below.
- Those that exercise only **happy-path interop** against the real sandbox (no fault injection) are **direct sandbox** tests under `integration/standard/<module>/`. They use `SandboxApp` alone (no `ProxySession`), connecting straight to `ProxyManager.sandboxRealtimeHost` / `sandboxRestHost`. *(No example exists yet — model the suite setup/teardown on the proxy section but drop the `ProxySession`/`connectThroughProxy` wiring.)*

Example: `connection_state_machine_test.md` → `ConnectionStateMachineTest`

Package: derived from the output path under `kotlin/`.

---

## Step 3 — Read infrastructure files

Infrastructure is split by tier under `uts/src/test/kotlin/io/ably/lib/uts/infra/`:

- `infra/Utils.kt` — shared async helpers (`awaitState`, `awaitChannelState`, `pollUntil`), package `io.ably.lib.uts.infra`.
- `infra/unit/` — unit-test mocks/factories (`ClientFactories.kt`, `MockWebSocket.kt`, `MockHttpClient.kt`, `FakeClock.kt`, `MockEvent.kt`, the `PendingConnection`/`PendingRequest` pairs, and `Utils.kt` with the `ConnectionDetails { }` builder), package `io.ably.lib.uts.infra.unit`.
- `infra/integration/` + `infra/integration/proxy/` — proxy/sandbox helpers (`SandboxApp.kt`, `ProxyManager.kt`, `ProxySession.kt`) — see the **Proxy integration tests** section.

For a **unit** test, read all files under `infra/unit/` plus `infra/Utils.kt` before generating any code (you need exact method signatures).

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
package io.ably.lib.uts.unit.realtime          // io.ably.lib.uts.unit.<module> — realtime, rest, liveobjects, …

import io.ably.lib.uts.infra.unit.*            // TestRealtimeClient/TestRestClient, MockWebSocket, MockHttpClient, FakeClock, CONNECTED_MESSAGE, ConnectionDetails { } builder
import io.ably.lib.uts.infra.awaitState
import io.ably.lib.uts.infra.awaitChannelState  // if testing channels
import io.ably.lib.uts.infra.pollUntil          // if polling on a predicate
import io.ably.lib.realtime.ChannelState        // if testing channels
import io.ably.lib.realtime.ConnectionState
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

Use the per-tier task that matches what you generated (both are registered in `uts/build.gradle.kts`):

```bash
# Unit test  (io.ably.lib.uts.unit.*)
./gradlew :uts:runUtsUnitTests --tests "io.ably.lib.uts.unit.realtime.<ClassName>"

# Proxy integration test  (io.ably.lib.uts.integration.*)
./gradlew :uts:runUtsIntegrationTests --tests "io.ably.lib.uts.integration.proxy.realtime.<ClassName>"
```

(`./gradlew :uts:test` still runs all tiers — unit, standard, and proxy.)

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

Append to `uts/src/test/kotlin/io/ably/lib/uts/deviations.md`. Each entry needs:
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
- [ ] The deviation is recorded in `uts/src/test/kotlin/io/ably/lib/uts/deviations.md`

If you find gaps during this review, fix them and re-run Steps 5–6 before finishing.

---

## Proxy integration tests

Some specs are **integration tests** that exercise fault-handling behaviour against the **real Ably sandbox** instead of a mocked transport. They route the SDK through the [`ably/uts-proxy`](https://github.com/ably/uts-proxy) — a programmable HTTP/WebSocket proxy that forwards traffic transparently by default but can inject faults (dropped connections, modified/injected/delayed frames, error responses) via rules.

Recognise them by: a reference to `create_proxy_session()`, proxy `rules`, `trigger_action`, `get_log`, or a pointer to `uts/test/realtime/integration/helpers/proxy.md`.

### When proxy tests are the right tool

| Test type | When the spec uses it |
|---|---|
| **Unit test** (mock HTTP/WS — the rest of this skill) | Client-side logic, state machines, request formation, error parsing. Fast, deterministic. |
| **Direct sandbox integration** | Happy-path behaviour (connect, publish, subscribe, presence). No fault injection. |
| **Proxy integration test** | Fault behaviour against the real backend: connection failures, resume, heartbeat starvation, token renewal under network errors, channel error injection. |

### Infrastructure

Three helpers live under `uts/src/test/kotlin/io/ably/lib/uts/infra/integration/`. **Read them before translating a proxy spec** — they hold the exact method signatures.

- **`ProxyManager`** (`infra/integration/proxy/ProxyManager.kt`, package `io.ably.lib.uts.infra.integration.proxy`) — downloads/starts the shared `uts-proxy` process and exposes the sandbox host. Call `ProxyManager.ensureProxy()` once per suite in setup. `ProxyManager.sandboxRealtimeHost` / `sandboxRestHost` are the upstream sandbox hosts (the default target of every session).
- **`ProxySession`** (`infra/integration/proxy/ProxySession.kt`, same package) — one programmable session wrapping the proxy control API; also defines the `connectThroughProxy` extension and the rule-builder helpers.
- **`SandboxApp`** (`infra/integration/SandboxApp.kt`, package `io.ably.lib.uts.infra.integration`) — provisions/deletes a sandbox test app from the shared `test-app-setup.json` in ably-common. `SandboxApp.create()` returns a `SandboxApp` with `appId`, `defaultKey`, and `keys` (`defaultKey` is a full-capability `appId.keyId:keySecret`); `app.delete()` tears it down. Provision in suite setup, delete in teardown.

Import these into a proxy test from their packages, e.g. `io.ably.lib.uts.infra.integration.SandboxApp`, `io.ably.lib.uts.infra.integration.proxy.{ProxyManager, ProxySession, connectThroughProxy}`, plus `io.ably.lib.uts.infra.unit.TestRealtimeClient` and `io.ably.lib.uts.infra.{awaitState, pollUntil}`.

`ensureProxy()`, the `ProxySession` methods, and the `SandboxApp` methods are all **`suspend`** functions. Per-test bodies use `runTest { }`; JUnit5 `@BeforeAll`/`@AfterAll` (with `@TestInstance(Lifecycle.PER_CLASS)`) wrap their suspend calls in `runBlocking { }`.

### Test class docstring

Give every proxy integration test class this KDoc:

```kotlin
/**
 * Proxy integration test against Ably Sandbox endpoint.
 *
 * Uses the programmable proxy (`uts/test/proxy/`) to inject transport-level faults while the
 * SDK communicates with the real Ably backend. See
 * `uts/test/realtime/integration/helpers/proxy.md` for proxy infrastructure details.
 */
```

### Session lifecycle

`create_proxy_session(endpoint: "nonprod:sandbox", rules: [...])` → `ProxySession.create(...)`. The sandbox is already the default target, so an empty rule set is just:

```kotlin
val session = ProxySession.create(rules = emptyList())
```

Always close the session (and the client) in a `finally` block:

```kotlin
ProxyManager.ensureProxy()
val session = ProxySession.create(rules = listOf(
    wsConnectRule(action = mapOf("type" to "refuse_connection"), count = 2),
))
try {
    val client = TestRealtimeClient {
        key = sandboxKey
        connectThroughProxy(session)   // routes realtime + REST through the proxy
        autoConnect = false
    }
    client.connect()
    awaitState(client, ConnectionState.connected)
    // … scenario …
    client.close()
} finally {
    session.close()
}
```

| Pseudocode | Kotlin |
|---|---|
| `create_proxy_session(endpoint: "nonprod:sandbox", rules: [...])` | `ProxySession.create(rules = listOf(...))` |
| `session.add_rules(rules, position: "prepend")` | `session.addRules(rules, position = "prepend")` |
| `session.trigger_action({ type: "disconnect" })` | `session.triggerAction(mapOf("type" to "disconnect"))` |
| `session.get_log()` | `session.getLog()` |
| `session.close()` | `session.close()` |
| `session.proxy_port` / `session.proxy_host` | `session.proxyPort` / `session.proxyHost` |

### Connecting through the proxy

Call `connectThroughProxy(session)` inside the client builder block. It is a `ClientOptionsBuilder` extension (in `ProxySession.kt`) that wires the SDK through the proxy:

```kotlin
val client = TestRealtimeClient {
    key = sandboxKey
    connectThroughProxy(session)
    autoConnect = false
}
```

ably-java has **no `endpoint` ClientOptions field**; `connectThroughProxy` sets the discrete host fields for you:

| Proxy-def option | What `connectThroughProxy` sets |
|---|---|
| `endpoint: "localhost"` | `realtimeHost` **and** `restHost` = `session.proxyHost` (`"localhost"`) |
| `port: proxy_port` | `port = session.proxyPort` |
| `tls: false` | `tls = false` |
| `useBinaryProtocol: false` | already the `ClientOptionsBuilder` default — left untouched |

It does **not** touch `autoConnect`, so set that yourself. Setting explicit hosts disables fallback hosts automatically, so don't add `fallbackHosts`.

### Auth in proxy tests

A spec that needs to observe (re-)authentication uses an `authCallback`. Where the pseudocode "generates a JWT from the key parts", the idiomatic ably-java equivalent is a **locally-signed `TokenRequest`** from the same sandbox key — no JWT library required. The realtime client exchanges it for a token through the proxy:

```kotlin
val tokenSigner = AblyRest(app.defaultKey)          // local signing only; no network
val authCallbackCount = AtomicInteger(0)
val authCallback = Auth.TokenCallback { params ->
    authCallbackCount.incrementAndGet()
    tokenSigner.auth.createTokenRequest(params, null)
}
val client = TestRealtimeClient {
    this.authCallback = authCallback
    connectThroughProxy(session)
    autoConnect = false
}
```

`TokenParams`/`TokenRequest` are nested in `io.ably.lib.rest.Auth`; `AuthDetails` is nested in `io.ably.lib.types.ProtocolMessage`.

### Rule factory helpers

Build rules with the factory helpers in `ProxySession.kt` rather than raw map literals. Rules are evaluated in order, first match wins, unmatched traffic passes through, and `times` auto-removes a rule after N firings.

| Match condition | Kotlin |
|---|---|
| `{ "type": "ws_connect", "count": 2 }` | `wsConnectRule(action = ..., count = 2)` |
| `{ "type": "ws_connect", "queryContains": { "resume": "*" } }` | `wsConnectRule(action = ..., queryContains = mapOf("resume" to "*"))` |
| `{ "type": "ws_frame_to_client", "action": "ATTACHED", "channel": "c" }` | `wsFrameToClientRule(action = ..., messageAction = 11, channel = "c")` |
| `{ "type": "ws_frame_to_server", "action": "ATTACH", "channel": "c" }` | `wsFrameToServerRule(action = ..., messageAction = 10, channel = "c")` |
| `{ "type": "http_request", "method": "POST", "pathContains": "/keys/" }` | `httpRequestRule(action = ..., method = "POST", pathContains = "/keys/")` |

`messageAction` is the protocol action **number** (e.g. `4` CONNECTED, `6` DISCONNECTED, `9` ERROR, `10` ATTACH, `11` ATTACHED) — see the action-number table in `proxy.md`.

Actions are passed as `mapOf(...)`, e.g.:

```kotlin
mapOf("type" to "refuse_connection")
mapOf("type" to "disconnect")
mapOf("type" to "suppress")
mapOf("type" to "delay", "delayMs" to 2000)
mapOf("type" to "inject_to_client", "message" to mapOf("action" to 6))
mapOf("type" to "http_respond", "status" to 401, "body" to mapOf(...))
```

### Verifying the event log

`getLog()` returns a typed `List<Event>`. Access fields via dot notation (`it.type`, `it.direction`, `it.queryParams`, `it.status`); numeric fields (`status`, `closeCode`) are already `Int?`. The raw protocol message is exposed as `Event.message` (a Gson `JsonObject?`) — introspect it with `it.message?.get("action")?.asInt`.

```kotlin
val log = session.getLog()
val wsConnects = log.filter { it.type == "ws_connect" }
assertTrue(wsConnects.size >= 2)
val queryParams = wsConnects.first().queryParams
assertNotNull(queryParams["resume"])
```

### Conventions

1. Each test references the spec point and (where it exists) the corresponding unit test.
2. `ProxyManager.ensureProxy()` and sandbox-app provisioning go in `@BeforeAll` / suite setup; clean up in `@AfterAll`.
3. Each test creates its own `ProxySession` and closes it (and the client) in `finally`.
4. Use `awaitState` / `awaitChannelState` for state assertions; verify via SDK state **and** the proxy log where useful.
5. Use generous timeouts (10–30s) — real network is involved: `awaitState(client, ConnectionState.connected, 15.seconds)`.
6. Don't set `fallbackHosts`; explicit hosts already disable fallbacks.

Steps 5 (compile) and 6 (run) still apply. Note that proxy tests hit the live sandbox and download the proxy binary on first run, so they are slower and require network access.
