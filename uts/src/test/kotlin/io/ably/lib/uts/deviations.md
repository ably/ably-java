# SDK Deviations

Deviations from the Ably spec identified during UTS test translation. Each entry records the spec point, what the spec requires, what the SDK actually does, and which test contains the deviation gate.

Entries are grouped by actionability:

| Group | Meaning | Action |
|---|---|---|
| **1) Genuine SDK bugs — open** | runtime behaviour differs from the spec; ably-js is compliant | fix the SDK |
| **2) Shared gap — open in both SDKs** | ably-java and ably-js deviate the same way | optional joint fix (spec is ahead of both) |
| **3) Expected — typed-SDK / language adaptations** | not bugs: RTTS API partitioning, compile-time guarantees, internal-wire visibility | none — correct as documented |
| **4) Intentional deviation** | deliberate SDK design choice; the spec point itself is questioned | none unless the spec is revised |

> **Recently fixed and removed from this file:** RTO23e (`get()` now re-attaches a DETACHED channel —
> mode-only check + ensure-active-channel) and RTO20e/RTO20e1 (event-driven `once(SYNCED)` waiters +
> `failSyncWaiters` replace the orphan-prone shared deferred). Their spec-correct tests are un-gated and
> pass by default.
>
> One **test-stimulus adaptation** (not an SDK deviation) remains inline in `RealtimeObjectTest.kt`: the
> RTO20e1 test drives the 92008 path via channel ERROR → FAILED instead of the spec's DETACHED stimulus —
> an unsolicited DETACHED auto-reattaches (RTL13a) and never settles, so the spec's stimulus is
> unobservable. Same adaptation ably-js uses and the spec adopted for the proxy tier (specification#501).

---

# 1) Genuine SDK bugs — open (realtime module)

*Runtime behaviour differs from the spec and ably-js is compliant — real bugs, pending an SDK fix.*

> ⚠ **RTL13b / RTL13c:** the channel-state UTS tests these two entries cite are **not currently part of the
> uts suite** (no `RTL13*` tests or gates exist in `unit/realtime/` — only `ConnectionRecoveryTest.kt` is
> translated). The entries are retained as confirmed SDK gaps (cross-checked against ably-js in
> `ABLY-JS-JAVA-DEVIATIONS-COMPARISON.md`); re-verify them when the channels module translation lands.

## RTL13b — ATTACHING → SUSPENDED via `realtimeRequestTimeout` not implemented

**Spec point:** RTL13b  
**What the spec requires:** If a channel's reattach request (triggered by RTL13a) does not receive a response within `realtimeRequestTimeout`, the channel must transition from ATTACHING to SUSPENDED and schedule a retry after `channelRetryTimeout`.  
**What the SDK does:** The channel remains in ATTACHING indefinitely when no server response arrives. The `realtimeRequestTimeout` timer is not applied to channel attach requests; only a server-sent DETACH/ERROR while ATTACHING causes the ATTACHING → SUSPENDED transition.  
**Workaround in tests:** Tests that need a SUSPENDED state set up via failed reattach instead use server-sent DETACHED while ATTACHING (RTL13b's second condition, which IS implemented) to drive the channel to SUSPENDED.  
**Tests affected:**
- `RTL13a - server DETACHED on SUSPENDED channel triggers immediate reattach` (RTL13a/suspended-reattach-triggered-1) — setup path changed
- `RTL13b - failed reattach transitions to SUSPENDED with automatic retry` (RTL13b/failed-reattach-suspended-retry-0) — mock sends DETACHED instead of withholding response
- `RTL13b - repeated failures cycle SUSPENDED to ATTACHING indefinitely` (RTL13b/repeated-failure-cycle-2) — mock sends DETACHED instead of withholding response
- `RTL13c - automatic retry cancelled when connection is no longer CONNECTED` (RTL13c/retry-cancelled-disconnected-0) — setup path changed

---

## RTL13c — channelRetryTimeout not cancelled when connection leaves CONNECTED

**Spec point:** RTL13c  
**What the spec requires:** When the connection is no longer CONNECTED, any pending automatic channel reattach timer (channelRetryTimeout) must be cancelled. The channel should remain SUSPENDED without attempting to reattach until the connection is restored.  
**What the SDK does:** The channelRetryTimeout fires regardless of connection state. When it fires while disconnected, the channel transitions to ATTACHING even though there is no active connection, and no ATTACH message can be sent.  
**Tests affected:**
- `RTL13c - automatic retry cancelled when connection is no longer CONNECTED` (RTL13c/retry-cancelled-disconnected-0) — the assertions `assertEquals(attachCountAfterDisconnect, attachCount)` and `assertEquals(ChannelState.suspended, channel.state)` are gated behind `RUN_DEVIATIONS`.

---

## RTN16g2 — Fatal ERROR must be sent without closing the transport

**Spec point:** RTN16g2  
**What the spec requires:** Trigger FAILED state by sending a fatal ERROR message followed by closing the WebSocket (`send_to_client_and_close`), using error code 50000/statusCode 500.  
**What the SDK does (two issues):**  
1. Error code 50000/statusCode 500 is not treated as fatal by `isFatalError()` (requires code 40000–49999 or statusCode < 500), so FAILED is never reached with the spec's values.  
2. Sending `close(1000)` after the ERROR dispatches a synchronous `DISCONNECTED` action that races with and preempts the async `FAILED` transition triggered by the ERROR message.  
**Workaround in tests:** Use `sendToClient` (no close frame) with code 40000/statusCode 400. The SDK's own FAILED-state handler calls `clearTransport()`, so the explicit close is not needed.  
**Tests affected:**
- `RTN16g2 - createRecoveryKey returns null in inactive states and before first connect` (RTN16g2/recovery-key-null-inactive-0) — error code and send method changed.

---

## RTN16f — msgSerial not initialised from recovery key on connect

**Spec point:** RTN16f  
**What the spec requires:** When instantiated with the `recover` option, the SDK initialises its internal `msgSerial` counter to the value stored in the recovery key, so the first published message carries that serial.  
**What the SDK does:** `ConnectionManager.onConnected()` resets `msgSerial` to 0 whenever `connection.id` is null on the fresh client (line 1316), even when the `recover` option is set. The recovered serial is discarded.  
**Workaround in tests:** The spec-correct assertion (`assertEquals(42L, msgSerial)`) is gated behind `RUN_DEVIATIONS`. A regression guard assertion (`assertEquals(0L, msgSerial)`) runs by default to catch any unintentional change to the SDK's actual behaviour.  
**Tests affected:**
- `RTN16f - recover option initializes msgSerial from recoveryKey` (RTN16f/recover-initializes-msgserial-0) — `assertEquals(42L, ...)` gated; `assertEquals(0L, ...)` added as regression guard.

---

# 3) Expected — typed-SDK / language adaptations (objects) — NOT bugs, no action

*These exist only because ably-java implements the statically-typed **RTTS** variant of the objects spec
while the UTS assertions are written against ably-js's dynamically-typed API. Each is the documented-correct
translation: RTTS API partitioning (`as*` views, `compact()` opt-out, opaque value types), compile-time
guarantees replacing runtime type errors, or `internal` wire types replacing wire-shape assertions. No SDK
change is wanted; ably-js has no counterpart deviation because JS can express the assertion directly.*

*Spec-point provenance: the `RTTS*` points and `RTO23f` cited below come from the typed-SDK variant of the
objects spec (ably/specification **PR #491**, unmerged) — they are not in the merged `objects-features.md`
yet, so don't be surprised when a grep of the merged spec misses them.*

## RTINS12d / RTINS14d / RTINS16c — wrong-type Instance operation throws IllegalStateException, not ErrorInfo 92007

**Spec points:** RTINS12d, RTINS14d, RTINS16c
**What the spec requires:** Calling a type-specific operation on an `Instance` wrapping the wrong type fails with `ErrorInfo` code `92007` — `set`/`remove` on a non-LiveMap, `increment`/`decrement` on a non-LiveCounter, `subscribe` on a primitive.
**What the SDK does:** ably-java implements the typed-SDK variant (RTTS): those operations do not exist on the base `Instance` or on the mismatched typed view, so the wrong operation cannot be called at all. The type check happens at the `as*` cast, which fails fast with a plain `IllegalStateException` ("Not a LiveMap/LiveCounter instance") carrying no Ably error code (`DefaultInstance`, RTTS9d). There is no `92007` `AblyException` / `ErrorInfo`.
**Workaround in tests:** Assert `assertFailsWith<IllegalStateException> { … }` on the relevant `asLiveMap()` / `asLiveCounter()` cast instead of an `ErrorInfo` code `92007`.
**Tests affected (InstanceTest.kt):**
- `RTINS12d - set on non-LiveMap throws` (RTINS12d/set-non-map-throws-0)
- `RTINS14d - increment on non-LiveCounter throws` (RTINS14d/increment-non-counter-throws-0)
- `RTINS16c - subscribe on primitive throws` (RTINS16c/subscribe-primitive-throws-0)

---

## RTINS4d / RTINS9c — `value()` on a LiveMap / `size()` on a LiveCounter are not expressible

**Spec points:** RTINS4d, RTINS9c
**What the spec requires:** The polymorphic `Instance#value()` returns `null` for a LiveMap, and `Instance#size()` returns `null` for a non-LiveMap.
**What the SDK does:** Under the typed-SDK variant (RTTS10) these accessors are partitioned: `value()` exists only on `LiveCounterInstance` / primitive instances, and `size()` only on `LiveMapInstance`. A `LiveMapInstance` has no `value()` and a `LiveCounterInstance` has no `size()`, so the "wrong-type returns null" assertions cannot be written (and the cast to the other view would throw — see above).
**Workaround in tests:** The expressible half of each test is translated (counter `value()`, map `size()`); the not-expressible sub-assertion is dropped with an inline note.
**Tests affected (InstanceTest.kt):**
- `RTINS4 - value returns counter number or primitive` (RTINS4/value-counter-0) — map `value() == null` omitted.
- `RTINS9 - size returns non-tombstoned count` (RTINS9/size-0) — counter `size() == null` omitted.

---

## RTINS10 — `compact()` not implemented; `compactJson()` used instead

**Spec point:** RTINS10
**What the spec requires:** `Instance#compact()` returns a recursively-compacted native snapshot (plain map/number/string), e.g. `result["score"] == 100`.
**What the SDK does:** ably-java does not implement `compact()` (RTTS7d — typed SDKs need not). Only `compactJson()` is provided, returning a Gson `JsonObject`/`JsonElement` tree.
**Workaround in tests:** The test calls `compactJson()` and navigates the resulting `JsonObject` (`snapshot.get("score").asInt`, etc.).
**Tests affected (InstanceTest.kt):**
- `RTINS10 - compact recursively compacts` (RTINS10/compact-0)

---

## RTPO5b / RTPO6b — `get(non-string)` / `at(non-string)` failing with 40003 is not expressible

**Spec points:** RTPO5b, RTPO6b
**What the spec requires:** Calling `PathObject.get(key)` / `LiveMap.at(path)` with a non-String argument fails at runtime with `ErrorInfo` code `40003`.
**What the SDK does:** ably-java is statically typed — `LiveMapPathObject.get(@NotNull String)` and `LiveMapPathObject.at(@NotNull String)` only accept a `String`. A non-string argument is a compile error, never a runtime failure, so there is no code path that returns a `40003` `AblyException` for this input.
**Workaround in tests:** The case is not expressible; the test body documents the omission with an inline note and contains no executable assertion.
**Tests affected (PathObjectTest.kt):**
- `RTPO5b - get throws on non-string key` (RTPO5b/get-non-string-throws-0)
- `RTPO6b - at throws for non-string input` (RTPO6b/at-non-string-throws-0)

---

## RTPO13 / RTPO13c5 / RTPO13c / RTPO3c1 — `compact()` not implemented; `compactJson()` used instead

**Spec points:** RTPO13, RTPO13c5, RTPO13c, RTPO3c1
**What the spec requires:** `PathObject#compact()` returns a recursively-compacted native snapshot — plain map/number/string/boolean/bytes values, nested LiveMaps recursed, nested LiveCounters resolved to numbers, raw binary preserved as bytes, and cyclic references reused as the same in-memory object (`result["prefs"]["back_ref"] IS result`). `compact()` returns `null` on resolution failure.
**What the SDK does:** ably-java does not implement `compact()` (RTTS3f — typed SDKs need not). Only `compactJson(): JsonElement?` is provided, returning a Gson tree: binary values are base64-encoded strings (not raw bytes) and cyclic references are emitted as `{ "objectId": ... }` markers (not shared object identity). It returns `null` on resolution failure.
**Workaround in tests:** Each test calls `compactJson()` and navigates the resulting `JsonElement`/`JsonObject`. The binary entry is asserted as its base64 string (`"AQID"`); the cycle is asserted as the `{ "objectId": "map:profile@1000" }` marker instead of object identity; the LiveCounter compacts to its numeric JSON value; the resolution-failure case asserts `compactJson() == null`.
**Tests affected (PathObjectTest.kt):**
- `RTPO13 - compact recursively compacts LiveMap tree` (RTPO13/compact-recursive-0) — base64 for binary.
- `RTPO13c5 - compact handles cycles via shared reference` (RTPO13c5/compact-cycle-detection-0) — objectId marker instead of identity.
- `RTPO13c - compact returns number for LiveCounter` (RTPO13c/compact-counter-0).
- `RTPO3c1 - read operation returns null on resolution failure` (RTPO3c1/read-null-on-failure-0) — `compact()` sub-assertion uses `compactJson()`.

---

## RTPO10 / RTPO10d / RTPO11 / RTPO11d — `keys()` / `values()` "IS Array" is a static-type tautology

**Spec points:** RTPO10, RTPO10d, RTPO11, RTPO11d
**What the spec requires:** `PathObject.keys()` / `values()` return an array, asserted with `ASSERT keys IS Array` / `ASSERT vals IS Array` (alongside element-count and membership checks).
**What the SDK does:** ably-java is statically typed — `LiveMapPathObject.keys()` returns `Iterable<String>` and `values()` returns `Iterable<PathObject>` (mapping §4). The collection type is guaranteed by the method signature at compile time, so a runtime "is it an array" check is a tautology that cannot fail and adds no coverage.
**Workaround in tests:** The substantive assertions — element count and membership (`size`, `in`) — are translated; the `IS Array` type-tautology line is omitted.
**Tests affected (PathObjectTest.kt):**
- `RTPO10 - keys returns array of key strings` (RTPO10/keys-returns-array-0) — `keys IS Array` omitted; count + membership asserted.
- `RTPO10d - keys returns empty for non-LiveMap` (RTPO10d/keys-non-map-empty-0) — `keys IS Array` omitted; count == 0 asserted.
- `RTPO11 - values returns array of PathObjects` (RTPO11/values-returns-array-0) — `vals IS Array` omitted; count + membership asserted.
- `RTPO11d - values returns empty for non-LiveMap` (RTPO11d/values-non-map-empty-0) — `vals IS Array` omitted; count == 0 asserted.

---

## RTLM20 / RTLM21 — set/remove wire-message-shape assertions are internal; assert observable local effect

**Spec points:** RTLM20e2, RTLM20e3, RTLM20e6, RTLM20e7b, RTLM20e7c, RTLM20e7d, RTLM20e7e, RTLM20e7f, RTLM20h2, RTLM21e2, RTLM21e5
**What the spec requires:** `set` / `remove` send an OBJECT ProtocolMessage whose captured wire form is asserted directly — `captured_messages[0].state[0].operation.action == "MAP_SET" / "MAP_REMOVE"`, `operation.objectId == "root"`, `mapSet.key`, `mapSet.value.string / .number / .boolean / .json / .bytes` (base64), `mapRemove.key`.
**What the SDK does:** ably-java's public `LiveMapPathObject.set` / `remove` return a `CompletableFuture<Void>`; the bytes that go on the wire are internal `WireObjectMessage` objects in `ProtocolMessage.state` (`Object[]`), inaccessible through the public API (mapping §13). The public-observable consequence is that, once the operation is ACKed and echoed, it applies to the local graph.
**Workaround in tests:** Perform the public write, then assert the equivalent observable effect via a local round-trip read after the auto-ACK echo applies (`root.get(key).as<Type>().value()` for set, `getType() == null` for remove), polling for application. The exact wire-message shape is not asserted.
**Tests affected (InternalLiveMapApiTest.kt):**
- `RTLM20 - set sends MAP_SET message` (RTLM20/set-sends-map-set-0)
- `RTLM20 - set with different value types` (RTLM20/set-value-types-0)
- `RTLM20 - set with bytes value type` (RTLM20/set-bytes-value-0)
- `RTLM21 - remove sends MAP_REMOVE message` (RTLM21/remove-sends-map-remove-0)

---

## RTLM20e7g / RTLM20h1 — value-type CREATE-message generation/ordering is internal; assert resolved object

**Spec points:** RTLM20e7g1, RTLM20e7g2, RTLM20h1, RTLMV4d1, RTLMV4d2 (also RTLCV4 / RTLMV4 evaluation)
**What the spec requires:** Setting a `LiveCounter` / `LiveMap` value type produces an OBJECT whose `state` array contains the generated `*_CREATE` ObjectMessages followed by a `MAP_SET`, in depth-first order, with the `MAP_SET`'s `mapSet.value.objectId` referencing the final CREATE's `objectId` (and `objectId` prefixes `counter:` / `map:`).
**What the SDK does:** The evaluation of a value type into an ordered list of `*_CREATE` wire messages, nonce/objectId derivation, and the cross-referencing objectIds are all internal wire-level concerns (mapping §13) — not reachable through the public typed API. The public-observable consequence is that the new nested object is created and resolvable at the key.
**Workaround in tests:** Perform the public write, then assert the equivalent observable effect: the new value resolves to a `LIVE_COUNTER` / `LIVE_MAP` with its initial value/entries (and, for the nested case, the nested counter and primitive resolve). The CREATE-message count, ordering, and objectId cross-references are not asserted.
**Tests affected (InternalLiveMapApiTest.kt):**
- `RTLM20e7g - set with LiveCounter generates COUNTER_CREATE plus MAP_SET` (RTLM20e7g/set-counter-value-type-0)
- `RTLM20e7g - set with LiveMap generates nested CREATE plus MAP_SET` (RTLM20e7g/set-map-value-type-0)
- `RTLM20h1 - set with nested LiveMap containing LiveCounter` (RTLM20h1/set-nested-value-types-0)

---

## RTLM20 / RTLMV4c — invalid set value types (function / undefined / symbol) not expressible

**Spec points:** RTLM20e1, RTLMV4c
**What the spec requires:** A table-driven test feeds unsupported runtime values (a function, `undefined`, a symbol) into `set` and expects each to fail with `ErrorInfo` code `40013`.
**What the SDK does:** ably-java's `LiveMapPathObject.set(String, LiveMapValue)` accepts only a `LiveMapValue`, and `LiveMapValue.of(...)` is overloaded solely for the supported types (Boolean, Binary/byte[], Number, String, JsonArray, JsonObject, LiveCounter, LiveMap). There is no overload that accepts a function / undefined / symbol, so these inputs are rejected at compile time (mapping §6) — the runtime `40013` assertion cannot be expressed.
**Workaround in tests:** The test body is a documented no-op explaining the compile-time rejection; no runtime assertion is made.
**Tests affected (InternalLiveMapApiTest.kt):**
- `RTLM20 - invalid set value types` (RTLM20/set-invalid-values-table-0)

---

## RTLC12e2 / RTLC12e3 / RTLC12e5 / RTLC13b — outbound COUNTER_INC wire message is internal

**Spec points:** RTLC12e2, RTLC12e3, RTLC12e5, RTLC13b
**What the spec requires:** After `increment(n)` / `decrement(n)`, inspect the published OBJECT message's wire form — `captured.state[0].operation.action == "COUNTER_INC"`, `.operation.objectId == "counter:score@1000"`, `.operation.counterInc.number == n` (and `== -15` for decrement, proving decrement negates the amount).
**What the SDK does:** The outbound wire types (`WireObjectMessage` / `WireObjectOperation` / `WireCounterInc`) are `internal` to `:liveobjects` and not part of the public API; there is no public accessor for the message a `LiveCounterPathObject.increment` / `.decrement` publishes.
**Workaround in tests:** The captured outbound `ProtocolMessage` is found in `mockWs.events` (`MessageFromClient` with `action == object`), and its `state[0]` wire object's `operation` / `action` / `objectId` / `counterInc.number` are read by reflection (the same reflection technique `helpers.kt` and `PublicObjectMessageTest.kt` use for internal `:liveobjects` types). Where the spec also provides an observable value outcome (decrement → `value() == 85`), that is asserted directly via the public API.
**Tests affected (InternalLiveCounterApiTest.kt):**
- `RTLC12 - increment sends v6 COUNTER_INC message` (RTLC12/increment-sends-counter-inc-0)
- `RTLC13 - decrement delegates to increment with negated amount` (RTLC13/decrement-negates-0)

---

## RTLC11b1 — LiveCounterUpdate diff (`update.amount`) not exposed on public event

**Spec point:** RTLC11b1
**What the spec requires:** Subscribing to a counter `instance` and incrementing it emits an event whose `message.operation.counterInc.number` (the increment amount) equals the applied value (`updates[0].message.operation.counterInc.number == 7`).
**What the SDK does:** ably-java's public `InstanceSubscriptionEvent` carries no internal `LiveCounterUpdate` diff (no `update.amount` accessor — that is the internal RTLO4b update). It does expose the originating public `ObjectMessage` via `getMessage()`, whose `operation.counterInc.number` carries the amount.
**Workaround in tests:** Assert `event.getMessage().operation.counterInc.number == 7.0` (and `operation.action == COUNTER_INC`) instead of an `update.amount` diff field.
**Tests affected (InternalLiveCounterApiTest.kt):**
- `RTLC11 - LiveCounterUpdate emitted on increment` (RTLC11/counter-update-on-inc-0)

---

## RTLC12e1 — non-Number increment amounts are compile errors, not 40003 runtime failures

**Spec point:** RTLC12e1
**What the spec requires:** `increment(amount)` throws `ErrorInfo` code `40003` when `amount` is `null`, not a Number, not finite, or NaN — exercised both singly (`increment("not_a_number")`) and as a table (`null`, `NaN`, `±Infinity`, `"10"`, `true`, `[1,2]`, `{n:1}`).
**What the SDK does:** ably-java's `LiveCounterPathObject.increment(@NotNull Number)` accepts only a non-null `Number`. The non-Number rows (`null`, String, Boolean, array, object) are rejected by the type system at compile time, so they cannot be written as runtime assertions. The numeric-but-invalid rows (`NaN`, `+Infinity`, `-Infinity`) are valid `Double` values and remain expressible runtime `40003` assertions.
**Workaround in tests:** The non-Number cases are dropped with an inline note; the non-finite `Double` cases (`NaN`, `±Infinity`) are exercised and asserted to fail with `40003`. The dedicated single-case `increment-non-number-0` test is reduced to a documented placeholder for the same reason.
**Tests affected (InternalLiveCounterApiTest.kt):**
- `RTLC12e1 - increment with non-number throws` (RTLC12e1/increment-non-number-0) — not expressible; placeholder.
- `RTLC12e1 - Table-driven invalid increment amounts` (RTLC12e1/increment-invalid-amounts-table-0) — non-Number rows dropped; non-finite rows exercised.

---

## RTLC12b / RTLC12c / RTLC12d — increment write-preconditions relocated to RTO26; no executable spec content

**Spec points:** RTLC12b, RTLC12c, RTLC12d
**What the spec requires:** The spec entry `objects/unit/RTLC12b/increment-requires-publish-0` carries no Setup/Test Steps/Assertions — its body states that RTLC12b/c/d (the OBJECT_PUBLISH-mode, channel-state and echoMessages write preconditions) "have been replaced by RTO26" and are "tested separately in `objects/unit/rto26_write_preconditions.md`".
**What the SDK does:** N/A — there is no executable spec content to translate from this entry; the precondition behaviour is covered by the RTO26 spec instead.
**Workaround in tests:** The empty marker entry is intentionally not translated into `InternalLiveCounterApiTest.kt`; the preconditions belong with an RTO26 translation, which is not part of this module's current scope.
**Tests affected (InternalLiveCounterApiTest.kt):**
- `objects/unit/RTLC12b/increment-requires-publish-0` — no corresponding `@Test`; relocated to RTO26, no executable spec content.

---

## RTO15 — `RealtimeObject.publish` and its OBJECT/ACK wire-message assertions are internal, not public

**Spec point:** RTO15 (RTO15e1, RTO15e2, RTO15e3, RTO15h)
**What the spec requires:** `channel.object.publish([objectMessages])` sends an OBJECT `ProtocolMessage` whose captured wire form is asserted directly — `captured_messages[0].action == OBJECT`, `.channel == "test"`, `.state.length == 1` — and returns a `PublishResult` from the ACK whose `serials == ["serial-0"]`.
**What the SDK does:** ably-java's `RealtimeObject` exposes no public `publish` method — `publish` / `publishAndApply` (RTO15 / RTO20) are marked `internal` in the IDL (mapping §13). The only public mutators are the typed `set` / `remove` / `increment` / `decrement` on the path/instance views, which return `CompletableFuture<Void>` (no `PublishResult`). The OBJECT `ProtocolMessage.state` entries are internal `WireObjectMessage` objects (`Object[]`), and the ACK `PublishResult` is consumed internally to drive the local apply — neither is reachable through the public API.
**Workaround in tests:** None expressible against the public surface. The publish-and-apply *effect* (RTO20) is covered observably elsewhere in this file (e.g. `RTO20 - publishAndApply applies locally on ACK` asserts `value() == 110` after a public `increment`). The RTO15 test body is a documented no-op.
**Tests affected (RealtimeObjectTest.kt):**
- `RTO15 - publish sends OBJECT ProtocolMessage` (RTO15/publish-sends-object-pm-0) — not expressible; documented no-op.

---

## RTO23f — `get()` result "IS PathObject" is guaranteed by the `LiveMapPathObject` return type

**Spec point:** RTO23f
**What the spec requires:** The object returned by `channel.object.get()` is a `PathObject`, asserted with `ASSERT root IS PathObject` (RTO23f — always a `LiveMapPathObject`).
**What the SDK does:** ably-java's `RealtimeObject.get()` is statically typed to return `CompletableFuture<LiveMapPathObject>`, and `LiveMapPathObject` is a `PathObject` sub-type (mapping §2, §4). The result's PathObject-ness is proven by the return type at compile time, so a runtime `IS PathObject` check is a tautology that cannot fail.
**Workaround in tests:** The observable behaviour around the call (`path() == ""`, channel-state transitions) is asserted; the `IS PathObject` type-tautology line is omitted, with an inline note at the call site.
**Tests affected (RealtimeObjectTest.kt):**
- `RTO23 - get returns PathObject wrapping root` (RTO23/get-returns-path-object-0) — `root IS PathObject` omitted; `path() == ""` asserted.
- `RTO23 - get implicitly attaches channel` (RTO23/get-implicit-attach-0) — `root IS PathObject` omitted; channel state + `path() == ""` asserted.
- `RTO23d - get resolves immediately when already SYNCED` (RTO23d/get-resolves-immediately-synced-0) — `root2 IS PathObject` omitted; `path() == ""` asserted.

---

## RTLCV3 / RTLMV3 — value-type internal count / entries have no public accessor

**Spec points:** RTLCV3b, RTLCV3a1, RTLMV3b, RTLMV3a1
**What the spec requires:** A constructed value type exposes its internal blueprint state for inspection — `LiveCounter.create(42).count == 42`, `LiveCounter.create().count == 0`, `LiveMap.create({...}).entries["name"] == "Alice"`.
**What the SDK does:** ably-java's `LiveCounter` / `LiveMap` value types are opaque immutable holders: the initial count / entries are "held internally by the implementation; [they have] no public accessor" (their Javadoc). Only the static `create(...)` factory and the abstract type identity are observable; there is no `count` / `entries` getter.
**Workaround in tests:** Assert construction succeeds and the result `is LiveCounter` / `is LiveMap` (the value-type identity). The internal `count` / `entries` sub-assertions are dropped with an inline note.
**Tests affected (ValueTypesTest.kt):**
- `RTLCV3 - LiveCounter create with initial count` (RTLCV3/create-with-count-0) — `vt.count == 42` omitted.
- `RTLCV3 - LiveCounter create defaults to 0` (RTLCV3/create-default-zero-0) — `vt.count == 0` omitted.
- `RTLMV3 - LiveMap create with entries` (RTLMV3/create-with-entries-0) — `vt.entries[...]` omitted.

---

## RTLCV4 / RTLMV4 — value-type `evaluate()` ObjectMessage generation is internal/wire-level

**Spec points:** RTLCV4 (RTLCV4b1, RTLCV4c, RTLCV4d, RTLCV4f, RTLCV4g1–g5), RTLMV4 (RTLMV4e1, RTLMV4f, RTLMV4g, RTLMV4i, RTLMV4j1–j5, RTLMV4d1, RTLMV4d2, RTLMV4k, RTLMV4e2)
**What the spec requires:** Calling `evaluate(vt)` on a value type returns the list of generated `ObjectMessage`s and asserts on their internal/wire form — `operation.action == "COUNTER_CREATE"/"MAP_CREATE"`, `operation.objectId` `counter:`/`map:` prefix and `@`-suffixed RTO14 derivation, `counterCreateWithObjectId`/`mapCreateWithObjectId` with a 16+-char `nonce` and a JSON `initialValue`, the retained local `counterCreate`/`mapCreate` (`count == 42`, `semantics == "LWW"`, `entries[k].data.<field>`), depth-first ordering of nested creates with cross-referencing `entries[k].data.objectId`, and `mapCreate.entries == {}` for empty entries.
**What the SDK does:** There is no public `evaluate` on the value types. Evaluation into an ordered list of `*_CREATE` wire messages, nonce / `initialValue` / `objectId` derivation, and the `counterCreateWithObjectId` / `mapCreateWithObjectId` wire forms are all internal/wire-level concerns (mapping §13), not reachable through the public typed API. (`PublicAPI::ObjectOperation` itself carries only the resolved `mapCreate`/`counterCreate`, never a `*WithObjectId` getter, per PAOOP1.)
**Workaround in tests:** Only the public construction is exercised (`create(...)` returns a `LiveCounter`/`LiveMap`). The message-generation, nonce/objectId, `initialValue`, retained-create, ordering and empty-entries assertions are dropped with inline notes.
**Tests affected (ValueTypesTest.kt):**
- `RTLCV4 - Evaluation generates COUNTER_CREATE ObjectMessage` (RTLCV4/evaluate-generates-message-0)
- `RTLCV4g5 - Evaluation retains local CounterCreate` (RTLCV4g5/retains-local-counter-create-0)
- `RTLCV4 - Evaluation with count 0` (RTLCV4/evaluate-zero-count-0)
- `RTLMV4 - Evaluation generates MAP_CREATE ObjectMessage` (RTLMV4/evaluate-generates-message-0)
- `RTLMV4j5 - Evaluation retains local MapCreate` (RTLMV4j5/retains-local-map-create-0)
- `RTLMV4d1, RTLMV4d2 - Nested value types produce depth-first ObjectMessages` (RTLMV4d1/nested-value-types-0)
- `RTLMV4e2 - Empty entries produces MapCreate with empty entries` (RTLMV4e2/empty-entries-0)
- `RTLMV4d - Entry value type mapping` (RTLMV4d/entry-value-types-0) — generated `data.<field>` adapted to public `LiveMapValue` union inspection.
- `RTLMV4d - Table-driven MAP_SET value type mapping` (RTLMV4d/map-set-all-types-table-0) — generated `data[field]` (incl. base64 "AQID") adapted to public `LiveMapValue` union inspection.

---

## RTLCV3c / RTLCV4a / RTLMV4a / RTLMV4b / RTLMV4c — wrong-typed value-type `create` args are compile errors, not runtime 40003/40013

**Spec points:** RTLCV3c, RTLCV4a, RTLMV4a, RTLMV4b, RTLMV4c
**What the spec requires:** No validation at creation time (RTLCV3c — `LiveCounter.create("not_a_number")` *succeeds* at create), with validation deferred to evaluation: `LiveCounter.create("not_a_number")` → 40003; `LiveMap.create(null)` → 40003; a non-String key (`{ 123: "value" }`) → 40003; an unsupported value (a function) → 40013.
**What the SDK does:** ably-java's signatures reject all of these at compile time (mapping §6): `LiveCounter.create(@NotNull Number)` rejects a String and rejects null; `LiveMap.create(@NotNull Map<String, LiveMapValue>)` rejects null, enforces String keys, and the `LiveMapValue` union constructs only from the supported types (Boolean, byte[], Number, String, JsonArray, JsonObject, LiveCounter, LiveMap) — an unsupported value cannot be wrapped. So none of these inputs can be written, and the runtime 40003/40013 failures are not expressible.
**Workaround in tests:** Each test body is a documented no-op explaining the compile-time rejection; no runtime assertion is made.
**Tests affected (ValueTypesTest.kt):**
- `RTLCV3c - no validation at creation time` (RTLCV3c/no-validation-at-create-0) — the deliberately-invalid create arg can't be constructed, so "no error at create" isn't demonstrable either.
- `RTLCV4a - Evaluation validates count type` (RTLCV4a/evaluate-validates-count-0)
- `RTLMV4a - Evaluation validates entries type` (RTLMV4a/evaluate-validates-entries-0)
- `RTLMV4b - Evaluation validates key types` (RTLMV4b/evaluate-validates-keys-0)
- `RTLMV4c - Evaluation validates value types` (RTLMV4c/evaluate-validates-values-0)

---

# 4) Intentional deviation — spec point under review (objects)

## RTO18d — `EventEmitter.on(event, listener)` deduplicates an identical listener instance

**Spec points:** RTO18d, RTE4
**What the spec requires:** Registering the **same** listener instance twice for a sync-state event makes
it fire **twice** per emission (RTO18d / RTE4 — registrations are additive).
**What the SDK does:** ably-java's core `EventEmitter.on(event, listener)` stores listeners in a **Map keyed
by the listener instance** (`filters.put(listener, …)`), so a duplicate registration overwrites the first
and the listener fires **once**. This is a long-standing, **deliberate SDK-wide** choice (documented in
`EventEmitter.java`'s own Javadoc as a spec deviation), not a LiveObjects-specific accident.
**Status — intentional deviation (spec point questioned):** the RTO18d requirement is considered dubious — a
listener registered twice runs identical logic, so invoking it twice for one event serves no practical
purpose. ably-java therefore **keeps** the de-duplicating behaviour by design; the spec-correct assertion is
retained behind `RUN_DEVIATIONS` (green by default). No fix is planned unless the spec point is revised. If
compliance were ever required, it should be a **scope-limited** change to the LiveObjects emitters
(`ObjectsStateEmitter`), NOT the core `EventEmitter` — that class backs Connection/Channel/Presence (large
blast radius) and its `off()` would then need to remove *all* matching entries.
**Tests affected (RealtimeObjectTest.kt):**
- `RTO18d - Duplicate listener registered twice fires twice` — env-gated (intentional).
