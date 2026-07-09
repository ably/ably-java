# `objects` UTS → ably-java `liveobjects`: ably-js ⇄ ably-java type/interface map

Read this **before translating any spec from the `objects` module** (target ably-java module
`liveobjects`). The `objects` UTS specs are written in a language-agnostic pseudocode that mirrors the
**ably-js** LiveObjects API — a dynamically-typed surface with a single polymorphic `PathObject` /
`Instance`, `Promise`-returning mutators, and raw JS values. ably-java is a **statically-typed SDK** and
implements the *Typed-SDK variant* of the spec (`RTTS1`–`RTTS10` in `objects-features.md`): it partitions
that one polymorphic class into a typed hierarchy and wraps write values in a union type. So almost every
spec line needs a mechanical rewrite, not a literal transcription. This doc is that rewrite table.

The canonical bridge is the spec's own Interface Definition (`## Interface Definition {#idl}`) and its
`=== Typed-SDK variant (RTTS1-RTTS10) ===` block — ably-java follows the typed variant verbatim. When in
doubt, that IDL is the source of truth; this doc is the applied version of it for ably-java.

## Table of contents

1. [The three layers (don't conflate them)](#1-the-three-layers)
2. [Entry point & channel access](#2-entry-point)
3. [Async: Promise/await → CompletableFuture](#3-async)
4. [Dynamic `PathObject` → typed `PathObject` hierarchy](#4-pathobject)
5. [Dynamic `Instance` → typed `Instance` hierarchy](#5-instance)
6. [Creation value types & the `LiveMapValue` union](#6-value-types)
7. [Mutations (set / remove / increment / decrement)](#7-mutations)
8. [Subscriptions, listeners & events](#8-subscriptions)
9. [Sync-state events (`object.on('synced')`)](#9-sync-state)
10. [`ValueType` & type discrimination](#10-valuetype)
11. [Message / operation types (`PublicAPI::ObjectMessage` →)](#11-messages)
12. [Errors & error codes](#12-errors)
13. [Internal-graph types (unit specs) — important caveat](#13-internal-graph)
14. [Integration-test helpers — REST fixture provisioning](#14-integration-helpers)
15. [Worked example](#15-worked-example)
16. [Quick symbol index](#16-symbol-index)

---

## 1. The three layers <a id="1-the-three-layers"></a>

The single biggest source of confusion: the spec uses the names `LiveMap` / `LiveCounter` for **two
different things**, and a third *internal* layer underneath. Keep them straight:

| Layer | Spec name | ably-js | ably-java | Package |
|---|---|---|---|---|
| **Creation value type** — immutable blueprint you pass *into* `set` | `LiveMap` / `LiveCounter` (the `RTLMV*` / `RTLCV*` classes) | `LiveMap.create()` / `LiveCounter.create()` | `LiveMap` / `LiveCounter` | `io.ably.lib.liveobjects.value` |
| **Public read/write view** — what you navigate & subscribe on | `PathObject`, `Instance` | `PathObject`, `Instance` | typed hierarchy (§4, §5) | base in `io.ably.lib.liveobjects.path` / `.instance`; **typed subtypes in `.path.types` / `.instance.types`** |
| **Internal graph object** — the live CRDT node | `InternalLiveMap` / `InternalLiveCounter` (`RTLM*` / `RTLC*`), `ObjectsPool` | internal | `DefaultLiveMap` / `DefaultLiveCounter` etc. (impl, `:liveobjects` module) | not public — see §13 |

So when a spec says `counter = LiveCounter.create(5)` and passes it to `set`, that's the **value type**
(`io.ably.lib.liveobjects.value.LiveCounter`). When a spec says "the resolved value is an
`InternalLiveCounter` with `.data == 5`", that's the **internal graph node** (§13). When a spec navigates
`root.get("counter").value()`, that's the **public view** (`PathObject`).

---

## 2. Entry point & channel access <a id="2-entry-point"></a>

| Spec / ably-js | ably-java (Kotlin) |
|---|---|
| `channel.object` (objects entry point) | `` channel.`object` `` — a **public field** of type `RealtimeObject`, *not* a method. (Declared `public RealtimeObject object;` on `ChannelBase`.) |
| `root = AWAIT channel.object.get()` | `` val root: LiveMapPathObject = channel.`object`.get().await() `` — returns `CompletableFuture<LiveMapPathObject>` (always a `LiveMapPathObject`, per `RTTS6d`/`RTO23f`). |
| `channel.object.get<MyType>()` (ably-js generic) | **No generic.** ably-java is untyped at the root; you always get a `LiveMapPathObject` and narrow downstream with `as*` casts (§4). Drop the type parameter entirely. |
| Channel needs object modes | `TestRealtimeClient { … }` then `channels.get(name, ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) })`. (`ChannelMode` constants are lower-case: `object_subscribe`, `object_publish`; `ChannelOptions.modes` is a `ChannelMode[]`.) |

> ⚠️ **`object` is a hard keyword in Kotlin.** The entry-point field is named `object` in Java, so from
> Kotlin you **must** escape it with backticks: `` channel.`object` ``. Bare `channel.object` is a compile
> error. This applies everywhere in this doc and in generated tests.

`RealtimeObject` extends `ObjectStateChange` (sync-state subscription, §9). When the plugin isn't installed
the field is `RealtimeObject.Unavailable.INSTANCE`; real tests install it.

---

## 3. Async: Promise / await → CompletableFuture <a id="3-async"></a>

Every spec `AWAIT`/Promise-returning call maps to a `CompletableFuture<…>` in ably-java:

| Spec / ably-js | ably-java return type |
|---|---|
| `AWAIT channel.object.get()` | `CompletableFuture<LiveMapPathObject>` |
| `AWAIT pathObj.set(k, v)` / `.remove(k)` | `CompletableFuture<Void>` |
| `AWAIT counterObj.increment(n)` / `.decrement(n)` | `CompletableFuture<Void>` |
| `AWAIT instance.set(...)` etc. | `CompletableFuture<Void>` |

Subscriptions are **not** futures — `subscribe(...)` returns a `Subscription` synchronously (`@NonBlocking`).

**Awaiting a `CompletableFuture` inside a `runTest { }` body:** use `future.await()` with
`import kotlinx.coroutines.future.await` — the `future` integration ships inside `kotlinx-coroutines-core`
(verified on the version the uts module resolves), so no extra dependency is needed. Use the blocking
`future.get(timeout, unit)` only if a specific test needs to assert synchronously; prefer `await()` so the
test stays within structured concurrency.

---

## 4. Dynamic `PathObject` → typed `PathObject` hierarchy <a id="4-pathobject"></a>

In the spec/ably-js a `PathObject` is polymorphic: `get`, `at`, `value`, `set`, `increment`, `entries`…
all hang off the one object. In ably-java the base `PathObject` exposes **only** the type-agnostic methods;
everything type-specific is moved onto a sub-interface you reach via an `as*` cast.

**Base `PathObject`** (`io.ably.lib.liveobjects.path.PathObject`) — always available. The typed sub-types
returned by the `as*` casts (`LiveMapPathObject`, `LiveCounterPathObject`, `NumberPathObject`,
`StringPathObject`, `BooleanPathObject`, `BinaryPathObject`, `JsonObjectPathObject`, `JsonArrayPathObject`)
live in **`io.ably.lib.liveobjects.path.types`** (not `.path`) — import them from there.

| Spec / ably-js | ably-java |
|---|---|
| `pathObj.path()` | `pathObj.path(): String` |
| `pathObj.instance()` | `pathObj.instance(): Instance?` (null if path resolves to a primitive, or doesn't resolve) |
| `pathObj.compactJson()` | `pathObj.compactJson(): JsonElement?` |
| `pathObj.compact()` | **Not implemented in ably-java** (`RTTS3f`: typed SDKs need not implement `compact`). Use `compactJson()` for snapshot assertions; if a spec genuinely needs the non-JSON `compact()` shape, that's a deviation — flag it. |
| `pathObj.subscribe(listener[, opts])` | `pathObj.subscribe(PathObjectListener[, PathObjectSubscriptionOptions]): Subscription` (§8) |
| *(typed-SDK addition)* exists check | `pathObj.exists(): Boolean` (`RTTS4a`) |
| `pathObj.getType()` | `pathObj.getType(): ValueType?` — null when nothing resolves (§10) |
| — cast helpers — | `asLiveMap()`, `asLiveCounter()`, `asNumber()`, `asString()`, `asBoolean()`, `asBinary()`, `asJsonObject()`, `asJsonArray()` |

**`PathObject` casts never throw** (`RTTS5d`) — they only re-wrap. A wrong cast surfaces later: read ops on
the wrong-typed view return `null`/empty; write ops throw (§12). So `root.get("k").asNumber().value()`
returns `null` if `k` isn't a number, rather than throwing.

**Map-only methods** — require `asLiveMap()` → `LiveMapPathObject`:

| Spec / ably-js (on a `PathObject`) | ably-java |
|---|---|
| `pathObj.get(key)` | `pathObj.asLiveMap().get(key): PathObject` |
| `pathObj.at("a.b.c")` | `pathObj.asLiveMap().at("a.b.c"): PathObject` |
| `pathObj.entries()` | `pathObj.asLiveMap().entries(): Iterable<Map.Entry<String, PathObject>>` |
| `pathObj.keys()` / `.values()` | `pathObj.asLiveMap().keys(): Iterable<String>` / `.values(): Iterable<PathObject>` |
| `pathObj.size()` | `pathObj.asLiveMap().size(): Long?` |
| `pathObj.set(key, value)` | `pathObj.asLiveMap().set(key, LiveMapValue.of(value))` (§6, §7) |
| `pathObj.remove(key)` | `pathObj.asLiveMap().remove(key)` |

> The **root** is already a `LiveMapPathObject` (from `channel.object.get()`), so `root.get(...)` /
> `root.set(...)` need no cast — only deeper, freshly-navigated `PathObject`s do.

**Iterating & membership.** `entries()` returns `Iterable<Map.Entry<String, PathObject>>`; `keys()` /
`values()` return `Iterable<…>`. The spec's tuple-destructuring loops and `IN` membership map to Kotlin
directly:

```text
# spec
FOR [key, pathObj] IN root.entries(): …
ASSERT "name" IN root.keys()
keys = list(root.keys())
```
```kotlin
for ((key, pathObj) in root.entries()) { … }     // Map.Entry destructures into (key, value)
assertTrue("name" in root.keys())                 // Kotlin `in` -> Iterable.contains
val keys = root.keys().toList()                    // when the spec materialises a list / checks length
```

These live on `LiveMapPathObject`, so a *navigated* node needs `asLiveMap()` first
(`root.get("score").asLiveMap().entries()`); `root` itself doesn't.

**Counter-only methods** — require `asLiveCounter()` → `LiveCounterPathObject`:

| Spec / ably-js | ably-java |
|---|---|
| `pathObj.value()` *(when it's a counter)* | `pathObj.asLiveCounter().value(): Double?` (counter value, else null) |
| `pathObj.increment([n])` | `pathObj.asLiveCounter().increment()` / `.increment(n: Number)` |
| `pathObj.decrement([n])` | `pathObj.asLiveCounter().decrement()` / `.decrement(n: Number)` |

**Primitive value reads** — the spec's single `pathObj.value()` splits by primitive type. Cast to the
matching primitive sub-type (`NumberPathObject`, `StringPathObject`, `BooleanPathObject`, `BinaryPathObject`,
`JsonObjectPathObject`, `JsonArrayPathObject`) and call `value()` (each returns its type or `null`):

| Spec resolves to | ably-java |
|---|---|
| number | `pathObj.asNumber().value(): Number?` |
| string | `pathObj.asString().value(): String?` |
| boolean | `pathObj.asBoolean().value(): Boolean?` |
| binary | `pathObj.asBinary().value(): ByteArray?` |
| JSON object | `pathObj.asJsonObject().value(): JsonObject?` |
| JSON array | `pathObj.asJsonArray().value(): JsonArray?` |

> The dynamic `PathObject#value` (`RTPO7`) returns "the resolved counter value *or* any primitive". The
> typed `value()` accessors are **stricter** (`RTTS6g`): each returns `null` unless the resolved value is
> exactly that type. Translate "ASSERT pathObj.value() == 5" against a counter as
> `assertEquals(5.0, root.get("c").asLiveCounter().value())`, not `asNumber()`.
>
> **Number comparison gotcha.** Specs assert against integer literals (`value() == 110`, `size() == 7`),
> but ably-java returns wider numeric types: counter `value()` is `Double` (assert `110.0`); primitive
> `asNumber().value()` is a boxed `Number` whose runtime type follows JSON decoding; and `size()` is `Long`
> (assert `7L`). `assertEquals` treats `110.0`/`110`/`110L` as unequal across `Double`/`Int`/`Long`, so
> normalise: `assertEquals(110.0, obj.asNumber().value()?.toDouble())`, `assertEquals(7L, root.size())`. A
> spec `size() == null` (called on a non-map) is `assertNull(node.asLiveMap().size())` — the cast doesn't
> throw and `size()` returns null off-map.

**Path strings & dot-escaping (`RTPO4`/`RTPO4b`/`RTPO6`).** `path()` returns a dot-delimited `String`; the
root's is `""`. A literal dot *inside* a segment is escaped as `\.`, and `at()` parses `\.` back to a
literal dot — so `path()` round-trips. Mind Kotlin's own backslash escaping (`"a\\.b.c"` is the string
`a\.b.c`):

```text
# spec                                    # ably-java (Kotlin)
ASSERT root.path() == ""                   assertEquals("", root.path())
ASSERT root.get("a").get("b").path()       assertEquals("a.b", root.get("a").asLiveMap().get("b").path())
       == "a.b"
po = root.at("a\.b.c")                      val po = root.at("a\\.b.c")          // segments ["a.b", "c"]
ASSERT po.path() == "a\.b.c"               assertEquals("a\\.b.c", po.path())
```

---

## 5. Dynamic `Instance` → typed `Instance` hierarchy <a id="5-instance"></a>

Same partition as `PathObject`, with two differences: the base is **abstract / never instantiated**
directly (`RTTS7e`), and the casts **throw** on mismatch instead of degrading (`RTTS9d`) — because an
`Instance` wraps an already-resolved value of known type.

**Base `Instance`** (`io.ably.lib.liveobjects.instance.Instance`):

| Spec / ably-js | ably-java |
|---|---|
| `instance.getType()` | `instance.getType(): ValueType` (non-null — never `UNKNOWN` in normal operation, `RTTS8a`) |
| `instance.compactJson()` | `instance.compactJson(): JsonElement` (**non-null**, `RTINS11c`) |
| `instance.compact()` | **Not implemented in ably-java** (`RTTS7d`, same as `PathObject`). Use `compactJson()`; flag a deviation if a spec needs `compact()`. |
| — casts — | `asLiveMap()`, `asLiveCounter()`, `asNumber()`, `asString()`, `asBoolean()`, `asBinary()`, `asJsonObject()`, `asJsonArray()` — **throw `IllegalStateException`** (or `AblyException` 400/92007) on type mismatch |

> `subscribe` is **not** on the base `Instance` (`RTTS7b`) — only on `LiveMapInstance` / `LiveCounterInstance`.
> `id`, `value`, `get`, `set`, … are all partitioned onto sub-classes too (`RTTS7c`).

**`LiveMapInstance`** (`…instance.types.LiveMapInstance`):

| Spec / ably-js | ably-java |
|---|---|
| `instance.id` | `getId(): String` (non-null, `RTTS10a`) |
| `instance.get(key)` | `get(key): Instance?` |
| `instance.entries()` / `.keys()` / `.values()` | `entries(): Iterable<Map.Entry<String, Instance>>` / `keys(): Iterable<String>` / `values(): Iterable<Instance>` |
| `instance.size()` | `size(): Long` (non-null here, `RTTS10a`) |
| `instance.set(key, value)` / `.remove(key)` | `set(key, LiveMapValue.of(value))` / `remove(key)` → `CompletableFuture<Void>` |
| `instance.subscribe(listener)` | `subscribe(InstanceListener): Subscription` |
| `instance.compactJson()` | `compactJson(): JsonObject` (narrowed) |

**`LiveCounterInstance`** (`…instance.types.LiveCounterInstance`):

| Spec / ably-js | ably-java |
|---|---|
| `instance.id` | `getId(): String` |
| `instance.value()` | `value(): Double` (non-null, `RTTS10b`) |
| `instance.increment([n])` / `.decrement([n])` | `increment()` / `increment(n)` / `decrement()` / `decrement(n)` → `CompletableFuture<Void>` |
| `instance.subscribe(listener)` | `subscribe(InstanceListener): Subscription` |
| `instance.compactJson()` | `compactJson(): JsonPrimitive` (narrowed) |

**Primitive instances** (`NumberInstance`, `StringInstance`, `BooleanInstance`, `BinaryInstance`,
`JsonObjectInstance`, `JsonArrayInstance`) are **read-only**: each exposes a non-null `value()` of its type
and a narrowed `compactJson()`; no `id`, `get`, `set`, `subscribe`, etc.

---

## 6. Creation value types & the `LiveMapValue` union <a id="6-value-types"></a>

ably-java can't accept "any JS value" into `set`, so it uses a tagged union `LiveMapValue` and dedicated
immutable creation value types.

| Spec / ably-js | ably-java |
|---|---|
| `LiveCounter.create()` | `LiveCounter.create(): LiveCounter` (`io.ably.lib.liveobjects.value`) |
| `LiveCounter.create(5)` | `LiveCounter.create(5)` (arg is `Number`) |
| `LiveMap.create()` | `LiveMap.create(): LiveMap` |
| `LiveMap.create({ a: 1, b: "x" })` | `LiveMap.create(mapOf("a" to LiveMapValue.of(1), "b" to LiveMapValue.of("x")))` — entries are `Map<String, LiveMapValue>` |
| a raw value passed to `set` | wrap it: `LiveMapValue.of(value)` |

`LiveMapValue.of(...)` overloads: `Boolean`, `ByteArray` (binary), `Number`, `String`, `JsonArray`,
`JsonObject`, `LiveCounter` (value type), `LiveMap` (value type). Inspect with `isNumber()` / `getAsNumber()`
etc. when a spec asserts on a constructed value's contents.

> **Type-safety turns several "invalid input" spec cases into compile errors, not runtime assertions.**
> Where a spec feeds a deliberately wrong type and expects an `ErrorInfo`, ably-java's signatures reject it
> at compile time, so the test isn't expressible — note it as a deviation rather than forcing it:
> - Passing a **graph object / public view** (`PathObject`, `Instance`, a live object) as a map value
>   (`RTLMV4c1`, runtime `40013` in the dynamic API) — blocked by the `LiveMapValue` union.
> - **Wrong-typed `create` args**, e.g. `LiveCounter.create("not_a_number")` (spec expects `40003`) —
>   blocked by `create(Number)`; `LiveMap.create` likewise takes `Map<String, LiveMapValue>` so non-`Dict`
>   / non-`String`-key / unsupported-value entry cases (`RTLMV4a`/`b`/`c`) can't be constructed either.
>
> Validation cases on *values the type system still allows* (e.g. a NaN / out-of-range `Number`) remain
> real runtime assertions — only the cases the signature outright forbids become deviations.

---

## 7. Mutations (set / remove / increment / decrement) <a id="7-mutations"></a>

Putting §4 + §6 together — the canonical write translations:

```text
# spec
AWAIT root.set("count", LiveCounter.create(0))
AWAIT root.get("count").increment(5)
AWAIT root.set("name", "alice")
AWAIT root.remove("name")
```
```kotlin
// ably-java (root is a LiveMapPathObject)
root.set("count", LiveMapValue.of(LiveCounter.create(0))).await()
root.get("count").asLiveCounter().increment(5).await()
root.set("name", LiveMapValue.of("alice")).await()
root.remove("name").await()
```

- `set` / `remove` live on `LiveMapPathObject` (or `LiveMapInstance`); navigate+`asLiveMap()` first unless
  you're on the root or an already-typed map view.
- `increment` / `decrement` live on `LiveCounterPathObject` (or `LiveCounterInstance`); `asLiveCounter()`
  first.
- Default-amount forms exist: `increment()` ≡ `increment(1)`, `decrement()` ≡ `decrement(1)`.

### Wrong-type write failures still go *through* the cast

A common spec shape is a write on the wrong kind of object, expecting a runtime error — e.g.
`AWAIT root.increment(5) FAILS WITH error` (increment on a map) or `counter.set("k", v) FAILS WITH error`.
In the dynamic API every method exists on every `PathObject`, so the call is expressible and throws at
runtime. In ably-java the typed view **doesn't have that method at all** (`LiveMapPathObject` has no
`increment`; `LiveCounterPathObject` has no `set`), so calling it directly is a *compile* error — not the
runtime failure the spec is testing.

To translate these, cast to the view whose write method you need (the `PathObject` cast never throws,
`RTTS5d`), then assert the **operation** throws — that's where the `92007` surfaces:

```text
# spec: increment on a map fails
AWAIT root.increment(5) FAILS WITH error   # code 92007
```
```kotlin
val ex = assertFailsWith<AblyException> { root.asLiveCounter().increment(5).await() }
assertEquals(92007, ex.errorInfo.code)
```

So "can't call increment on a map" is **not** "not expressible" — it's `asLiveCounter().increment(...)`
plus an assertion on the throw. (Contrast §6: invalid *value* / *argument-type* cases genuinely aren't
expressible, because the union/`create(Number)` signatures reject them at compile time.)

---

## 8. Subscriptions, listeners & events <a id="8-subscriptions"></a>

ably-js passes a closure and gets back a `Subscription` with `.unsubscribe()`. ably-java uses named
single-method listener interfaces; the event is an object with getters.

| Spec / ably-js | ably-java |
|---|---|
| `sub = pathObj.subscribe((event) => { … })` | `val sub = pathObj.subscribe(PathObjectListener { event -> … })` |
| `pathObj.subscribe(listener, { depth: 2 })` | `pathObj.subscribe(listener, PathObjectSubscriptionOptions(2))` (no-arg ctor = unlimited depth) |
| `sub = instance.subscribe((event) => { … })` | `val sub = mapOrCounterInstance.subscribe(InstanceListener { event -> … })` |
| `sub.unsubscribe()` | `sub.unsubscribe()` |
| `event.object` | `event.getObject()` — a `PathObject` (path sub) / `Instance` (instance sub) |
| `event.message` | `event.getMessage(): ObjectMessage?` (§11) |

Listener SAMs: `PathObjectListener.onUpdated(PathObjectSubscriptionEvent)`,
`InstanceListener.onUpdated(InstanceSubscriptionEvent)`. In Kotlin you can pass a lambda for either (SAM
conversion). `PathObjectSubscriptionOptions(depth)` throws `AblyException` 400/`40003` for non-positive
depth (`RTPO19c1`).

> **`LiveObjectUpdate` is not the public event.** `live_object_subscribe.md` cites the internal `RTLO4b`
> `LiveObjectUpdate` (fields `update` / `noop` / `objectMessage` / `tombstone`), but it subscribes through
> the *public* `instance.subscribe(...)`, whose ably-java event is `InstanceSubscriptionEvent` — only
> `getObject()` + `getMessage()`, **no diff/`noop`/`tombstone` accessors**. So "listener fired N times" and
> "returns a `Subscription`" translate directly, but any assertion on the `LiveObjectUpdate` *diff* fields
> is internal (§13) — adapt or skip with a deviation.

---

## 9. Sync-state events (`object.on('synced')`) <a id="9-sync-state"></a>

`RealtimeObject` extends `ObjectStateChange`. The ably-js string-event API becomes an enum + listener:

| Spec / ably-js | ably-java |
|---|---|
| `channel.object.on('synced', cb)` | `` channel.`object`.on(ObjectStateEvent.SYNCED, listener): Subscription `` |
| `channel.object.on('syncing', cb)` | `` channel.`object`.on(ObjectStateEvent.SYNCING, listener) `` |
| `channel.object.off(cb)` | `` channel.`object`.off(listener) `` |
| remove all | `` channel.`object`.offAll() `` |

Listener: `ObjectStateChange.Listener.onStateChanged(ObjectStateEvent)`. Enum `ObjectStateEvent { SYNCING,
SYNCED }` (`io.ably.lib.liveobjects.state`).

---

## 10. `ValueType` & type discrimination <a id="10-valuetype"></a>

ably-js uses string-literal type tags; ably-java has an enum `io.ably.lib.liveobjects.ValueType`:

| Spec value category | `ValueType` |
|---|---|
| string / number / boolean / binary | `STRING` / `NUMBER` / `BOOLEAN` / `BINARY` |
| JSON object / JSON array | `JSON_OBJECT` / `JSON_ARRAY` |
| live map / live counter | `LIVE_MAP` / `LIVE_COUNTER` |
| present but unrecognised | `UNKNOWN` |

`pathObj.getType()` returns `null` when nothing resolves (distinct from `UNKNOWN`); `instance.getType()` is
non-null. Use `getType()` for "what is it" assertions and the matching `as*` cast to read it.

---

## 11. Message / operation types <a id="11-messages"></a>

The spec's `PublicAPI::ObjectMessage` / `PublicAPI::ObjectOperation` (the `PAOM*` / `PAOOP*` types,
delivered to subscription listeners) map to ably-java interfaces with getters (package
`io.ably.lib.liveobjects.message`). The `PublicAPI::` prefix is dropped — ably-java exposes them as
`ObjectMessage` / `ObjectOperation`.

> **Getter-only, no *public* constructor — use the `buildPublicObjectMessage` helper.** In normal use you
> obtain an `ObjectMessage` from a subscription event (`event.getMessage()`, §8); there is no public
> factory. The spec's explicit construction-from-wire (`PublicObjectMessage.fromObjectMessage(source,
> channel)` / `PublicObjectOperation.fromObjectOperation(op)`, `PAOM3`/`PAOOP3`, in
> `public_object_message.md`) is `internal` to `:liveobjects` — but the unit helpers expose it **by
> reflection** as `buildPublicObjectMessage(wireJson, channelName)` (§13). So `public_object_message.md` is
> translatable: build the source with the op builders (`buildMapSet(...)`, `buildCounterInc(...)`, …) and
> assert the public getters on the result.

`ObjectMessage`: `getId()`, `getClientId()`, `getConnectionId()`, `getTimestamp(): Long?`, `getChannel():
String`, `getOperation(): ObjectOperation`, `getSerial()`, `getSerialTimestamp(): Long?`, `getSiteCode()`,
`getExtras(): JsonObject?`. (Timestamps are epoch-millis `Long`, not a `Time` object.)

`ObjectOperation`: `getAction(): ObjectOperationAction`, `getObjectId(): String`, and one non-null payload
getter matching the action — `getMapCreate()`, `getMapSet()`, `getMapRemove()`, `getCounterCreate()`,
`getCounterInc()`, `getObjectDelete()`, `getMapClear()`.

**The spec accesses these as dotted property chains and compares `action` to a *string literal*; ably-java
uses getters and an *enum constant*.** Translate the chain getter-by-getter and the string tag to the enum:

```text
# spec
ASSERT msg.operation.action == "MAP_SET"
ASSERT msg.operation.mapSet.key == "name"
ASSERT msg.operation.mapSet.value.string == "blue"
ASSERT msg.operation.counterInc.number == 42
ASSERT msg.operation.mapCreate == null
```
```kotlin
val op = msg.operation
assertEquals(ObjectOperationAction.MAP_SET, op.action)   // string "MAP_SET" -> enum constant
assertEquals("name", op.mapSet!!.key)
assertEquals("blue", op.mapSet!!.value.string)           // ObjectData.getString()
assertEquals(42.0, op.counterInc!!.number)               // getNumber(): Double -> use .0
assertNull(op.mapCreate)
```

(Java getters read as Kotlin properties: `msg.operation` ≡ `getOperation()`, `op.mapSet` ≡ `getMapSet()`,
etc. The payload getter for the non-matching actions returns `null`, so `mapCreate == null` → `assertNull`.)
Every spec string action tag maps to its `ObjectOperationAction` constant: `"MAP_SET"`→`MAP_SET`,
`"COUNTER_INC"`→`COUNTER_INC`, `"OBJECT_DELETE"`→`OBJECT_DELETE`, etc. The same string-tag→enum rule applies
to map semantics (`"lww"`→`ObjectsMapSemantics.LWW`) and value types (§10).

| Spec type | ably-java | Notable getters |
|---|---|---|
| `ObjectOperationAction` | enum `ObjectOperationAction` | `MAP_CREATE, MAP_SET, MAP_REMOVE, COUNTER_CREATE, COUNTER_INC, OBJECT_DELETE, MAP_CLEAR, UNKNOWN` |
| `MapSet` | `MapSet` | `getKey(): String`, `getValue(): ObjectData` |
| `MapRemove` | `MapRemove` | `getKey(): String` |
| `MapCreate` | `MapCreate` | `getSemantics(): ObjectsMapSemantics`, `getEntries(): Map<String, ObjectsMapEntry>` |
| `CounterCreate` | `CounterCreate` | `getCount(): Double` |
| `CounterInc` | `CounterInc` | `getNumber(): Double` |
| `ObjectDelete` / `MapClear` | marker interfaces | no methods |
| `ObjectData` (leaf value) | `ObjectData` | `getObjectId()`, `getString()`, `getNumber(): Double?`, `getBoolean()`, `getBytes(): ByteArray?`, `getJson(): JsonElement?` |
| `ObjectsMapEntry` | `ObjectsMapEntry` | `getTombstone(): Boolean?`, `getTimeserial()`, `getSerialTimestamp(): Long?`, `getData(): ObjectData?` |
| map semantics | enum `ObjectsMapSemantics` | `LWW, UNKNOWN` |

> Note `PublicAPI::ObjectOperation` carries only `mapCreate`/`counterCreate` (the `*WithObjectId` outbound
> variants are resolved back to their `MapCreate`/`CounterCreate` forms, `PAOOP1`). Don't expect a
> `getMapCreateWithObjectId()` on the public type.

---

## 12. Errors & error codes <a id="12-errors"></a>

Spec assertions like `FAILS WITH error code 92007` map to ably-java exceptions:

| Spec failure | ably-java |
|---|---|
| async op rejects with `ErrorInfo` code N | the `CompletableFuture` completes exceptionally with `AblyException`. With `.await()` the cause is rethrown directly, so `assertFailsWith<AblyException> { … .await() }` then `ex.errorInfo.code == N`. With blocking `.get()` you instead catch `ExecutionException` and read `.cause` |
| wrong write method for the type (e.g. `increment` on a map, `set` on a counter) | the typed view lacks the method, so cast first (`asLiveCounter()` / `asLiveMap()` — never throws, `RTTS5d`) then the **operation** throws `AblyException` 400/`92007`. See §7 "Wrong-type write failures" |
| `Instance` `as*` cast on wrong type | **`IllegalStateException`** (or `AblyException` 400/`92007`) — thrown synchronously (`RTTS9d`) |
| `PathObject` `as*` cast on wrong type | **never throws** (`RTTS5d`) — failure shows up on the subsequent read (null) or write (throws, above) |
| invalid value into `set` (graph object / view) | `AblyException` 400 / code `40013` (`RTLMV4c1`) — usually not expressible in ably-java's typed `set`; treat as a deviation |
| non-positive subscription `depth` | `AblyException` 400 / code `40003` (`PathObjectSubscriptionOptions(int)`) |
| write where path doesn't resolve | `AblyException` 400 / code `92005` |
| write where value isn't the required type | `AblyException` 400 / code `92007` |
| `get()` / op when channel lacks the object mode (`RTO23a`/`RTO2a2`) | `AblyException` 400 / code `40024` |
| `get()` / access when channel is DETACHED or FAILED (`RTO23b`/`RTO25`) | `AblyException` 400 / code `90001` |
| channel enters DETACHED/SUSPENDED/FAILED while awaiting SYNCED (`RTO20e`/`RTO23c`) | `AblyException` 400 / code `92008` |
| write while `echoMessages` is false (`RTO26c`) | `AblyException` 400 / code `40000` |

Assert the code as a plain int — `assertEquals(90001, ex.errorInfo.code)` — matching the spec's
`error.code == 90001`; error codes are int literals, not enums (unlike the action / semantics / value-type
tags). The `90000` a spec injects via a mocked `ERROR`/`DETACHED` `ProtocolMessage` is the channel-level
error, not an objects code — it's what drives the channel into the state that makes the objects call fail.

**Nested cause (`error.cause.code`).** `ErrorInfo` has no `cause` field, so a spec's nested
`error.cause.code` (e.g. `RTO20e`: top-level `92008` plus cause `90000`) lives on the **exception's** Java
cause — the objects layer sets it to the underlying `AblyException`. Read it by casting the cause:

```kotlin
assertEquals(92008, ex.errorInfo.code)
assertEquals(90000, assertIs<AblyException>(ex.cause).errorInfo.code)
```

---

## 13. Internal-graph types (unit specs) — important caveat <a id="13-internal-graph"></a>

Several **unit** specs assert on the **internal CRDT graph**, not the public API:
`InternalLiveCounter.data`, `InternalLiveMap.siteTimeserials`, `ObjectsPool.syncState`, `LiveObjectUpdate`,
`applyOperation(msg, source)`, object-id generation (`RTO14`), the `*CreateWithObjectId` wire variants and
`generateObjectId`, etc. Specs that are wholly or mostly internal:

- `objects_pool.md`, `parent_references.md`, `object_id.md` — pool sync state, the reverse parent-reference
  graph, and object-id generation: entirely internal.
- the internal-state assertions in `live_counter.md` / `live_map.md` (`.data`, `.siteTimeserials`,
  `.createOperationIsMerged`, `.isTombstone`, `applyOperation`, `replaceData`) — internal; their
  public-facing read/write counterparts live in `live_counter_api.md` / `live_map_api.md`.
- `value_types.md` — the *public* `LiveMap.create` / `LiveCounter.create` surface maps via §6, but the
  evaluation half (`COUNTER_CREATE` / `MAP_CREATE` `ObjectMessage` generation, nonce/`initialValue`/
  `objectId` derivation, the `*WithObjectId` wire forms) is internal/wire-level.
- `realtime_object.md` — **mixed**: `get()` (`RTO23`, incl. the `40024`/`90001`/`92008` precondition cases)
  is public and maps via §2/§12, but `publish` / `publishAndApply` (`RTO15`/`RTO20`, marked `internal` in the
  IDL) and the OBJECT/ACK wire assertions are internal.
- `public_object_message.md` — **translatable** via the `buildPublicObjectMessage` helper (below), which
  reflectively performs the `PAOM3`/`PAOOP3` construction (`WireObjectMessage` → `DefaultObjectMessage`)
  that is otherwise `internal`. Build the source with the op builders and assert the public getters (§11).

In ably-java these are **not public**. They live in the `:liveobjects` module as `Default*` / `Wire*` /
`ResolvedValue` / `Leaf` / `MapRef` / `CounterRef` classes (package `io.ably.lib.liveobjects.*`,
implementation source set). The `uts` module keeps them **off its compile classpath** (it compiles against
`:java` only) but now has `testRuntimeOnly(project(":liveobjects"))`, so the helpers reach the internal
wire/message classes **by reflection** at runtime. Consequences when translating:

- **Public-API unit specs** (`path_object*.md`, `instance.md`, `live_object_subscribe.md`,
  `public_object_message.md`, and the public-surface parts of `realtime_object.md` and `value_types.md`)
  translate cleanly against the §1–§12 map + the helpers below, and compile against `:java`. (Note
  `path_object.md` / `instance.md` also contain `compact()` cases, which are deviations per §4/§5 since
  ably-java implements only `compactJson()`.)
- **Internal-graph unit specs** (`objects_pool.md`, `parent_references.md`, the internal-state assertions in
  `live_counter.md` / `live_map.md`) assert on internal CRDT state the public API can't see. Options: (a)
  add reflective accessors to the helpers for the `Default*`/internal classes (the technique
  `buildPublicObjectMessage` and `infra/unit/Utils.kt` already use), (b) translate them in the
  `:liveobjects` module's own test source where the types are directly accessible, or (c) skip them. Flag
  rather than forcing a public-API assertion that can't reach internal state.
- Spec name → ably-java impl (for orientation, not public use): `InternalLiveMap` → `DefaultLiveMap`,
  `InternalLiveCounter` → `DefaultLiveCounter`, the public-view impls are `DefaultPathObject` /
  `DefaultLiveMapPathObject` / `DefaultInstance` / …, wire form is `WireObjectMessage` /
  `WireObjectOperation` / `WireObjectState` etc.

### Unit-test helpers — `standard_test_pool.md` → `Helpers.kt`

Every objects unit spec opens with `setup_synced_channel` and constructs protocol/object messages with the
`build_*` helpers. These are implemented in
`uts/src/test/kotlin/io/ably/lib/uts/unit/liveobjects/Helpers.kt` — **call them; don't hand-roll the mock
setup or message JSON.**

| Spec helper | `Helpers.kt` |
|---|---|
| `{ client, channel, root, mock_ws } = AWAIT setup_synced_channel("test")` | `val (client, channel, root, mockWs) = setupSyncedChannel("test")` (`suspend`, returns `SyncedChannel`) |
| `setup_synced_channel_no_ack(...)` | `setupSyncedChannelNoAck(...)` |
| `build_object_sync_message` / `build_object_message` / `build_ack_message` | `buildObjectSyncMessage` / `buildObjectMessage` / `buildAckMessage` → `ProtocolMessage` |
| `build_counter_inc` / `build_map_set` / `build_map_remove` / `build_map_clear` / `build_object_delete` / `build_counter_create` / `build_map_create` | same names camelCased → wire `JsonObject` |
| `build_object_state` / `build_object_message_with_state` | `buildObjectState` / `buildObjectMessageWithState` |
| `build_public_object_message(msg, channel)` | `buildPublicObjectMessage(wireJson, channel)` (reflective; §11) |
| `STANDARD_POOL_OBJECTS` | `STANDARD_POOL_OBJECTS` |
| inline ObjectData / map-entry / state fragments | `dataString` / `dataNumber` / `dataBoolean` / `dataObjectId` / `dataBytes` / `dataJson`, `mapEntry`, `mapState`, `counterState`, `mapCreateOp`, `counterCreateOp` |

`mock_ws.send_to_client(...)` is the existing `mockWs.sendToClient(...)` (§ mock API in the main skill). The
wire `action` / `semantics` are integer enum codes — the builders emit the codes for you.

> **Runtime caveat:** `setupSyncedChannel` returns only once `RealtimeObject.get()` resolves, which needs
> the `:liveobjects` SDK's OBJECT_SYNC processing. Until that lands the helpers **compile** and the test
> structure is correct, but the setup throws at runtime — i.e. translate-only today, runnable once the SDK
> is implemented. (`buildPublicObjectMessage` does *not* depend on this — the message/operation layer is
> implemented, so those tests can run now.)

(For the **integration** tier's REST fixture helper — `provision_objects_via_rest` — see §14.)

---

## 14. Integration-test helpers — REST fixture provisioning (`standard_test_pool.md` → integration `Helpers.kt`) <a id="14-integration-helpers"></a>

Some objects **integration** specs (tier `integration/standard`) seed object state over REST *before* the
realtime client connects, via the spec's `## REST Fixture Provisioning` helper `provision_objects_via_rest`.
Its ably-java translation lives in
`uts/src/test/kotlin/io/ably/lib/uts/integration/standard/liveobjects/Helpers.kt` (package
`io.ably.lib.uts.integration.standard.liveobjects`) — **call it; don't hand-roll the REST request or payload
JSON.** (Currently only `objects/integration/RTPO15` uses it.) Unlike the unit helpers (§13), this needs no
reflection and no `:liveobjects` dependency — it compiles and runs against `:java`'s public `AblyRest`.

| Spec helper / operation shape | integration `Helpers.kt` |
|---|---|
| `provision_objects_via_rest(api_key, channel_name, operations)` | `provisionObjectsViaRest(apiKey, channelName, operations: List<JsonObject>): List<String>` (POSTs the op(s); returns created/updated `objectIds`) |
| op `{ mapSet: { key, value }, objectId/path }` | `mapSetOp(key, value, objectId = …, path = …, id = …)` |
| op `{ mapRemove: { key }, objectId/path }` | `mapRemoveOp(key, objectId = …, path = …, id = …)` |
| op `{ mapCreate: { semantics: 0, entries }, [objectId/path] }` | `mapCreateOp(entries: Map<String, JsonObject>, semantics = 0, objectId = …, path = …, id = …)` |
| op `{ counterCreate: { count }, [objectId/path] }` | `counterCreateOp(count, objectId = …, path = …, id = …)` |
| op `{ counterInc: { number }, objectId/path }` | `counterIncOp(number, objectId = …, path = …, id = …)` |
| value `{ string }` / `{ number }` / `{ boolean }` / `{ bytes }` / `{ objectId }` | `valueString` / `valueNumber` / `valueBoolean` / `valueBytes` / `valueObjectId` (each → `JsonObject`; `valueString` / `valueBytes` take an optional `encoding`) |

> **V2 REST format.** These builders follow the LiveObjects **V2** objects REST API (the OpenAPI is the
> source of truth): `POST /channels/{channel}/object` (**singular**), body is a single operation **or** a
> bare array (no `messages` wrapper), each op named by its payload key (`mapSet` / `mapRemove` / `mapCreate`
> / `counterInc` / `counterCreate`) with an `objectId`/`path` target (and optional idempotency `id`). The
> spec's `standard_test_pool.md` originally showed the legacy `POST …/objects` + `{ messages: [...] }`
> envelope on the legacy `sandbox-rest.ably.io` host; both were aligned upstream — to this V2 shape and to
> the canonical nonprod sandbox host `sandbox.realtime.ably-nonprod.net` — in ably/specification#497.
>
> **Sandbox host.** `provisionObjectsViaRest` sets `restHost = SandboxApp.sandboxHost`
> (`sandbox.realtime.ably-nonprod.net`) — the same nonprod host `SandboxApp` and the realtime clients use,
> **not** `environment="sandbox"` (which resolves to the legacy `sandbox-rest.ably.io`, and
> can't be combined with `restHost` per `Hosts.java` TO3k2/TO3k3). The REST call hits the live sandbox
> today; the realtime client it provisions for only *observes* the data once the SDK's OBJECT_SYNC +
> `RealtimeObject.get()` land.

---

## 15. Worked example <a id="15-worked-example"></a>

Spec pseudocode (public-API style):

```text
test "increments a nested counter and observes it"
  root = AWAIT channel.object.get()
  AWAIT root.set("game", LiveMap.create({ score: LiveCounter.create(0) }))
  scoreSub = root.at("game.score").subscribe((event) => { received = event })
  AWAIT root.at("game.score").increment(10)
  ASSERT root.at("game.score").value() == 10
  ASSERT received.object.value() == 10
  scoreSub.unsubscribe()
```

ably-java / Kotlin translation:

```kotlin
val root: LiveMapPathObject = channel.`object`.get().await()   // `object` is a Kotlin keyword — backticks required

root.set(
    "game",
    LiveMapValue.of(LiveMap.create(mapOf("score" to LiveMapValue.of(LiveCounter.create(0))))),
).await()

var received: PathObjectSubscriptionEvent? = null
val scoreSub = root.at("game.score").subscribe(PathObjectListener { event -> received = event })

root.at("game.score").asLiveCounter().increment(10).await()

assertEquals(10.0, root.at("game.score").asLiveCounter().value())
assertEquals(10.0, received!!.getObject().asLiveCounter().value())
scoreSub.unsubscribe()
```

Note the four mechanical rewrites: `get()` → `.await()`; nested `LiveMap.create`/`LiveCounter.create`
wrapped in `LiveMapValue.of`; `at(...)` followed by `asLiveCounter()` before counter ops; `event.object`
→ `event.getObject()` and re-cast.

---

## 16. Quick symbol index <a id="16-symbol-index"></a>

| ably-js / spec symbol | ably-java |
|---|---|
| `channel.object` | field `` channel.`object` `` : `RealtimeObject` (Kotlin keyword → backticks) |
| `channel.object.get()` | `` channel.`object`.get() `` → `CompletableFuture<LiveMapPathObject>` |
| `PathObject` (polymorphic) | base `PathObject` + `asLiveMap()`/`asLiveCounter()`/`as<Primitive>()` |
| `Instance` (polymorphic) | abstract `Instance` + `as*` (throwing) |
| `pathObj.get(k)` / `.at(p)` | `pathObj.asLiveMap().get(k)` / `.at(p)` |
| `pathObj.value()` | `pathObj.as<Type>().value()` (typed, null on mismatch) |
| `pathObj.set(k, v)` / `.remove(k)` | `pathObj.asLiveMap().set(k, LiveMapValue.of(v))` / `.remove(k)` |
| `pathObj.increment(n)` / `.decrement(n)` | `pathObj.asLiveCounter().increment(n)` / `.decrement(n)` |
| `op FAILS WITH <code>` (wrong method for type) | cast to the needed view, then assert the op throws: `assertFailsWith<AblyException> { node.asLiveCounter().increment(n).await() }` (§7, §12) |
| `FOR [k, v] IN x.entries()` | `for ((k, v) in x.asLiveMap().entries())` |
| `"k" IN x.keys()` / `list(x.keys())` | `"k" in x.asLiveMap().keys()` / `x.asLiveMap().keys().toList()` |
| `size() == 7` / `== null` | `assertEquals(7L, …size())` (Long) / `assertNull(node.asLiveMap().size())` |
| `op.action == "MAP_SET"` | `assertEquals(ObjectOperationAction.MAP_SET, op.action)` (string tag → enum) |
| `op.mapSet.value.string` | `op.mapSet!!.value.string` (ObjectData getters) |
| `LiveMap.create(entries)` | `LiveMap.create(Map<String, LiveMapValue>)` (value type) |
| `LiveCounter.create(n)` | `LiveCounter.create(Number)` (value type) |
| raw value into `set` | `LiveMapValue.of(value)` |
| `subscribe(cb)` → `Subscription` | `subscribe(PathObjectListener / InstanceListener)` → `Subscription` |
| `{ depth: n }` | `PathObjectSubscriptionOptions(n)` |
| `event.object` / `event.message` | `event.getObject()` / `event.getMessage(): ObjectMessage?` |
| `object.on('synced', cb)` | `object.on(ObjectStateEvent.SYNCED, listener)` |
| type tag `'LiveMap'` etc. | `ValueType.LIVE_MAP` etc. |
| `PublicAPI::ObjectMessage` | `ObjectMessage` (getters) |
| `PublicAPI::ObjectOperation` | `ObjectOperation` (getters, one payload non-null) |
| `InternalLiveMap` / `InternalLiveCounter` / `ObjectsPool` | internal `:liveobjects` impl — see §13 |
