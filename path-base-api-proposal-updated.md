# LiveObjects Path-Based API — Updated Proposal for Java / Kotlin

> Status: **DRAFT v2 (lead-dev audit)** — supersedes the initial proposal in
> [PR #1190 — `liveobjects/PATH_BASED_API_JAVA_PYTHON.md`](https://github.com/ably/ably-java/pull/1190).
> This revision incorporates the review feedback recorded on that PR, with a
> particular focus on the comments from **@sacOO7**, and aligns the API with the
> stable JavaScript reference (`ably-js`) and the in-flight Swift/Kotlin/Python
> drafts captured in `LiveObjects-path-base-api.pdf`.
>
> **v2 changes:** corrected against the real source tree —
> `lib/src/main/java/io/ably/lib/objects/**` (Java 8 target), the Kotlin
> `liveobjects` module, the existing `ObjectsSubscription`, `ObjectsCallback`,
> `LiveMapValue`, `LiveMap`, `LiveCounter`, `LiveMapChange`, and
> `ObjectLifecycleChange` types. See §15 "Codebase Audit — what changed in
> v2" for the diff.

---

## Table of Contents

1. [Background and Context](#1-background-and-context)
2. [Problem Statement](#2-problem-statement)
3. [Requirements](#3-requirements)
4. [Goals and Non-Goals](#4-goals-and-non-goals)
5. [Summary of PR #1190 and Review Feedback](#5-summary-of-pr-1190-and-review-feedback)
6. [Key Decisions Derived From Review](#6-key-decisions-derived-from-review)
7. [Updated Java/Kotlin API Proposal](#7-updated-javakotlin-api-proposal)
8. [Cross-SDK Alignment (JS / Kotlin / Python)](#8-cross-sdk-alignment-js--kotlin--python)
9. [Usage Examples](#9-usage-examples)
10. [Ergonomics, Naming and Extensibility](#10-ergonomics-naming-and-extensibility)
11. [Open Questions, Trade-offs, Alternatives](#11-open-questions-trade-offs-alternatives)
12. [Implementation Plan in `ably-java`](#12-implementation-plan-in-ably-java)
13. [Migration and Backward Compatibility](#13-migration-and-backward-compatibility)
14. [Risks, Challenges and Testing Strategy](#14-risks-challenges-and-testing-strategy)
15. [Codebase Audit — what changed in v2](#15-codebase-audit--what-changed-in-v2)

---

## 1. Background and Context

Ably LiveObjects is a CRDT-based collaborative data feature. Each Ably channel
holds a single root object (always a `LiveMap`), under which arbitrary trees of
`LiveMap`, `LiveCounter` and primitive values may be composed. Mutations
performed by any client are replicated to all other subscribers via Ably's
real-time transport, with conflict resolution rules described in the
[Objects feature spec](https://sdk.ably.com/builds/ably/specification/main/objects-features/).

The **stable, reference public API** is the JavaScript SDK (`ably-js`), which
exposes a **path-based** programming model. Highlights of that model:

- A channel exposes a single root object via `channel.object.get()`.
- Navigation uses `myObject.get("key")` (single step) and chained calls for
  nested paths; some SDKs also expose `at("dotted.path")`.
- The navigated value is a `PathObject` — a *handle* to a location, not an
  object instance. Resolution happens at terminal calls (`.value()`,
  `.set(…)`, `.increment(…)`, `.instance()`, `.compact()`, etc.).
- Trees can be created atomically in one operation by passing nested
  `LiveMap.create(…)` / `LiveCounter.create(…)` / primitives to a single
  `set(…)` call.
- Subscriptions can be attached to any `PathObject` and configured with a
  `depth` parameter that controls how far down the tree updates propagate.

This proposal exists because the current Java SDK (`ably-java`) — under
`io.ably.lib.objects.RealtimeObjects` — exposes a **lower-level,
instance-centric API** (`getRoot()`, `createMap()`, `createCounter()`,
`LiveMap.get/set/remove`, etc.). That API matches the internal data structures
exactly but is awkward for users who think in *paths* and is increasingly out
of step with the reference JS API and the public Ably docs:

- <https://ably.com/docs/liveobjects/concepts/objects>
- <https://ably.com/docs/liveobjects/concepts/path-object>
- <https://ably.com/docs/liveobjects/concepts/operations>

PR #1190 (`docs: add path-based LiveObjects API proposal`) is the first attempt
to specify a path-based public surface for Java/Kotlin (and Python). The PR
remains open with substantive feedback that materially changes the proposed
shape; this document folds that feedback in.

---

## 2. Problem Statement

The current `RealtimeObjects` surface in `ably-java` is:

- **Verbose** for users navigating nested data (`getRoot().get("a").get("b")…`
  always requires `instanceof` checks because `LiveMapValue` is a union).
- **Inconsistent** with `ably-js`, the canonical SDK whose documentation Ably
  publishes externally.
- **Hard to compose**: nested structure creation requires explicit
  pre-creation of every `LiveMap` / `LiveCounter` instance, then wiring them
  with `set(…)` calls.
- **Missing path-level affordances**: there is no notion of subscribing to a
  *path* (independent of whether the object at that path is later replaced),
  no `compact()` / `compactJson()` snapshotting, no depth-bounded
  subscriptions.

We therefore need a path-based public API for `ably-java` that:

1. Matches the conceptual model already documented on ably.com.
2. Is idiomatic in both Java (interfaces with explicit `throws AblyException`,
   sync and async overloads) and Kotlin (`suspend` fns, nullable types,
   property-style accessors).
3. Coexists with the existing instance-level types (`LiveMap`,
   `LiveCounter`) which remain useful as the underlying *instance* API for
   advanced use cases (object IDs, instance-pinned subscriptions, etc.).

---

## 3. Requirements

**Functional**

- F1. A single, ordered way to obtain the root path object from a channel.
- F2. Path navigation by single key (`get(key)`) and by dotted path
  (`at("a.b.c")`).
- F3. Deferred resolution: building a path must not perform network or pool
  lookups; resolution happens on terminal calls.
- F4. Read terminals: primitive `value()`, counter `value()`, map
  enumeration (`entries`, `keys`, `values`, `size`), and `compact()` /
  `compactJson()` snapshots.
- F5. Write terminals: `set(key, value)`, `remove(key)`, `increment(n)`,
  `decrement(n)`.
- F6. Atomic deep create: a single `set` call may take a nested literal of
  maps/counters/primitives.
- F7. Subscriptions on a path with optional `depth` control and ability to
  unsubscribe.
- F8. Access to the underlying *instance* (`LiveMap`, `LiveCounter`) for
  cases where consumers need object identity (`id()`) or instance-pinned
  subscriptions.
- F9. Both blocking and non-blocking (callback / `suspend`) variants for
  Java and Kotlin respectively.

**Non-functional**

- N1. Backwards compatible with the existing
  `io.ably.lib.objects.RealtimeObjects` API for at least one major release.
- N2. Type-safety at the *instance* level (concrete types per instance);
  *dynamic* typing at the path level (the type at a path may change over
  time).
- N3. No premature feature creep — `LiveList` is **not** part of the current
  spec; do not expose it.
- N4. Consistent naming with `ably-js` and the Kotlin draft where reasonable.
- N5. Threadsafe (`RealtimeObjects` already requires this).

---

## 4. Goals and Non-Goals

**Goals**

- Make path-based navigation, mutation, snapshotting and subscription
  first-class in `ably-java`.
- Avoid leaking implementation details of the internal Objects pool through
  the public path API.
- Provide a clean Java surface and a Kotlin-friendly facade.
- Keep the door open for adding `LiveList` later without breaking changes.

**Non-Goals / Explicitly Out of Scope**

- Replacing the existing `LiveMap`/`LiveCounter` *instance* interfaces
  (they remain — they are the spec-mandated internal types and are useful
  for advanced flows).
- Specifying server-side behaviour or wire protocol — this proposal is
  purely about the public client API.
- Adding `LiveList` or any other collection type that is not yet in the
  Objects spec (per @sacOO7's review comment that `LiveListPathObject`
  doesn't exist in the current spec).
- **`batch(...)` operations** (e.g. JS's `obj.batch(ctx => …)` that groups
  multiple mutations into one atomic operation). They are intentionally
  excluded from this iteration of the path-based API. If/when they are
  introduced, they will be a purely additive method on `PathObject` and
  will not change any of the decisions in §6.
- **REST API surface** (the `AblyRest` / `io.ably.lib.rest` operations such
  as `channel.objects.set(...)` for server-side / one-shot mutations from a
  REST client). The path-based API specified here is the **realtime**
  client surface only. Bringing the REST API into the path-based world is
  tracked as separate, future work and is not addressed by this document.

---

## 5. Summary of PR #1190 and Review Feedback

### 5.1 What PR #1190 proposed

A 943-line spec
(`liveobjects/PATH_BASED_API_JAVA_PYTHON.md`) introducing:

- A `PathObject` root interface with typed *cast* methods
  (`asStringPrimitive`, `asNumberPrimitive`, `asLiveMap`, `asLiveCounter`,
  `asLiveList`, …) and a generic `subscribe`.
- Specialised sub-interfaces: `LiveMapPathObject`, `LiveCounterPathObject`,
  `LiveListPathObject`, `StringPathObject`, `NumberPathObject`,
  `BooleanPathObject`, `BinaryPathObject`.
- `LiveMap.create(...)` / `LiveCounter.create(...)` / `Primitive.create(...)`
  static factories for atomic deep creation.
- A `MessageOptions` parameter on every mutating call.
- `channel.getObject().get()` to fetch the root.
- A `compact()` returning `JsonValue` for snapshotting.

### 5.2 What the reviewers said

The PR received feedback from **@sacOO7** (19 comments — the bulk of the
substantive review), **@lawrence-forooghian** (5 comments), and the GitHub
Copilot/CodeRabbit bots (correctness nits). The key threads, grouped:

#### A. The big conceptual question — typing of `PathObject`

> *"`PathObject` as a concept is making sure we handle dynamic instances at a
> given path; feels odd when we apply concrete typing here. Maybe it would
> make sense to have type-specific instances, i.e. `StringInstance`,
> `LiveMapInstance` etc."* — @sacOO7

> *"Types for a given `instance` remain the same throughout the lifetime; in
> case of `PathObject`, the type at a given path can change."* — @sacOO7

> *"Not sure if this should be `LiveMapPathObject`. Puts a developer in a
> limbo when someone tries to `set` a key/value on `foo` but the code throws
> because `foo` isn't a `LiveMap` or is of a different type. Since
> `PathObject` itself represents dynamic types at runtime, defining types for
> it will be a contradiction at multiple levels."* — @sacOO7

> *"This [explicit `asLiveMap()` cast] defeats the purpose of
> reading/writing dynamic types at given paths. Static types don't ensure we
> are updating or reading the right values."* — @sacOO7

**Take-away:** `PathObject` is a dynamic handle. Specialising it
(`LiveMapPathObject`, `StringPathObject`) is conceptually wrong because the
type at a path can change. Strong typing belongs on **instances**.

#### B. Naming of the root accessor

> *"We previously had `channel.getObjects().getRoot()`. With the new public
> API change where `getObjects` is now `getObject` and `getRoot` is `get`, it
> would make sense to update the Java API to read more sensibly as
> `channel.object().get()`. In Kotlin, `channel.realtimeObject.get()` or
> `channel.Object.get()` since uppercase `Object` is not a reserved keyword
> in Kotlin."* — @sacOO7

(Bot/Copilot also flagged the same inconsistency.)

#### C. Internal vs. public type names

> *"As per spec, `LiveMap`/`LiveCounter` is now an internal type. This is
> also true for `ably-js`, but `ably-js` exports `LiveCounterValueType`
> (→ `LiveCounter`) and `LiveMapValueType` (→ `LiveMap`). Need to keep
> naming consistent across SDKs. Public docs still expose `LiveCounter` /
> `LiveMap` (see [path-object#typing](https://ably.com/docs/liveobjects/concepts/path-object#typing))."*
> — @sacOO7

#### D. `compact()` is hard in a strongly-typed language

> *"I don't think we can represent `compact` for `ably-java`. In `ably-js`
> it returns an anonymous JS object without concrete typing. In Java/Swift it
> would be impossible to handle anonymous typing as `compact` / `compactJson`
> do. … Maybe it's possible to return `Map<String, Object>` where the value
> can be `Primitive`, `LiveMap` or `LiveCounter`. We'd need a superclass for
> them — tricky but doable."* — @sacOO7

@lawrence-forooghian also noted the JS API has since been split into
`compact()` and `compactJson()` (one preserves cycles + binary types, the
other is JSON-safe).

#### E. `Primitive` is missing from the spec

> *"We currently don't have `Primitive` as an explicit type anywhere in the
> spec. We probably need to define it so we can publicly expose and use it
> consistently across SDKs."* — @sacOO7

> *"Need explicit `Primitive` type in the spec, maybe should be called
> `LivePrimitive`."* — @sacOO7

#### F. The root is special

> *"Root object will always be a `LiveMap`; the type of the root will never
> change. But `PathObject`s at child locations — their types can always
> change dynamically."* — @sacOO7

> *"Maybe this should be called `RootPathObject` instead [of
> `LiveMapPathObject`]."* — @sacOO7

> *"It's not frequent that types can change dynamically for children, but
> still need to consider cases where we store the type in a variable and then
> try performing some action on it when it could possibly change internally."*
> — @sacOO7

#### G. `LiveListPathObject` should not exist

> *"`LiveListPathObject` doesn't exist."* — @sacOO7 (the Objects spec does
> not include a list type).

#### H. Examples internally inconsistent

> *"`visits.value()` can return `string` or `livemap` based on the
> underlying changed type."* — @sacOO7

> *"This mutation example navigates to `\"user.name\"` (a primitive path)
> and then casts to `LiveMap` before calling `set(…)`, which is internally
> inconsistent."* — Copilot

Several examples in PR #1190 mis-navigate (treat a primitive as a map,
double-`set` a "state" key, etc.).

#### I. `MessageOptions` mismatch

@lawrence-forooghian and Copilot both noted that the `options` parameter
proposed in the Java signatures does **not** exist in the JS API, and that
the Java examples themselves omit it on most calls. Either drop it or
provide overloads.

#### J. Failure mode on `value()` / `instance()`

@lawrence-forooghian asked for a clear specification of when these throw vs
return `null`:

- Nothing at the path → `null`?
- Type mismatch (e.g. asking `asStringPrimitive().value()` on a map) →
  `throws`?

---

## 6. Key Decisions Derived From Review

Based on the feedback above, the updated proposal adopts the following
decisions:

| # | Decision | Driven by |
|---|---|---|
| D1 | **`PathObject` is a single, untyped handle.** No `LiveMapPathObject`, `LiveCounterPathObject`, `StringPathObject`, etc. The set of terminal operations available on a `PathObject` is uniform; type assertions happen only when reading/writing typed values. | @sacOO7 A, H |
| D2 | **Strong typing lives on `Instance`s.** Introduce `LiveMapInstance`, `LiveCounterInstance`, with a sealed base `LiveInstance`. These are obtained via `pathObject.instance()` (returns `LiveInstance?` / `Optional<LiveInstance>`) or via the typed helpers `asLiveMap()` / `asLiveCounter()` (which throw if the runtime type doesn't match). | @sacOO7 A, F |
| D3 | **The root is a `RootPathObject`** — a marker subtype of `PathObject` whose `instance()` is statically known to be `LiveMapInstance`. This is the only place where compile-time typing is encoded in a `PathObject`. | @sacOO7 F |
| D4 | **Channel accessor:** `channel.object().get()` (Java) / `channel.realtimeObject.get()` (Kotlin extension). The legacy `channel.getObjects().getRoot()` continues to work, marked `@Deprecated`. | @sacOO7 B |
| D5 | **`LivePrimitive`** is introduced as the public, sealed wrapper for primitive scalars (`String`, `Number`, `Boolean`, `byte[]`) — replacing the implicit "primitive" notion in PR #1190. The internal `LiveMapValue` continues to exist as the wire-level union. | @sacOO7 E |
| D6 | **`LiveMap` / `LiveCounter` are internal**; the public path-API names are `LiveMapInstance` / `LiveCounterInstance`. Internally they may extend the existing `LiveMap` / `LiveCounter` interfaces. | @sacOO7 C |
| D7 | **No `LiveListPathObject`** — list support is out of scope until the spec adds it. | @sacOO7 G |
| D8 | **`compact()` returns `Map<String, LiveValue>`**, where `LiveValue` is the sealed superclass of `LivePrimitive`, `LiveMapInstance`, `LiveCounterInstance`. A separate `compactJson()` returns a `JsonElement` suitable for `Gson` serialisation (no cycles, binary as base64). | @sacOO7 D, @lawrence-forooghian |
| D9 | **No `MessageOptions` on path mutations** — JS doesn't have it. If we ever need per-message metadata, it can be added later as overloads without changing the canonical form. | Copilot, @lawrence-forooghian I |
| D10 | **Failure semantics:** `value()` returns `null` when the path is unresolved (no IO; non-blocking). `asLiveMap()` / `asLiveCounter()` throw `AblyException` (well-defined error code) if the type at the path is not the expected one (also non-blocking — just a type check). **Blocking write operations** (`set` / `remove` / `increment` / `decrement`) on an unresolved path throw `AblyException` with `code=92000` (object not found). | @lawrence-forooghian J |
| D11 | **Reads are non-blocking, writes are `@Blocking`** — matches `LiveMap` / `LiveCounter` / `RealtimeObjects` conventions in the codebase. No `*Async` variant for reads (they're already synchronous off the in-memory pool); `*Async` provided only for the four mutating methods. | Lead-dev audit (matches `LiveMap.java` / `LiveCounter.java`) |

---

## 7. Updated Java/Kotlin API Proposal

> **Build-target constraint (verified from source):** the public Java API
> lives under `lib/src/main/java/io/ably/lib/...`, compiled by the `:java`
> Gradle module which sets
> `sourceCompatibility = JavaVersion.VERSION_1_8` and
> `targetCompatibility = JavaVersion.VERSION_1_8` (see
> `java/build.gradle.kts:11-14`). Therefore the **public Java API must be
> Java 8 source-compatible**:
>
> - No `sealed interface` / `sealed class` (JDK 17+).
> - No `switch` pattern matching (JDK 21+).
> - No `record` (JDK 14+).
> - `Optional`, `List.of` are OK; `Map.of` is OK (it was stabilised in 9 but
>   we already see `Map.of` used in the proposal — verify when writing
>   examples; if Java 8 only, use `Collections.unmodifiableMap(new HashMap<>(){…})`).
>
> The Kotlin facade in the `:liveobjects` module is unconstrained (the
> module uses Kotlin with `explicitApi()` and already targets a modern JVM).
> Sealed *concepts* are therefore realised in two ways:
>
> 1. **In Java**: a plain `interface` (e.g. `LiveValue`) marked
>    `@org.jetbrains.annotations.ApiStatus.NonExtendable` and documented as
>    "logically sealed; only the implementations bundled with the SDK are
>    valid". Implementations live in a package whose impls are
>    package-private or `final` to keep the surface tight.
> 2. **In Kotlin (`liveobjects` module)**: real `sealed interface`s in
>    `internal` impls — they can be unsealed in the public API because they
>    aren't on the Java 8 surface.

### 7.1 Package layout

```
lib/src/main/java/io/ably/lib/objects/path/        — new public Java API (Java 8 compatible)
├── PathObject.java                       — interface, the dynamic handle
├── RootPathObject.java                   — PathObject; instance() returns LiveMapInstance
├── PathChangeEvent.java                  — payload delivered to subscriptions
├── PathChangeListener.java               — @FunctionalInterface
├── PathSubscriptionOptions.java          — depth=unlimited|N (factory methods)
├── LiveValue.java                        — root marker (logically sealed)
├── LiveInstance.java                     — extends LiveValue; id(), subscribe()
├── LiveMapInstance.java                  — extends LiveInstance + io.ably.lib.objects.type.map.LiveMap
├── LiveCounterInstance.java              — extends LiveInstance + io.ably.lib.objects.type.counter.LiveCounter
├── LivePrimitive.java                    — extends LiveValue; isString()/asString()/...; static of(...)
├── LiveMap.java                          — public factory: create(Map<String, LiveValue>) → LiveValue (a MapCreate token)
├── LiveCounter.java                      — public factory: create(Number) → LiveValue (a CounterCreate token)
├── ChannelObject.java                    — exposed via Channel#object()
└── ObjectMessage.java                    — public view of the wire message (clientId, operation, extras, timestamp)

liveobjects/src/main/kotlin/io/ably/lib/objects/path/   — internal impls (Kotlin)
├── DefaultPathObject.kt
├── DefaultRootPathObject.kt
├── DefaultChannelObject.kt
├── PathResolver.kt
├── Compactor.kt
├── MapCreate.kt          (internal data class, implements LiveValue)
├── CounterCreate.kt      (internal data class, implements LiveValue)
└── ext/
    ├── ChannelExtensions.kt   — val Channel.realtimeObject, suspend fun ChannelObject.getRoot()
    └── PathObjectExtensions.kt — operator get, suspend wrappers, Flow events, liveMapOf/liveCounterOf DSL
```

**Reused existing types** (no duplication):

| Existing type | Path-API use |
|---|---|
| `io.ably.lib.objects.ObjectsCallback<T>` | Async callback for `*Async` methods |
| `io.ably.lib.objects.ObjectsSubscription` | Returned by `PathObject#subscribe(...)` — no new `PathSubscription` |
| `io.ably.lib.objects.type.map.LiveMap` | Extended by `LiveMapInstance` |
| `io.ably.lib.objects.type.counter.LiveCounter` | Extended by `LiveCounterInstance` |
| `io.ably.lib.objects.type.map.LiveMapValue` | Bridged to `LiveValue` via `LiveValue.fromMapValue(LiveMapValue)` / `toMapValue()` |
| `io.ably.lib.objects.LiveObjectsPlugin` | New `getChannelObject(channelName)` method added |
| `io.ably.lib.realtime.Channel` (extends `ChannelBase`) | New `object()` instance method on `ChannelBase` |

### 7.2 `PathObject` (Java, Java-8 compatible)

> **Blocking vs non-blocking conventions (verified from the existing
> codebase):**
>
> | Class | Method | Annotation in source | Why |
> |---|---|---|---|
> | `LiveMap` | `get`, `entries`, `keys`, `values` | *(none)* | Reads from the in-memory `ObjectsPool` — no IO |
> | `LiveMap` | `size` | `@Contract(pure = true)` | Pure pool read |
> | `LiveMap` | `set`, `remove` | `@Blocking` | Publishes op and waits for ACK |
> | `LiveMap` | `setAsync`, `removeAsync` | `@NonBlocking` | Callback variants |
> | `LiveCounter` | `value` | `@Contract(pure = true)` | Pure pool read |
> | `LiveCounter` | `increment`, `decrement` | `@Blocking` | Publish + ACK |
> | `LiveCounter` | `incrementAsync`, `decrementAsync` | `@NonBlocking` | Callback variants |
> | `RealtimeObjects` | `getRoot` | `@Blocking` | Waits for initial sync |
> | `RealtimeObjects` | `createMap`, `createCounter` | `@Blocking` | Publish + ACK |
> | `LiveMapChange` | `subscribe` | `@NonBlocking` | Registry insert only |
>
> The path-based API follows the **same convention**: all reads
> (`value`, `instance`, `compact`, `compactJson`, `as*`, `entries`,
> `keys`, `values`, `size`) are non-blocking — they go through
> `PathResolver` against the in-memory `ObjectsPool`. Only writes
> (`set` / `remove` / `increment` / `decrement`) and the initial root
> fetch (`ChannelObject#get`) carry `@Blocking`. **No `*Async` variant
> is exposed for reads** — they already return synchronously off the
> pool, mirroring `LiveMap#get` / `LiveCounter#value`.

```java
package io.ably.lib.objects.path;

import io.ably.lib.types.AblyException;
import io.ably.lib.objects.ObjectsCallback;
import io.ably.lib.objects.ObjectsSubscription;     // reused
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.google.gson.JsonElement;

import java.util.Map;

/**
 * A handle to a location within the LiveObjects tree. PathObject is
 * deliberately untyped: the type of the value present at this path may
 * change over the lifetime of the channel as keys are set/removed. Terminal
 * methods (value, set, remove, increment, instance, compact, …) resolve the
 * path at call time and may throw if the resolved type does not match.
 *
 * <p>Reads ({@code value}, {@code instance}, {@code compact},
 * {@code compactJson}, {@code as*}, {@code entries}, {@code keys},
 * {@code values}, {@code size}) are non-blocking — they resolve against
 * the in-memory objects pool. Writes ({@code set}, {@code remove},
 * {@code increment}, {@code decrement}) are {@link Blocking} — they
 * publish an operation to Ably and wait for the ACK; use the
 * {@code *Async} variants to avoid blocking the calling thread.
 */
@ApiStatus.NonExtendable
public interface PathObject {

    /** The dotted path from the channel root, e.g. {@code "game.players.alice"}. */
    @NotNull String path();

    /** Navigate one key into a map at this path. */
    @NotNull PathObject get(@NotNull String key);

    /** Navigate by dotted path; convenience for {@code get("a").get("b")…}. */
    @NotNull PathObject at(@NotNull String dottedPath);

    // ---- Reads (non-blocking — pool lookups) -------------------------------

    /**
     * Resolves the path against the local objects pool and returns the value
     * at it. Non-blocking; never performs IO.
     *
     * <ul>
     *   <li>{@code null} if the path does not resolve to anything.</li>
     *   <li>A {@link LivePrimitive} for primitives (String / Number / Boolean / byte[] / JsonArray / JsonObject).</li>
     *   <li>A {@link LiveCounterInstance} or {@link LiveMapInstance} for live objects.</li>
     * </ul>
     */
    @Nullable @Contract(pure = true) LiveValue value();

    /**
     * Resolves the path and returns the live-object instance at it, or
     * {@code null} if the path is unresolved or resolves to a primitive.
     * Non-blocking.
     */
    @Nullable @Contract(pure = true) LiveInstance instance();

    /**
     * Snapshots the subtree rooted at this path into a Java map of nested
     * {@link LiveValue}s. The returned structure references the live-object
     * instances themselves, so cycles are preserved. Non-blocking.
     */
    @NotNull @Contract(pure = true) Map<String, LiveValue> compact();

    /**
     * Snapshots the subtree rooted at this path into a JSON-safe Gson
     * {@link JsonElement}. Cycles are broken via {@code {"objectId":"…"}}
     * placeholders; binary values are base64-encoded. Non-blocking.
     */
    @NotNull @Contract(pure = true) JsonElement compactJson();

    // ---- Typed accessors (assertion, not specialised PathObject types) -----
    // Non-blocking — they only check the type of the already-resolved value.

    /**
     * Asserts that the value at this path is a LiveMap and returns its
     * instance. Throws {@link AblyException} if the path is unresolved or
     * the resolved value is of a different type.
     *
     * <p><b>Note:</b> this returns a {@code LiveMapInstance}, NOT a
     * specialised path object. The path itself remains dynamic; if you
     * need to keep navigating in a way that survives type changes, use the
     * underlying {@link PathObject}.
     */
    @NotNull LiveMapInstance asLiveMap() throws AblyException;

    @NotNull LiveCounterInstance asLiveCounter() throws AblyException;

    @NotNull LivePrimitive asPrimitive() throws AblyException;

    // ---- Map-like enumeration (non-blocking; throws/null if not a map) -----

    @NotNull Iterable<Map.Entry<String, PathObject>> entries() throws AblyException;
    @NotNull Iterable<String> keys() throws AblyException;
    @NotNull Iterable<PathObject> values() throws AblyException;

    /** Size of the LiveMap at this path; {@code null} if the path is not a map. */
    @Nullable @Contract(pure = true) Long size();

    // ---- Writes (Blocking — publish + ACK) ---------------------------------

    /** If this path resolves to a LiveMap, set {@code key} to {@code value}. */
    @Blocking void set(@NotNull String key, @NotNull LiveValue value) throws AblyException;

    /** If this path resolves to a LiveMap, remove {@code key}. */
    @Blocking void remove(@NotNull String key) throws AblyException;

    /** If this path resolves to a LiveCounter, increment by {@code amount}. */
    @Blocking void increment(@NotNull Number amount) throws AblyException;

    /** If this path resolves to a LiveCounter, decrement by {@code amount}. */
    @Blocking void decrement(@NotNull Number amount) throws AblyException;

    // ---- Subscriptions (reuse existing ObjectsSubscription) ----------------
    // @NonBlocking — matches LiveMapChange.subscribe / LiveCounterChange.subscribe.

    @NonBlocking @NotNull ObjectsSubscription subscribe(@NotNull PathChangeListener listener);
    @NonBlocking @NotNull ObjectsSubscription subscribe(@NotNull PathChangeListener listener,
                                                        @NotNull PathSubscriptionOptions options);

    // ---- Async variants (only for the Blocking writes) ---------------------
    // No async variants for reads — reads are already non-blocking.

    @NonBlocking void setAsync(@NotNull String key, @NotNull LiveValue value,
                               @NotNull ObjectsCallback<Void> cb);
    @NonBlocking void removeAsync(@NotNull String key, @NotNull ObjectsCallback<Void> cb);
    @NonBlocking void incrementAsync(@NotNull Number amount, @NotNull ObjectsCallback<Void> cb);
    @NonBlocking void decrementAsync(@NotNull Number amount, @NotNull ObjectsCallback<Void> cb);
}
```

> **Why reuse `ObjectsSubscription` (existing) and not introduce
> `PathSubscription`?** The existing `io.ably.lib.objects.ObjectsSubscription`
> has exactly the contract we need (`unsubscribe()`, spec RTLO4b5/RTLO4b5a)
> and is already used by `LiveMap#subscribe`, `LiveCounter#subscribe`,
> `ObjectsStateChange#on`, etc. Adding a parallel `PathSubscription` type
> with the same shape would just bloat the public surface.
>
> **Why no `valueAsync` / `compactAsync` / etc.?** Because the
> corresponding sync methods are already non-blocking — they only touch
> the in-memory pool. This is the same reason `LiveMap#get` and
> `LiveCounter#value` have no async variant in today's API.

### 7.3 `RootPathObject`

```java
/**
 * Returned from {@link Channel#object()}{@code .get()}. The root of a
 * channel's LiveObjects tree is *always* a LiveMap, so a single statically
 * typed entry point is provided. {@code instance()} is a pure read against
 * the in-memory pool — non-blocking.
 */
public interface RootPathObject extends PathObject {

    /** Statically known to be a LiveMapInstance at the root. Non-blocking. */
    @Override
    @NotNull @Contract(pure = true) LiveMapInstance instance();
}
```

### 7.4 Logically-sealed value hierarchy (Java 8 compatible)

In Java we cannot use `sealed interface` (JDK 17+). Instead, we declare the
hierarchy as plain interfaces with `@ApiStatus.NonExtendable` and place
**all valid implementations in this package as `final` classes with
package-private constructors**, giving the same "logical seal" without the
language feature:

```java
/** Marker for everything that can sit at a path. Logically sealed. */
@ApiStatus.NonExtendable
public interface LiveValue {
    /**
     * Convenience bridge to the spec-aligned {@link io.ably.lib.objects.type.map.LiveMapValue}
     * union used by the underlying LiveMap. Returns the corresponding
     * LiveMapValue; never null.
     */
    @NotNull io.ably.lib.objects.type.map.LiveMapValue toMapValue();

    /** Inverse bridge used by reads. */
    @NotNull static LiveValue fromMapValue(@NotNull io.ably.lib.objects.type.map.LiveMapValue v) {
        return LiveValues.from(v);   // package-private factory
    }
}

/** Marker for live (collaborative) instances. Logically sealed. */
@ApiStatus.NonExtendable
public interface LiveInstance extends LiveValue {

    /** Object ID, e.g. {@code "counter:abc@1734628392000"}. */
    @NotNull String id();

    /**
     * Instance-pinned subscription — survives even if this instance is
     * moved/replaced at its old path.
     */
    @NotNull ObjectsSubscription subscribe(@NotNull PathChangeListener listener);
}

/**
 * Public path-API name for a LiveMap instance. Extends the existing
 * internal {@code LiveMap} so existing consumers can use it interchangeably.
 */
@ApiStatus.NonExtendable
public interface LiveMapInstance
        extends LiveInstance, io.ably.lib.objects.type.map.LiveMap {}

@ApiStatus.NonExtendable
public interface LiveCounterInstance
        extends LiveInstance, io.ably.lib.objects.type.counter.LiveCounter {}
```

> *Note:* `LiveMapInstance` extends both `LiveInstance` and the existing
> `io.ably.lib.objects.type.map.LiveMap`. Diamond resolution is fine here
> because both ancestors declare disjoint method sets — `LiveInstance.id()` /
> `subscribe(PathChangeListener)` vs. `LiveMap.get/set/remove/size/...`.

### 7.5 `LivePrimitive` (Java 8 compatible)

`LivePrimitive` mirrors the **existing** `LiveMapValue` union
(`io.ably.lib.objects.type.map.LiveMapValue` — see source for full list:
Boolean, Binary, Number, String, JsonArray, JsonObject, LiveCounter,
LiveMap). For the path API we want a **leaf-only** view: drop LiveCounter
and LiveMap (those become `LiveInstance`) and keep the rest as primitives.

```java
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Wrapper for primitive leaf values. Logically sealed. */
@ApiStatus.NonExtendable
public interface LivePrimitive extends LiveValue {

    @NotNull static LivePrimitive of(@NotNull String value)     { return LiveValues.primitive(value); }
    @NotNull static LivePrimitive of(@NotNull Number value)     { return LiveValues.primitive(value); }
    @NotNull static LivePrimitive of(boolean value)             { return LiveValues.primitive(value); }
    @NotNull static LivePrimitive of(byte @NotNull [] value)    { return LiveValues.primitive(value); }
    @NotNull static LivePrimitive of(@NotNull JsonArray value)  { return LiveValues.primitive(value); }
    @NotNull static LivePrimitive of(@NotNull JsonObject value) { return LiveValues.primitive(value); }

    /** Raw value boxed as Object — matches {@link LiveMapValue#getValue()}. */
    @NotNull Object raw();

    // Type checks — same shape as LiveMapValue.isXxx() for familiarity.
    boolean isString();
    boolean isNumber();
    boolean isBoolean();
    boolean isBinary();
    boolean isJsonArray();
    boolean isJsonObject();

    // Typed accessors — throw IllegalStateException for type mismatch
    // (consistent with LiveMapValue.getAsXxx()).
    @NotNull String     asString();
    @NotNull Number     asNumber();
    boolean             asBoolean();
    byte @NotNull []    asBinary();
    @NotNull JsonArray  asJsonArray();
    @NotNull JsonObject asJsonObject();
}
```

> **Why this lines up with `LiveMapValue` deliberately:** the existing
> `LiveMapValue` is already the wire-level union (spec RTO11a1 —
> `Boolean | Binary | Number | String | JsonArray | JsonObject | LiveCounter | LiveMap`).
> `LivePrimitive` is just `LiveMapValue` minus the live-object variants,
> rebranded for the path API. Internally a single helper converts between
> the two so we don't keep two parallel hierarchies in sync by hand.

### 7.6 Atomic deep-create factories

`LiveMap.create(...)` / `LiveCounter.create(...)` build **uncommitted creation
tokens** (`MapCreate` / `CounterCreate`) which implement `LiveValue` and may
be passed straight into `PathObject#set`. They never reserve resources or
talk to the wire by themselves; resource creation only happens on the
enclosing `set` operation.

```java
public final class LiveMap {
    public static LiveValue create() {
        return new MapCreate(Map.of());
    }
    public static LiveValue create(@NotNull Map<String, LiveValue> entries) {
        return new MapCreate(entries);
    }
    private LiveMap() {}
}

public final class LiveCounter {
    public static LiveValue create() {
        return new CounterCreate(0);
    }
    public static LiveValue create(@NotNull Number initialValue) {
        return new CounterCreate(initialValue);
    }
    private LiveCounter() {}
}
```

> *Note on naming:* keeping the public **factory** names `LiveMap` /
> `LiveCounter` matches `ably-js` and the Ably docs. The **types they
> produce** (`LiveMapInstance`, `LiveCounterInstance`) are distinct from the
> internal `LiveMap` / `LiveCounter` interfaces in
> `io.ably.lib.objects.type.*` — see D6.

### 7.7 Subscriptions

`ObjectsSubscription` is reused (see §7.2 note). The remaining new types are
the **event payload**, the **listener** and the **options**.

```java
public final class PathSubscriptionOptions {
    private final int depth;
    private PathSubscriptionOptions(int depth) { this.depth = depth; }
    public int depth() { return depth; }  // -1 means unlimited
    public static PathSubscriptionOptions unlimited()      { return new PathSubscriptionOptions(-1); }
    public static PathSubscriptionOptions depth(int depth) { return new PathSubscriptionOptions(depth); }
}

public interface PathChangeEvent {
    /** The PathObject for the location that changed. */
    @NotNull PathObject object();

    /**
     * The wire message that caused the change. {@link ObjectMessage} is a
     * new public type — see §7.8.
     */
    @NotNull ObjectMessage message();
}

@FunctionalInterface
public interface PathChangeListener {
    void onChange(@NotNull PathChangeEvent event);
}
```

> **Note on `PathChangeEvent.message()`:** the existing internal
> `io.ably.lib.objects.ObjectMessage` (Kotlin class in the `liveobjects`
> module) is **not** part of the public Java API. We therefore need a
> small new public Java type `io.ably.lib.objects.path.ObjectMessage`
> exposing at minimum: `clientId()`, `operation()` (action + counterInc /
> mapSet / mapRemove / objectCreate payloads), `extras()`, `timestamp()`,
> `serial()`. The internal Kotlin class is adapted to this view at the
> path-API boundary. Spec ref: `objects-features` Operation messages.

### 7.8 New public `ObjectMessage` type

```java
package io.ably.lib.objects.path;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Read-only view of an Objects operation message as seen by a subscriber. */
@ApiStatus.NonExtendable
public interface ObjectMessage {
    @Nullable String  clientId();
    @Nullable String  connectionId();
    /** Server-assigned operation serial (for ordering / dedup). */
    @Nullable String  serial();
    /** Action enum: e.g. MAP_SET, MAP_REMOVE, COUNTER_INC, OBJECT_DELETE… */
    @NotNull  Operation operation();
    @Nullable Long    timestamp();   // millis since epoch
    @Nullable JsonObject extras();

    interface Operation {
        @NotNull Action action();
        @Nullable String mapSetKey();
        @Nullable LiveValue mapSetValue();
        @Nullable String mapRemoveKey();
        @Nullable Number counterIncNumber();
        @Nullable String objectId();
    }

    enum Action { MAP_SET, MAP_REMOVE, MAP_CREATE,
                  COUNTER_INC, COUNTER_CREATE,
                  OBJECT_DELETE }
}
```

### 7.9 Channel accessor

```java
package io.ably.lib.objects.path;

import io.ably.lib.objects.ObjectsCallback;
import io.ably.lib.types.AblyException;

public interface ChannelObject {
    /** Blocking: waits for sync; returns the root path object. */
    @org.jetbrains.annotations.Blocking
    @org.jetbrains.annotations.NotNull
    RootPathObject get() throws AblyException;

    /** Non-blocking variant — completion via {@link ObjectsCallback}. */
    @org.jetbrains.annotations.NonBlocking
    void getAsync(@org.jetbrains.annotations.NotNull ObjectsCallback<RootPathObject> callback);
}
```

In `ChannelBase` we add a new method **alongside** the existing
`getObjects()`:

```java
// io.ably.lib.realtime.ChannelBase

/**
 * Returns the path-based LiveObjects accessor for this channel.
 *
 * @since 1.X (the release that introduces the path-based API)
 */
public ChannelObject object() throws AblyException {
    if (liveObjectsPlugin == null) {
        throw AblyException.fromErrorInfo(new ErrorInfo(
            "LiveObjects plugin hasn't been installed, " +
            "add runtimeOnly('io.ably:liveobjects:<ably-version>') to your dependency tree",
            400, 40019));
    }
    return liveObjectsPlugin.getChannelObject(name);
}

/** @deprecated since 1.X. Use {@link #object()} instead. */
@Deprecated
public RealtimeObjects getObjects() throws AblyException { /* unchanged */ }
```

> *Why no `forRemoval = true`:* the `@Deprecated(forRemoval = …)` element
> was added in Java 9. The public surface is Java 8. Plain `@Deprecated`
> with the Javadoc `@deprecated` tag is the portable form. Removal is
> tracked separately in a release note.

The `LiveObjectsPlugin` interface gains a single new method:

```java
// io.ably.lib.objects.LiveObjectsPlugin

/** Returns the path-based accessor for the given channel. */
@org.jetbrains.annotations.NotNull
ChannelObject getChannelObject(@org.jetbrains.annotations.NotNull String channelName);
```

Both implementations live in the Kotlin `liveobjects` module
(`DefaultLiveObjectsPlugin.kt`); the existing `getInstance(channelName)`
continues to return the deprecated `RealtimeObjects`.

### 7.10 Kotlin facade

The public realtime channel class in `ably-java` is
**`io.ably.lib.realtime.Channel`** (`java/src/main/java/io/ably/lib/realtime/Channel.java`)
— it extends `ChannelBase`. There is no class called `RealtimeChannel` in
this repo, so all Kotlin extensions hang off `Channel` (or `ChannelBase`
for inheritance-friendly extensions).

```kotlin
// In: liveobjects/src/main/kotlin/io/ably/lib/objects/path/ext

import io.ably.lib.realtime.Channel
import io.ably.lib.objects.path.*

/** Property-style access, like Kotlin's getter/setter for `object`. */
val Channel.realtimeObject: ChannelObject
    get() = this.`object`()           // backticks required only in Kotlin for the Java method named `object`

suspend fun ChannelObject.getRoot(): RootPathObject =
    suspendCancellableCoroutine { cont ->
        getAsync(object : ObjectsCallback<RootPathObject> {
            override fun onSuccess(result: RootPathObject) = cont.resume(result)
            override fun onError(e: AblyException)         = cont.resumeWithException(e)
        })
    }

// No suspend variant for reads — `value()` is already non-blocking and
// returns synchronously off the in-memory pool. This mirrors the existing
// `LiveMap#get` / `LiveCounter#value` shape.

// suspend variants for the blocking write ops (set/remove/increment/decrement):
suspend fun PathObject.setSuspending(key: String, value: LiveValue): Unit =
    suspendCancellableCoroutine { cont ->
        setAsync(key, value, object : ObjectsCallback<Void> {
            override fun onSuccess(result: Void?)  = cont.resume(Unit)
            override fun onError(e: AblyException) = cont.resumeWithException(e)
        })
    }

// idiomatic Kotlin DSL for atomic deep create
fun liveMapOf(vararg pairs: Pair<String, Any?>): LiveValue =
    LiveMap.create(pairs.associate { (k, v) -> k to v.toLiveValue() })

fun liveCounterOf(initial: Number = 0): LiveValue = LiveCounter.create(initial)
```

Reads in Kotlin look like:

```kotlin
val root: RootPathObject = channel.realtimeObject.getRoot()
val score: Double? = (root.at("game.players.alice.score").value() as? LiveCounterInstance)?.value()
```

For the common typed read, the SDK ships extension functions:

```kotlin
fun PathObject.counterValue(): Double? =
    (value() as? LiveCounterInstance)?.value()

fun PathObject.stringValue(): String? =
    (value() as? LivePrimitive)?.takeIf { it.isString }?.asString()
```

The Kotlin module is free to use real `sealed interface`s for its internal
implementation hierarchy (`MapCreate`, `CounterCreate`, the concrete
`LivePrimitive` impls), because they sit behind the Java 8 surface
boundary.

---

## 8. Cross-SDK Alignment (JS / Kotlin / Python)

| Concept                           | `ably-js` (stable)              | This proposal (Java)                | Kotlin (sugar)                          | PDF/Python draft                  |
|-----------------------------------|---------------------------------|-------------------------------------|-----------------------------------------|-----------------------------------|
| Root accessor                     | `channel.object.get()`          | `channel.object().get()`            | `channel.realtimeObject.getRoot()`      | `channel.object.get()`            |
| Navigate single                   | `obj.get("a")`                  | `obj.get("a")`                      | `obj["a"]` (extension)                  | `obj.get("a")`                    |
| Navigate dotted                   | `obj.get("a").get("b")`         | `obj.at("a.b")` / `obj.get("a").get("b")` | `obj.at("a.b")`                   | `obj.get("a").get("b")`           |
| Read scalar                       | `obj.get("k").value()`          | `obj.get("k").value()` → `LiveValue?` | `obj.get("k").stringValue()`           | `obj.get("k").value()`            |
| Read counter                      | `obj.get("v").value()`          | `obj.get("v").asLiveCounter().value()` | as above                              | `obj.get("v").value()`            |
| Mutate map                        | `obj.set("k", v)`               | `obj.set("k", LivePrimitive.of(v))` | `obj.set("k", v)` (overload)           | `obj.set("k", v)`                 |
| Mutate counter                    | `c.increment(1)`                | `c.increment(1)`                    | `c.increment(1)`                        | `c.increment(1)`                  |
| Atomic deep create                | `LiveMap.create({…})`           | `LiveMap.create(Map.of(…))`         | `liveMapOf(…)`                          | `LiveMap.create({…})`             |
| Instance access                   | `obj.instance()` (returns `LiveCounter \| LiveMap \| undefined`) | `obj.instance()` → `LiveInstance?` | `obj.instance()` (suspend) | `obj.instance()` |
| Object ID                         | `instance.id`                   | `instance.id()`                     | `instance.id()`                         | `instance.id()`                   |
| Snapshot (cycles preserved)       | `obj.compact()`                 | `obj.compact()` → `Map<String, LiveValue>` | `obj.compact()`                      | `obj.compact()`                   |
| Snapshot (JSON-safe)              | `obj.compactJson()`             | `obj.compactJson()` → `JsonElement` | `obj.compactJson()`                     | `obj.compactJson()`               |
| Path subscribe                    | `obj.subscribe(cb)`             | `obj.subscribe(listener)`           | `obj.subscribe(listener)`               | `obj.subscribe(cb)`               |
| Depth-bounded subscribe           | `obj.subscribe(cb, {depth:1})`  | `obj.subscribe(l, depth(1))`        | `obj.subscribe(l, depth(1))`            | `obj.subscribe(cb, depth=1)`      |

**Differences and why:**

- *Single typed cast methods.* `ably-js` does this implicitly via TypeScript
  generics on the `PathObject<T>` type. JVM languages don't have a
  comparable structural-typing escape hatch at runtime, so we expose
  *throwing* casts (`asLiveMap`, `asLiveCounter`, `asPrimitive`) but keep
  the `PathObject` itself untyped (D1).
- *Sealed value type.* Java doesn't carry the JS union type; we replicate
  it with a sealed `LiveValue` hierarchy so consumers can use exhaustive
  `switch` (Java 21+) or `when` (Kotlin).
- *`MessageOptions`.* Dropped, per D9 (matches JS, removes ambiguity).

---

## 9. Usage Examples

### 9.1 Hello, root

```java
RealtimeChannel channel = client.channels.get("game:123");
RootPathObject root = channel.object().get();
```

```kotlin
val channel = client.channels.get("game:123")
val root = channel.realtimeObject.getRoot()
```

### 9.2 Atomic deep create

```java
root.set("game", LiveMap.create(Map.of(
    "title",   LivePrimitive.of("Chess"),
    "players", LiveMap.create(Map.of(
        "alice", LiveMap.create(Map.of(
            "score",  LiveCounter.create(0),
            "colour", LivePrimitive.of("white")
        )),
        "bob", LiveMap.create(Map.of(
            "score",  LiveCounter.create(0),
            "colour", LivePrimitive.of("black")
        ))
    )),
    "state", LivePrimitive.of("ongoing")
)));
```

```kotlin
root.set("game", liveMapOf(
    "title"   to "Chess",
    "players" to liveMapOf(
        "alice" to liveMapOf("score" to liveCounterOf(), "colour" to "white"),
        "bob"   to liveMapOf("score" to liveCounterOf(), "colour" to "black"),
    ),
    "state" to "ongoing",
))
```

### 9.3 Reads

```java
// Primitive
LiveValue v = root.at("game.title").value();         // may be null
String title = (v instanceof LivePrimitive p && p.isString()) ? p.asString() : null;

// Counter (assert)
double aliceScore = root.at("game.players.alice.score").asLiveCounter().value();

// Map iteration
for (Map.Entry<String, PathObject> e : root.at("game.players").entries()) {
    System.out.println(e.getKey() + " → " + e.getValue().compact());
}
```

### 9.4 Writes

```java
root.at("game.players.alice").set("colour", LivePrimitive.of("blue"));
root.at("game.players.alice.score").increment(5);
root.at("game.players").remove("bob");
```

### 9.5 Subscriptions

```java
PathSubscription sub = root.at("game").subscribe(event -> {
    System.out.println("changed at: " + event.object().path());
    System.out.println("by:         " + event.message().getClientId());
}, PathSubscriptionOptions.unlimited());

// Only top-level changes to "game"
root.at("game").subscribe(
    e -> System.out.println("top-level: " + e.object().path()),
    PathSubscriptionOptions.depth(1)
);

sub.unsubscribe();
```

### 9.6 Instance-pinned subscription

```java
LiveCounterInstance c = root.at("game.players.alice.score").asLiveCounter();
String pinned = c.id();              // counter:abc@…
c.subscribe(e -> { /* fires even if the entry is reseated at a new path */ });
```

### 9.7 Snapshots

```java
Map<String, LiveValue> snap = root.compact();
JsonElement json = root.compactJson();
client.log.debug("state = " + json);
```

### 9.8 Type-change resilience (the point of D1)

In **Java 8** the public API can't use `switch` pattern matching, so the
canonical defensive read uses `instanceof`:

```java
// 'value' at this path may be a primitive today, a counter tomorrow
PathObject p = root.at("session.kind");

LiveValue v = p.value();
if (v == null)                            handleMissing();
else if (v instanceof LivePrimitive)      handlePrimitive((LivePrimitive) v);
else if (v instanceof LiveCounterInstance) handleCounter((LiveCounterInstance) v);
else if (v instanceof LiveMapInstance)    handleMap((LiveMapInstance) v);
```

In **Kotlin** (recommended for new code), the same logic reads cleanly with
exhaustive `when`:

```kotlin
when (val v = p.value()) {
    null                       -> handleMissing()
    is LivePrimitive           -> handlePrimitive(v)
    is LiveCounterInstance     -> handleCounter(v)
    is LiveMapInstance         -> handleMap(v)
    else                       -> error("logically sealed; new variant?")
}
```

This is exactly what **@sacOO7** asked for: the *handle* doesn't lie about
the type, and the consumer is forced to handle each case.

> *Why no `default`/`else` in Java?* `LiveValue` is logically sealed
> (`@ApiStatus.NonExtendable`), but the compiler doesn't know that. An
> `else` branch that throws `IllegalStateException` is good defensive
> practice; we omit it in the snippet above for brevity.

---

## 10. Ergonomics, Naming and Extensibility

### 10.1 Naming choices

| Old (PR #1190) | New | Why |
|---|---|---|
| `LiveMapPathObject` | `PathObject` (and `RootPathObject` for the root) | D1 / @sacOO7 — types at a path are dynamic |
| `LiveCounterPathObject` | `PathObject` | D1 |
| `StringPathObject` / `NumberPathObject` / `BooleanPathObject` / `BinaryPathObject` | `LivePrimitive` (sealed) | D5 — keep primitives in one type |
| `LiveListPathObject` | *removed* | D7 — not in spec |
| `LiveMap` (as instance type) | `LiveMapInstance` | D6 — avoid name clash with internal type and with the factory |
| `LiveCounter` (as instance type) | `LiveCounterInstance` | D6 |
| `Primitive.create("x")` | `LivePrimitive.of("x")` | matches Java idioms; "create" implies network creation |
| `MessageOptions` parameter | *removed* | D9 |
| `channel.getObject().get()` | `channel.object().get()` (Java) / `channel.realtimeObject.getRoot()` (Kotlin) | @sacOO7 B |
| `compact() : JsonValue` | `compact() : Map<String, LiveValue>` + `compactJson() : JsonElement` | @sacOO7 D, @lawrence-forooghian |

### 10.2 Extensibility

- **Adding `LiveList` later** is a pure additive change: a new
  `LiveListInstance extends LiveInstance` and a new `LiveList.create(...)`
  factory. No `PathObject` surface change is required.
- **Adding new primitive types** (e.g. a future `Date`/`Decimal` value):
  extend the sealed `LivePrimitive` with a new variant.
- **Adding per-message metadata** (the old `MessageOptions` idea): can be
  reintroduced as method overloads (e.g. `set(key, value, MessageMetadata)`)
  without breaking the canonical form.

### 10.3 Java/Kotlin ergonomics

- *Java sealed types* (Java 17+) let consumers exhaustively switch over
  `LiveValue`.
- *Kotlin* gets:
  - `realtimeObject` as a property extension on `RealtimeChannel`,
  - operator overloads (`obj["key"]` ≡ `obj.get("key")`),
  - `suspend` shims around every `*Async` Java method,
  - `liveMapOf(...)` / `liveCounterOf(...)` DSL functions.

---

## 11. Open Questions, Trade-offs, Alternatives

### 11.1 Open questions

1. **Is `at("a.b.c")` desirable** when `get("a").get("b").get("c")` already
   works? The PDF draft has `at(...)` only on the root. Decision needed
   before public release.
2. **Path encoding for keys containing `.`**: do we follow JS's choice (no
   escape — disallow `.` in keys), or accept escaped paths
   (e.g. `\.`)? Recommendation: disallow `.` in keys, document it.
3. **Should `asLiveMap`/`asLiveCounter` return `Optional<…>`** instead of
   throwing? @sacOO7 leaned toward avoiding throws when the result is
   *naturally* nullable. Trade-off: `Optional` is awkward in Java method
   chains; an explicit `throws` mirrors JS's hard-failure behaviour.
4. **`subscribe_async` (Python-only)** — Python supports an `AsyncIterator`
   form. Kotlin could mirror this with a `Flow<PathChangeEvent>`. Decision:
   *include* `pathObject.events(): Flow<PathChangeEvent>` in the Kotlin
   facade.
5. **Backpressure on subscriptions** — listeners are invoked from the
   transport thread. Should the SDK offer a built-in dispatcher option, or
   leave that to user code? Recommend: same model as the existing
   `LiveMap` / `LiveCounter` subscriptions (no built-in backpressure).
6. **Atomic creation token type** — making `LiveMap.create(...)` return
   `LiveValue` lets it be used wherever `LiveValue` is expected, but it
   means a creation token *looks like* a `LiveMapInstance` while not being
   one (it has no `id()` until the operation lands). Alternative: return a
   distinct `LiveValue.Create` subtype and require explicit wrapping. The
   current proposal favours the ergonomic option.

### 11.2 Trade-offs

| Choice | Benefit | Cost |
|---|---|---|
| Untyped `PathObject` | Honest about type drift; matches `ably-js` | Loses compile-time exhaustiveness on path navigation |
| Sealed `LiveValue` | Exhaustive `switch`/`when` for consumers | Sealed types require JDK 17+ for Java consumers (already required by the SDK build) |
| `compact()` returns `Map<String, LiveValue>` | Preserves cycles & instances | Heavier than JS's plain JS object; not directly JSON-serializable |
| Dropping `MessageOptions` | Matches JS | If we ever need per-op metadata, we'll need overloads later |
| Keeping `getObjects()` deprecated | Backwards compat for one release | Two APIs visible during the deprecation window |

### 11.3 Alternatives considered

- **Generic `PathObject<T>`** like TypeScript. Rejected because at runtime
  the JVM has no way to check `T` so `value() : T` is dangerously wrong
  whenever the schema drifts. This is exactly @sacOO7's objection.
- **Specialised `*PathObject` interfaces** (PR #1190). Rejected per D1 —
  see review thread A.
- **`compact()` returning `JsonElement` only**. Rejected — destroys
  cycles and binary fidelity. We keep both `compact()` and `compactJson()`.

---

## 12. Implementation Plan in `ably-java`

This section describes how the proposal can be realised against the current
codebase at `/Users/sachinsh/IdeaProjects/ably-java`.

### 12.1 Codebase landmarks (relevant to this work)

| Component | Path | Role |
|---|---|---|
| Public objects API (current) | `lib/src/main/java/io/ably/lib/objects/RealtimeObjects.java` | Existing `getRoot()`, `createMap()`, … |
| Public instance types | `lib/src/main/java/io/ably/lib/objects/type/{map,counter}` | `LiveMap`, `LiveCounter`, `LiveMapValue` |
| Plugin glue | `lib/src/main/java/io/ably/lib/objects/LiveObjectsPlugin.java` | `getInstance(channelName)` |
| Channel hook | `lib/src/main/java/io/ably/lib/realtime/ChannelBase.java:115` | `getObjects()` returns `RealtimeObjects` |
| Internal pool & runtime | `liveobjects/src/main/kotlin/io/ably/lib/objects/{ObjectsPool,ObjectsManager,DefaultRealtimeObjects}.kt` | The actual CRDT engine; this is where path resolution will hook |
| Wire types | `liveobjects/src/main/kotlin/io/ably/lib/objects/{ObjectMessage,ObjectId,serialization}` | Used by subscriptions and `compactJson` |

### 12.2 New modules / packages

1. **Public Java API** — new package `io.ably.lib.objects.path` under
   `lib/src/main/java/`. Contains `PathObject`, `RootPathObject`,
   `LiveValue`, `LivePrimitive`, `LiveMapInstance`,
   `LiveCounterInstance`, `LiveMap`, `LiveCounter` (factories),
   `PathChangeEvent`, `PathSubscription`, `PathSubscriptionOptions`,
   `PathChangeListener`, `ChannelObject`.

2. **Internal implementation** — under
   `liveobjects/src/main/kotlin/io/ably/lib/objects/path/`:
   - `DefaultPathObject.kt` — Kotlin class implementing `PathObject`
     against the existing `ObjectsPool` / `ObjectsManager`.
     Holds a `path: List<String>` and a reference to the channel's
     `DefaultRealtimeObjects`.
   - `DefaultRootPathObject.kt` — extends `DefaultPathObject`, narrows
     `instance()` to `LiveMapInstance`.
   - `MapCreate.kt`, `CounterCreate.kt` — creation tokens carrying nested
     literals; the `ObjectsManager` serialises them into the
     `MAP_CREATE` / `COUNTER_CREATE` wire operations.
   - `PathResolver.kt` — single utility responsible for walking from
     root → leaf inside the pool. Returns either `LiveValue?` (for reads)
     or the parent `LiveMap` + final key (for writes).
   - `Compactor.kt` — implements `compact()` and `compactJson()` (the
     latter shares logic with the wire serializer for `ObjectId` refs).

3. **Kotlin facade** — under `liveobjects/src/main/kotlin/io/ably/lib/objects/path/ext/`:
   - `RealtimeChannelExtensions.kt` — `val RealtimeChannel.realtimeObject`,
     `suspend fun ChannelObject.getRoot()`.
   - `PathObjectExtensions.kt` — `suspend` shims, `operator fun get`,
     `liveMapOf`, `liveCounterOf`.

### 12.3 API surface changes (Java)

Touched files: `lib/src/main/java/io/ably/lib/realtime/ChannelBase.java`,
`lib/src/main/java/io/ably/lib/objects/LiveObjectsPlugin.java`, and all the
new files under `lib/src/main/java/io/ably/lib/objects/path/`.

```diff
 // lib/src/main/java/io/ably/lib/realtime/ChannelBase.java (line ~115)
+    /** Path-based LiveObjects accessor for this channel. */
+    public ChannelObject object() throws AblyException {
+        if (liveObjectsPlugin == null) {
+            throw AblyException.fromErrorInfo(new ErrorInfo(
+                "LiveObjects plugin hasn't been installed, " +
+                "add runtimeOnly('io.ably:liveobjects:<ably-version>') to your dependency tree",
+                400, 40019));
+        }
+        return liveObjectsPlugin.getChannelObject(name);
+    }
+
+    /** @deprecated Use {@link #object()} (path-based API) instead. */
+    @Deprecated
     public RealtimeObjects getObjects() throws AblyException { … }
```

```diff
 // lib/src/main/java/io/ably/lib/objects/LiveObjectsPlugin.java
+    @NotNull io.ably.lib.objects.path.ChannelObject getChannelObject(@NotNull String channelName);
```

`Channel.java` (`java/src/main/java/io/ably/lib/realtime/Channel.java`) does
not need any changes — `object()` is inherited from `ChannelBase`.

### 12.4 Internal implementation approach

#### Path resolution

`DefaultPathObject` stores only the path (`List<String>`) and a back-reference
to `DefaultRealtimeObjects`. **Nothing is resolved until a terminal call.**
Terminal calls go through `PathResolver`:

```kotlin
class PathResolver(private val objects: DefaultRealtimeObjects) {

    /** Returns the LiveValue at [path], or null if missing or tombstoned. */
    fun resolve(path: List<String>): LiveValue? {
        var cur: Any? = objects.root  // LiveMap
        for (segment in path) {
            cur = when (cur) {
                is LiveMap -> cur.get(segment)?.unwrap()  // LiveMapValue → LiveValue
                else -> return null   // dead end
            }
        }
        return cur as? LiveValue
    }

    /** Returns (parent LiveMap, final-key) for writes. */
    fun resolveParent(path: List<String>): Pair<LiveMap, String>? {
        require(path.isNotEmpty()) { "cannot write to root with no key" }
        val parent = resolve(path.dropLast(1)) as? LiveMap ?: return null
        return parent to path.last()
    }
}
```

#### Mutations

`set/remove/increment/decrement` resolve to the parent instance and delegate
to the existing `LiveMap` / `LiveCounter` methods. Creation tokens are
detected and converted to the appropriate wire op:

```kotlin
override fun set(key: String, value: LiveValue) {
    val (parent, _) = resolver.resolveParent(this.path + key)
        ?: throw AblyException.fromErrorInfo(ErrorInfo("object not found at $path", 92000))
    val converted = when (value) {
        is MapCreate     -> objects.createMap(value.entries)
        is CounterCreate -> objects.createCounter(value.initial)
        is LiveInstance  -> value.asLiveMapValue()
        is LivePrimitive -> value.asLiveMapValue()
    }
    parent.set(key, converted)
}
```

#### Subscriptions

`PathObject.subscribe` registers with `DefaultRealtimeObjects`'s lifecycle
manager. The manager already publishes `ObjectLifecycleEvent`s per instance;
we add a *path subscription registry* keyed by canonical path. On every
applied operation, the registry computes the affected paths and dispatches
to matching listeners that satisfy the `depth` constraint.

Per @sacOO7's "type-change resilience" point, when a subscription fires, the
delivered `PathChangeEvent.object()` is the **same** dynamic `PathObject` —
listeners must re-read the value, *not* hold a typed cast taken at
subscription time.

#### `compact()` / `compactJson()`

Both walk the pool from the resolved root. `compact()` keeps direct
references to `LiveMapInstance` / `LiveCounterInstance`, naturally
preserving cycles. `compactJson()` does a second pass that breaks cycles by
emitting `{"objectId":"…"}` (same format as JS), inlining primitives, and
base64-encoding binary `LivePrimitive`s.

### 12.5 Threading & lifecycle

- `DefaultPathObject` is immutable; safe to share across threads.
- All terminal operations route through the same `Worker` thread used by
  the existing `ObjectsManager`, so we inherit its thread-safety.
- `PathSubscription.unsubscribe()` is idempotent.
- When a channel detaches or releases, all path subscriptions are torn
  down; future terminal calls on stale `PathObject`s throw
  `AblyException(code=92010)` (`channel not attached`).

### 12.6 Tests

- **Unit tests** (Kotlin, JUnit 5) under
  `liveobjects/src/test/kotlin/io/ably/lib/objects/unit/path/`:
  `DefaultPathObjectTest`, `PathResolverTest`, `CompactorTest`,
  `MapCreateTest`, `LivePrimitiveTest`, `RootPathObjectTest`.
- **Integration tests** under
  `liveobjects/src/test/kotlin/io/ably/lib/objects/integration/path/`:
  drive the full client against the sandbox and verify path mutations
  echo back; assert `instance().id()` parity between two clients;
  subscribe-with-depth tests.
- **Spec conformance**: add path-based scenarios to the existing UTS spec
  runner — they should mirror the `ably-common/objects` fixtures.

### 12.7 Phased delivery

| Phase | Scope | Outcome |
|---|---|---|
| P0 | Public interfaces + Kotlin facade, no behaviour | API source set compiles; `@Deprecated` annotations applied to `RealtimeObjects` |
| P1 | Read path: `PathObject.value()`, `compact()`, `compactJson()`, `instance()`, `asLiveMap/asLiveCounter` | Unit + integration reads pass |
| P2 | Write path: `set`/`remove`/`increment`/`decrement` + `LiveMap.create` / `LiveCounter.create` deep create | Two-client roundtrip tests pass |
| P3 | Subscriptions with depth | `PathSubscription` lifecycle covered |
| P4 | Async / Kotlin `suspend` / `Flow` | Parity with existing `…Async` style; Kotlin DSL polished |
| P5 | Docs & migration guide | `/liveobjects/README.md` updated, blog post draft |

---

## 13. Migration and Backward Compatibility

- `channel.getObjects()` and `RealtimeObjects` remain functional for one
  major release with `@Deprecated(forRemoval = false)` and a Javadoc
  pointer to `channel.object()`.
- Internally, `RealtimeObjects.getRoot()` returns a `LiveMap` that is also
  the underlying instance behind `channel.object().get().instance()`, so
  hybrid usage during migration is safe.
- The `LiveMap` and `LiveCounter` interfaces under
  `io.ably.lib.objects.type.{map,counter}` are kept stable; the new public
  types `LiveMapInstance` / `LiveCounterInstance` extend them, which means
  every existing `LiveMap` reference is implicitly a `LiveMapInstance` and
  can be passed into the new API.
- No wire-format changes — all existing recorded fixtures still pass.

A short migration table for users:

| Old | New |
|---|---|
| `channel.getObjects().getRoot()` | `channel.object().get()` |
| `root.get("k")` returning `LiveMapValue` | `root.get("k").value()` returning `LiveValue?` |
| `root.set("k", LiveMapValue.of("v"))` | `root.set("k", LivePrimitive.of("v"))` |
| `objects.createMap(entries); root.set("a", LiveMapValue.of(m))` | `root.set("a", LiveMap.create(entries))` |
| Subscribing to a `LiveMap` instance | `root.at("path").subscribe(…)` for path-pinned, *or* `instance.subscribe(…)` for instance-pinned |

---

## 14. Risks, Challenges and Testing Strategy

### 14.1 Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Wanting real `sealed` types in the public Java API | Met by `@ApiStatus.NonExtendable` + package-private impls; **no source-compatibility bump required** — `:java` module remains at `JavaVersion.VERSION_1_8` (`java/build.gradle.kts:11-14`) | We accept that consumers using Java 21 won't get exhaustive `switch` over `LiveValue`. Kotlin users get full exhaustive `when`. |
| Path subscription dispatch is N×M (paths × ops) and could be hot on busy channels | Med | Index subscriptions by their longest common prefix; only walk affected branches on each op |
| `compact()` returning instances allows users to mutate the snapshot and confuse themselves | Low | Document clearly that `compact()` is a *live* view; provide `compactJson()` for snapshots |
| Two `LiveMap` names (factory vs internal) | Low | Place factory in `io.ably.lib.objects.path` package so the import is explicit |
| Deep-create token misuse — e.g. passing a `MapCreate` to a `set` that resolves to a counter | Low | Validate at `set` time and throw `AblyException` with a clear message |
| Schema drift at a path is now silent (no compile-time guard) — exactly @sacOO7's point | Inherent | This is a feature, not a bug; ensure samples and docs cover `switch (LiveValue)` patterns |

### 14.2 Testing strategy

1. **Unit tests** (Kotlin) cover:
   - Path navigation with mixed map/primitive/counter trees, including
     paths that traverse a missing key.
   - `value()` / `asLiveMap` / `asLiveCounter` error semantics (per D10).
   - `compact()` cycle preservation; `compactJson()` cycle breaking and
     binary base64.
   - `MapCreate` / `CounterCreate` round-trip through
     `DefaultRealtimeObjects.createMap` / `createCounter`.
   - Subscription depth (0 = self only, 1 = self+direct children, …).
2. **Integration tests** (sandbox):
   - Two clients, one writes via path API, the other reads via path API.
   - Cross-API: client A writes via legacy `RealtimeObjects`, client B
     reads via path API and vice versa.
   - Replay of `ably-common/objects` fixtures through the path API.
3. **Conformance**:
   - Add path examples to the existing UTS spec runner so the same English
     scenarios run against JS, Kotlin and Python implementations.
4. **Performance smoke tests**:
   - Throughput of `compact()` on a 10k-entry tree.
   - Subscription dispatch on a channel with 1k path subscriptions.

---

## Appendix A — Mapping of @sacOO7's review comments to decisions

| Comment | Line | Adopted as |
|---|---|---|
| "PathObject … feels odd when we apply concrete typing here, … maybe `StringInstance`, `LiveMapInstance`" | 22 | D1, D2 |
| "types for a given instance remain same … in case of PathObject, type at given path can change" | 22 | D1 |
| "we need to give a thought about we can make `PathObjects` strongly typed. … consistent across SDKs" | 22 | D1 (resolved as: strong typing on **Instance**, not **PathObject**) |
| "`channel.object().get()` … in Kotlin `channel.realtimeObject.get()`" | 53 | D4 |
| "we agree to use `LiveMapPathObject myObject = channel.object().get()` instead of `channel.getObject().get()`" | 53 | D4 |
| "We currently don't have `Primitive` as explicit type … maybe `LivePrimitive`" | 39, 362 | D5 |
| "`LiveMap`/`LiveCounter` is internal … ably-js exports `LiveCounterValueType` → `LiveCounter`" | 66 | D6 |
| "I don't think we can represent `compact` for ably-java … return `Map<String, object>` … superclass for Primitive/LiveMap/LiveCounter" | 110 | D8 (`Map<String, LiveValue>` + sealed `LiveValue`) |
| "Root object will always be `LiveMapPathObject`, … should be called `RootPathObject` instead" | 328 | D3 |
| "It's not frequent that types can change dynamically for children, still need to consider …" | 328 | D1 (consumer-side `switch` on `LiveValue`) |
| "`LiveListPathObject` doesn't exist" | 154 | D7 |
| "`visits.value` can return string or livemap based on underlying changed type" | 336 | D1 / D10 (typed casts throw; `value()` returns `LiveValue?`) |
| "This defeats purpose of updating reading/writing dynamic types at given paths" (re explicit `asLiveMap()` cast) | 887 | D1 — but typed casts kept as an *opt-in* assertion; the main API stays dynamic |
| Suggestion: change `instance()` return type to `LiveMapInstance` | 143 | D2 |

---

---

## 15. Codebase Audit — what changed in v2

A line-by-line check against the actual source tree at
`/Users/sachinsh/IdeaProjects/ably-java` revealed the following gaps in v1,
all of which are now reflected in this document:

| # | Finding | Source-of-truth | Fix |
|---|---|---|---|
| A1 | `lib/src/main/java/**` compiles at **Java 8**, not 17 | `java/build.gradle.kts:11-14` (`sourceCompatibility = VERSION_1_8`); `sourceSets.main.java.srcDirs("src/main/java", "../lib/src/main/java")` | §7 prologue states this constraint; removed all `sealed interface` / `switch` pattern matching from public Java API. Sealed types kept only in Kotlin module. |
| A2 | The public realtime channel class is `Channel`, not `RealtimeChannel` | `java/src/main/java/io/ably/lib/realtime/Channel.java` (extends `ChannelBase`) | All Kotlin extensions now hang off `Channel`; Java examples use `Channel channel = client.channels.get(...)`. |
| A3 | An `ObjectsSubscription` type already exists with the correct contract | `lib/src/main/java/io/ably/lib/objects/ObjectsSubscription.java` (spec RTLO4b5/a) | Removed the proposed `PathSubscription` type; `PathObject#subscribe` returns `ObjectsSubscription` directly. |
| A4 | An `ObjectsCallback<T>` type already exists | `lib/src/main/java/io/ably/lib/objects/ObjectsCallback.java` | All async methods use the existing callback type. |
| A5 | `LiveMapValue` already covers the full primitive union — including `JsonArray` and `JsonObject` — and uses an `of(...)` factory style | `lib/src/main/java/io/ably/lib/objects/type/map/LiveMapValue.java` | `LivePrimitive` was rebuilt to match exactly (added `isJsonArray()/isJsonObject()` and `asJsonArray()/asJsonObject()`); a `toMapValue()`/`fromMapValue(...)` bridge is documented so we don't keep two parallel hierarchies in sync by hand. |
| A6 | The existing `LiveMap#subscribe(Listener)` already returns `ObjectsSubscription` and delivers a `LiveMapUpdate` | `lib/src/main/java/io/ably/lib/objects/type/map/LiveMapChange.java` and `LiveMapUpdate.java` | Documented that path-level subscriptions are a *separate* registry — they receive a `PathChangeEvent` (path + `ObjectMessage`), not the per-key delta `LiveMapUpdate`. Instance-level subscriptions still go through `LiveMapChange` unchanged. |
| A7 | The internal `ObjectMessage` is Kotlin-only and not part of the public API | `liveobjects/src/main/kotlin/io/ably/lib/objects/ObjectMessage.kt` | Added §7.8 — a new public Java `io.ably.lib.objects.path.ObjectMessage` interface that the internal Kotlin class is adapted to. |
| A8 | `LiveCounter` already uses `Number` (not `Double`) for `increment`/`decrement` | `lib/src/main/java/io/ably/lib/objects/type/counter/LiveCounter.java:28` | `PathObject#increment(Number)` matches. (CodeRabbit suggested `Double`; we keep `Number` for consistency with the existing instance API.) |
| A9 | `LiveMap#size()` returns boxed `Long`, not `long` | `LiveMap.java:101` | `PathObject#size()` returns `Long` (nullable) to match — also lets us return `null` when the path doesn't resolve to a map (per PDF spec). |
| A10 | The `LiveObjectsPlugin` interface has no `getChannelObject` method today; only `getInstance`, `handle`, `handleStateChange`, `dispose` | `lib/src/main/java/io/ably/lib/objects/LiveObjectsPlugin.java` | §7.9 / §12.3 now show this as a **new** plugin method to add. Default impl lives in `DefaultLiveObjectsPlugin.kt`. |
| A11 | `@Deprecated(forRemoval=…)` is Java 9+ | n/a | Use plain `@Deprecated` + `@deprecated` Javadoc tag. |
| A12 | `DefaultRealtimeObjects` already has an `asyncScope` / sequential coroutine scope used by every `*Async` callback | `liveobjects/src/main/kotlin/io/ably/lib/objects/DefaultRealtimeObjects.kt:51-94` | Path-API async impls reuse this scope; no new threading infrastructure needed. |
| A13 | `:liveobjects` already declares `compileOnly(project(":java"))` and `kotlin { explicitApi() }` | `liveobjects/build.gradle.kts` | New Kotlin code under `liveobjects/src/main/kotlin/io/ably/lib/objects/path/` slots in directly; nothing in the build needs to change. |
| A14 | `Map.of(...)` (used in examples) is **Java 9+** | n/a | Examples remain readable on Java 9+ consumers; for Java 8 only, the SDK itself doesn't use `Map.of` in the public surface — only in user examples, which is consumer-side. Documented as such (open question §11.1). |
| A15 | "RootPathObject" terminology — confirmed compatible with @sacOO7 review comment "Maybe this should be called RootPathObject instead" | PR #1190 comment 3324310520 | Kept. |
| A16 | **Reads are not `@Blocking`** in the existing API — only writes are. `LiveMap.get/entries/keys/values/size` and `LiveCounter.value` carry either no annotation or `@Contract(pure = true)`; only `set/remove/increment/decrement` are `@Blocking`. | `lib/.../LiveMap.java` (lines 33, 42, 52, 62, 77, 90, 99, 115, 129), `lib/.../LiveCounter.java` (lines 27, 37, 51, 62, 71) | §7.2 prologue table now documents this; removed `@Blocking` from all PathObject read methods (`value/instance/compact/compactJson/as*/entries/keys/values/size`), kept it on writes only. Dropped `valueAsync` (no value over the sync read); kept `*Async` only for the four mutating methods. Added decision **D11** capturing this. |

**Net effect on the public Java API surface size** (after corrections):

```
io.ably.lib.objects.path
├── ChannelObject               (interface)
├── LiveCounter                 (final class — factory only)
├── LiveCounterInstance         (interface, extends LiveInstance + internal LiveCounter)
├── LiveInstance                (interface, extends LiveValue)
├── LiveMap                     (final class — factory only)
├── LiveMapInstance             (interface, extends LiveInstance + internal LiveMap)
├── LivePrimitive               (interface, extends LiveValue)
├── LiveValue                   (interface)
├── ObjectMessage               (interface)
├── PathChangeEvent             (interface)
├── PathChangeListener          (functional interface)
├── PathObject                  (interface)
├── PathSubscriptionOptions     (final class)
└── RootPathObject              (interface, extends PathObject)
```

14 public Java types — minimal, every one is justified by either a JS API
parity requirement or a Java type-system necessity. No new
`*Subscription`, `*Callback`, or duplicate value-type hierarchies.

*End of proposal — feedback welcome on the open questions in §11.1.*
