---
description: "Translate the UTS pseudocode test specs in a whole module directory into runnable Kotlin tests in the ably-java uts module. Takes a UTS module directory (e.g. .../specification/uts/objects), validates its structure, resolves the target ably-java module, lets you pick a tier (unit/integration/proxy) and which specs, then derives a Kotlin test per spec. Usage: /uts-to-kotlin <path-to-uts-module-directory>"
allowed-tools: Bash, Read, Edit, Write, WebFetch
---

Translate the UTS pseudocode test specs under the **module directory** `$ARGUMENTS` into runnable Kotlin
tests in the ably-java `uts` module.

`$ARGUMENTS` is a UTS *module* directory — a directory sitting directly under `.../specification/uts/`,
e.g. `/Users/sachinsh/ably-specification/specification/uts/objects`. Its name (`objects`, `realtime`,
`rest`, …) is the **source module**. A module directory holds many spec files, organised into tiers
(`unit/`, `integration/`, and `integration/proxy/`).

The work happens in two phases:

- **Phase 1 — Selection (Steps A–E below):** a bundled resolver script validates the directory and works
  out the target ably-java package, the spec files, and their class names; you then pick a tier, pick which
  specs, and choose whether to also evaluate.
- **Phase 2 — Per-spec translation (Steps 1–7):** for each selected spec file, derive a Kotlin test.

## Required reading — fetch first

Always fetch [writing-derived-tests.md](https://raw.githubusercontent.com/ably/specification/refs/heads/main/uts/docs/writing-derived-tests.md) first (once per run) — don't rely on memory or the inlined summaries; the manual is updated over time.

---

# Phase 1 — Selection

Path validation, the package mapping, spec discovery, and class-name derivation are all mechanical, so a
bundled script does them — that keeps selection byte-for-byte deterministic instead of relying on the model
to re-eyeball regexes, join paths, and hand-convert `snake_case` → `PascalCase` each run.

> **If `$ARGUMENTS` is empty or blank**, stop and show: `Usage: /uts-to-kotlin <path-to-uts-module-directory>`
> — with the example `/uts-to-kotlin /Users/sachinsh/ably-specification/specification/uts/objects`.

## Step A — Resolve the module

Run the resolver on the directory passed in (substitute the real path; the script path is relative to the
ably-java repo root):

```bash
python3 .claude/skills/uts-to-kotlin/scripts/resolve_uts.py "<module-dir>"
```

It prints one JSON object. **If `ok` is `false`, relay `message` to the user and stop** — error codes:
`NOT_A_UTS_MODULE_PATH` (not a `.../uts/<module>` directory), `DIR_NOT_FOUND`, `NO_TIER_DIRS` (no `unit/`
or `integration/`). On success it gives `sourceModule`, `mapped`, `testRoot`, and a `tiers` object with one
entry per tier (`unit` / `integration` / `proxy`), each carrying `present`, `sourceDir`, `targetDir`,
`package`, and `specs` (a list of `{file, className}`). Everything downstream reads from this output — treat
it as the single source of truth and don't recompute paths or names by hand.

## Step B — Confirm or create the target mapping

The target dirs come from `uts-package-mapping.json` (alongside this skill); spec and ably-java module names
don't always match (e.g. `objects` → `liveobjects`), which is why it's explicit.

- **If `mapped` is `true`**: show the resolved `targetDir` for each present tier and ask the user to confirm.
  If they say the mapping is wrong, ask for the correct ably-java module base name and re-run with `--create`
  (below) to overwrite the entry, then re-resolve.
- **If `mapped` is `false`**: there's no mapping for `sourceModule` yet. Ask for the target ably-java module
  base name (default to `sourceModule`; suggest a rename only when the SDK uses different terminology, e.g.
  `objects` → `liveobjects`), then create it deterministically and re-resolve:

  ```bash
  python3 .claude/skills/uts-to-kotlin/scripts/resolve_uts.py "<module-dir>" --create <target>
  ```

  This adds `unit/<target>`, `integration/standard/<target>`, and `integration/proxy/<target>` under
  `packages` and re-prints the resolved output. (`<target>` must be a simple module base name — letters,
  digits, underscore; the script returns `BAD_TARGET_NAME` otherwise, so just ask again.)

## Step C — Choose the tier

Offer the tiers whose `present` is `true`. The chosen tier fixes the `targetDir`, `package`, and `specs`
(from Step A) **and** the translation flow Phase 2 uses — don't re-detect any of it per spec:

| Tier | Translation flow |
|---|---|
| **unit** | mocked transport — Steps 3–4 below |
| **integration** (direct sandbox) | real sandbox, no faults — **Proxy integration tests** section, but drop the `ProxySession`/`connectThroughProxy` wiring |
| **proxy** | real sandbox + fault injection — **Proxy integration tests** section |

## Step D — Choose which specs to translate

The chosen tier's `specs` list (from Step A) is the candidate set — each entry already has its source `file`
and derived `className`. Present it and ask whether to translate **all** of them or a **selected subset**.
Then continue to Step E.

## Step E — Translate only, or also evaluate?

`writing-derived-tests.md` splits the work into **Translation** (always) and **Evaluation** (only
meaningful once the SDK implementation for this module exists). Ask the user which they want, and carry the
answer into Phase 2:

- **Translate only** — generate each test and make it **compile** (Steps 1–5 and the Step 7 review). Don't
  run the tests. Use this when the SDK feature isn't implemented yet, so there's nothing to run against.
- **Translate and evaluate** — all of the above **plus** running the tests and **fixing until they pass**
  (Step 6): work the decision tree, and where the SDK genuinely diverges, gate/adapt the assertion and
  record a deviation. Use this when the implementation exists.

If you can't tell whether the implementation exists, ask the user rather than guessing.

---

# Phase 2 — Per-spec translation

Run this for **each** spec file selected in Step D. **Step 6 only applies in "translate and evaluate" mode
(Step E)** — in "translate only" mode, stop after compiling (Step 5) and reviewing (Step 7), and skip
Step 6 entirely.

When translating several specs, do Steps 1–4 (generate the file) for every spec first, then run Step 5
(compile) once for the whole module, then per file run Step 6 (only if evaluating) and the Step 7 review —
compiling once is faster than per-file and surfaces cross-file issues together. For a single spec, just go
through the steps in order.

## Step 1 — Read the spec

Read the current spec file (the one being translated from the Step D selection). Identify:
- All test cases — each has a structured ID like `realtime/unit/RSA4c2/callback-error-connecting-disconnected-0` and a description
- The protocol used (WebSocket for Realtime, HTTP for REST)
- Any timer usage (`enable_fake_timers`, `ADVANCE_TIME`)

---

## Step 2 — Output path and package

Don't derive anything here — the resolver (Step A) already produced it. For the chosen tier use its
`targetDir` and `package`, and for the spec being translated use its `className` from that tier's `specs`
list. Write the test to `<targetDir>/<className>.kt` with `package <package>` at the top.

The spec's own `<sub>` grouping (e.g. `connection/`, `channels/`) is **not** reflected in the output — every
test sits directly in `targetDir` (the resolver flattens it). The chosen tier also fixes the translation
flow: **unit** → the rules in Steps 3–4 below; **integration** (direct sandbox) and **proxy** → the **Proxy
integration tests** section (direct sandbox drops the `ProxySession`/`connectThroughProxy` wiring; see
Step C).

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

- KDoc comment immediately above `@Test` using `/** @UTS <uts-id> */` — copy the spec's **full** structured
  id verbatim (e.g. `realtime/unit/RTN4a/some-description-0`; for the objects module it would start
  `objects/unit/…`). Don't hand-build the prefix — use what the spec file declares.
- Method name: backtick string `` `<spec-point> - <description>` `` — the spec point (e.g. `RTN4a`) plus a short description.
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
package <package>                              // the resolver's package for the chosen tier (Step 2)

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

class <className> {

    /**
     * @UTS <uts-id>
     */
    @Test
    fun `<spec-point> - <description>`() = runTest {
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

## Step 6 — Run tests *(evaluate mode only)*

Skip this whole step in "translate only" mode. In "translate and evaluate" mode, run the test and **keep
fixing until it passes** — either the spec-correct assertion passes, or it's deliberately gated/adapted as a
documented deviation (below). A red test is never an acceptable end state here.

Use the per-tier task that matches the chosen tier (both are registered in `uts/build.gradle.kts`), and the
resolver's `package` + the spec's `className` for the `--tests` filter:

```bash
# unit tier            → io.ably.lib.uts.unit.*
./gradlew :uts:runUtsUnitTests --tests "<package>.<className>"

# integration / proxy  → io.ably.lib.uts.integration.*
./gradlew :uts:runUtsIntegrationTests --tests "<package>.<className>"
```

(`./gradlew :uts:test` still runs all tiers — unit, standard, and proxy.)

Handle test failures using this decision tree (the **Required reading** doc you fetched up front has the full detail):

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

### Deviation honesty *(evaluate mode)*

Deviations are discovered by running, so this check applies in evaluate mode. For any place where the
generated test diverges from the spec pseudocode (adapted assertion, env-gated skip, or omitted step):
- [ ] A `// DEVIATION:` comment explains why
- [ ] The deviation is recorded in `uts/src/test/kotlin/io/ably/lib/uts/deviations.md`

If you find gaps during this review, fix them and re-run Step 5 (compile) — and, in evaluate mode, Step 6 —
before finishing.

---

## Proxy integration tests

Some specs are **integration tests** that exercise fault-handling behaviour against the **real Ably sandbox** instead of a mocked transport. They route the SDK through the [`ably/uts-proxy`](https://github.com/ably/uts-proxy) — a programmable HTTP/WebSocket proxy that forwards traffic transparently by default but can inject faults (dropped connections, modified/injected/delayed frames, error responses) via rules.

Recognise them by: a reference to `create_proxy_session()`, proxy `rules`, `trigger_action`, `get_log`, or a pointer to `uts/realtime/integration/helpers/proxy.md`.

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
 * Uses the programmable uts-proxy to inject transport-level faults while the
 * SDK communicates with the real Ably backend. See
 * `uts/realtime/integration/helpers/proxy.md` for proxy infrastructure details.
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

Step 5 (compile) still applies; Step 6 (run) applies only in evaluate mode (Step E). Note that proxy tests hit the live sandbox and download the proxy binary on first run, so they are slower and require network access.
