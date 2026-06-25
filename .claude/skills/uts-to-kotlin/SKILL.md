---
description: "Translate the UTS pseudocode test specs in a whole module directory into runnable Kotlin tests in the ably-java uts module. Takes a UTS module directory (e.g. .../specification/uts/objects), validates its structure, resolves the target ably-java module, lets you pick a tier (unit/integration/proxy) and which specs, then derives a Kotlin test per spec. Usage: /uts-to-kotlin <path-to-uts-module-directory>"
allowed-tools: Bash, Read, Edit, Write
---

Translate the UTS pseudocode test specs under the **module directory** `$ARGUMENTS` into runnable Kotlin
tests in the ably-java `uts` module.

`$ARGUMENTS` is a UTS *module* directory — a directory sitting directly under `.../specification/uts/`,
e.g. `/Users/sachinsh/ably-specification/specification/uts/objects`. Its name (`objects`, `realtime`,
`rest`, …) is the **source module**. A module directory holds many spec files, organised into tiers
(`unit/`, `integration/`, and `integration/proxy/`).

The work happens in two phases:

- **Phase 1 — Selection (Steps A–D below):** validate the directory, resolve the *target* ably-java
  module via `uts-package-mapping.json`, pick a tier, and pick which spec files to translate.
- **Phase 2 — Per-spec translation (Steps 1–7):** for each selected spec file, derive a Kotlin test.

Reference: [Writing Derived Tests](https://raw.githubusercontent.com/ably/specification/refs/heads/main/uts/docs/writing-derived-tests.md)

---

# Phase 1 — Selection

## Step A — Validate the module directory

`$ARGUMENTS` must be a UTS **module directory** sitting directly under a `uts/` parent.

**If `$ARGUMENTS` is empty or blank**, stop and tell the user:

```
Usage: /uts-to-kotlin <path-to-uts-module-directory>

Example:
  /uts-to-kotlin /Users/sachinsh/ably-specification/specification/uts/objects

Pass a UTS module directory (a directory directly under .../specification/uts/), not a single spec file.
```

Otherwise validate with these checks. Substitute the real path for `DIR=` (shell variables do **not**
persist between separate `Bash` calls, and `$ARGUMENTS` is a text placeholder, not a shell variable — so
set `DIR` literally each time you need it):

```bash
DIR="/absolute/path/to/the/module"               # the path passed in, trailing slash removed
# 1. Must be a directory directly under a `uts/` parent: .../uts/<module>
[[ "$DIR" =~ /uts/[^/]+$ ]] || echo "NOT_A_UTS_MODULE_PATH"
# 2. Must exist as a directory
test -d "$DIR" || echo "DIR_NOT_FOUND"
# 3. Standard structure: at least one recognised tier directory
{ test -d "$DIR/unit" || test -d "$DIR/integration"; } || echo "NO_TIER_DIRS"
echo "MODULE=$(basename "$DIR")"
```

- If `NOT_A_UTS_MODULE_PATH` → the path isn't `.../uts/<module-name>`. Tell the user the path must point at a
  module directory directly under `uts/` (e.g. `.../specification/uts/objects`) and stop.
- If `DIR_NOT_FOUND` → tell the user the directory doesn't exist and stop.
- If `NO_TIER_DIRS` → the directory has no `unit/` or `integration/` sub-directory, so it isn't a valid UTS
  module. Tell the user and stop.

The **source module** is the directory's base name (`MODULE=` above) — `objects`, `realtime`, `rest`, …

Only continue once all three checks pass.

## Step B — Resolve the target ably-java module

Spec modules don't always share a name with their ably-java counterpart (e.g. `objects` → `liveobjects`),
so the mapping is explicit. Read `uts-package-mapping.json`, which sits alongside this skill at
`.claude/skills/uts-to-kotlin/uts-package-mapping.json`. The file has a single shared parent — `testRoot`
(the directory, from the ably-java repo root, that every target lives under) — and a `packages` table whose
keys are source module names. Each tier value is the output directory **relative to `testRoot`**:

```json
"testRoot": "uts/src/test/kotlin/io/ably/lib/uts",
"packages": {
  "objects": {
    "unit": "unit/liveobjects",
    "integration": "integration/standard/liveobjects",
    "proxy": "integration/proxy/liveobjects"
  }
}
```

Resolve a tier to its concrete target like so:
- **Output directory** = `testRoot` + `/` + the tier entry (e.g. `uts/src/test/kotlin/io/ably/lib/uts/unit/liveobjects`).
- **Kotlin package** = that path's segment **after `src/test/kotlin/`** with `/` replaced by `.` (e.g. `io.ably.lib.uts.unit.liveobjects`).

Then:
- **If the source module has an entry**, show its three resolved target dirs and ask the user to confirm it,
  or to pick a different existing module instead.
- **If it has no entry**, tell the user there's no mapping yet and offer to create one. Ask for the target
  module base name (default to the source name; suggest a rename only when the SDK uses different
  terminology, e.g. `objects` → `liveobjects`). Then add a new entry to `uts-package-mapping.json` with the
  three `testRoot`-relative paths — `unit/<target>`, `integration/standard/<target>`, and
  `integration/proxy/<target>` — and save the file before continuing.

## Step C — Choose the tier

Offer the tiers that actually exist in the source module, and map each to its source spec directory and the
target output directory from Step B:

| Tier | Source spec directory | Target (mapping entry, joined onto `testRoot`) | Per-spec translation flow |
|---|---|---|---|
| **unit** | `<module>/unit/` | mapping `unit` (e.g. `unit/liveobjects`) | mocked transport — Steps 1–7 below |
| **integration** (direct sandbox) | `<module>/integration/` *(excluding `proxy/` and `helpers/`)* | mapping `integration` (e.g. `integration/standard/liveobjects`) | real sandbox, no faults — see **Proxy integration tests** but drop the `ProxySession`/`connectThroughProxy` wiring |
| **proxy** | `<module>/integration/proxy/` | mapping `proxy` (e.g. `integration/proxy/liveobjects`) | real sandbox + fault injection — see **Proxy integration tests** |

The tier you pick here fixes the target directory/package and which translation flow Phase 2 uses — you do
**not** re-detect it per spec.

## Step D — Choose which specs to translate

List the candidate spec files in the chosen tier's source directory (recurse, but **exclude** any
`helpers/` directory and non-spec docs like `PLAN.md`, `README.md`, or `*_SUMMARY.md`). Substitute the
real module path for `DIR` again:

```bash
DIR="/absolute/path/to/the/module"
# unit
find "$DIR/unit" -name '*.md' -not -path '*/helpers/*' | sort
# integration (direct sandbox) — exclude the proxy subtree
find "$DIR/integration" -name '*.md' -not -path '*/proxy/*' -not -path '*/helpers/*' | sort
# proxy
find "$DIR/integration/proxy" -name '*.md' -not -path '*/helpers/*' | sort
```

Present the list and ask the user whether to **translate all** of them or **select specific** files. Then,
for each selected spec, run Phase 2 (Steps 1–7).

---

# Phase 2 — Per-spec translation

Run this for **each** spec file selected in Step D. When translating several specs, do Steps 1–4 (generate
the file) for every spec first, then run Step 5 (compile) once for the whole module, then Steps 6–7 per
file — compiling once is faster than per-file and surfaces cross-file issues together. For a single spec,
just go through Steps 1–7 in order.

## Step 1 — Read the spec

Read the current spec file (the one being translated from the Step D selection). Identify:
- All test cases — each has a structured ID like `realtime/unit/RSA4c2/callback-error-connecting-disconnected-0` and a description
- The protocol used (WebSocket for Realtime, HTTP for REST)
- Any timer usage (`enable_fake_timers`, `ADVANCE_TIME`)

---

## Step 2 — Determine output path and package

The target directory and package are already fixed by the tier chosen in Step C and the mapping resolved in
Step B — you do not re-derive them from the spec path. `<targetDir>` is `testRoot` + the tier entry (e.g.
`uts/src/test/kotlin/io/ably/lib/uts/unit/liveobjects`) and `<package>` is its dotted form after
`src/test/kotlin/` (e.g. `io.ably.lib.uts.unit.liveobjects`).

Only the **class name** comes from the spec file: take its file name, strip a `_test` suffix if present,
convert `snake_case` → `PascalCase`, and append `Test`. Examples: `objects_batch_test.md` →
`ObjectsBatchTest`; `live_counter_api.md` → `LiveCounterApiTest`; `connection_recovery_test.md` →
`ConnectionRecoveryTest`.

The spec's own `<sub>` grouping (e.g. `connection/`, `channels/`) is **not** carried into a sub-package —
every test sits directly in `<targetDir>`. Output file: `<targetDir>/<ClassName>.kt`, with
`package <package>` at the top.

The chosen tier also fixes the translation flow: **unit** → the rules in Steps 3–4 below; **integration**
(direct sandbox) and **proxy** → the **Proxy integration tests** section (direct sandbox drops the
`ProxySession`/`connectThroughProxy` wiring; see Step C).

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
