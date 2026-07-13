# Private Deviations — Objects UTS specs that cannot (yet) be translated

> **Scope.** This file complements [`deviations.md`](./deviations.md). `deviations.md` records
> *per-test* deviations inside tests that **were** translated and compile. This file records the
> opposite: whole UTS spec files from the `objects` module that **could not be translated into the
> `uts` module at all**, why, and what would unblock them. It is written for a human reviewer / the
> LiveObjects implementers — not consumed by any tooling.

---

## 1. Status of all 15 `objects/unit` specs

| # | UTS spec (`objects/unit/…`) | ably-java test class | Status | Layer it targets |
|---|---|---|---|---|
| 1 | `instance.md` | `InstanceTest` | ✅ Translated | Public view (`Instance`) |
| 2 | `internal_live_counter.md` | `InternalLiveCounterTest` | ⛔ **Blocked** | **Internal CRDT node** |
| 3 | `internal_live_counter_api.md` | `InternalLiveCounterApiTest` | ✅ Translated | Public view |
| 4 | `internal_live_map.md` | `InternalLiveMapTest` | ⛔ **Blocked** | **Internal CRDT node** |
| 5 | `internal_live_map_api.md` | `InternalLiveMapApiTest` | ✅ Translated | Public view |
| 6 | `live_object_subscribe.md` | `LiveObjectSubscribeTest` | ✅ Translated | Public view |
| 7 | `object_id.md` | `ObjectIdTest` | ⛔ **Blocked** | **Internal (object-id gen)** |
| 8 | `objects_pool.md` | `ObjectsPoolTest` | ⛔ **Blocked** | **Internal (`ObjectsPool`)** |
| 9 | `parent_references.md` | `ParentReferencesTest` | ⛔ **Blocked** | **Internal (parent graph)** |
| 10 | `path_object.md` | `PathObjectTest` | ✅ Translated | Public view (`PathObject`) |
| 11 | `path_object_mutations.md` | `PathObjectMutationsTest` | ✅ Translated | Public view |
| 12 | `path_object_subscribe.md` | `PathObjectSubscribeTest` | ✅ Translated | Public view |
| 13 | `public_object_message.md` | `PublicObjectMessageTest` | ✅ Translated | Public message layer |
| 14 | `realtime_object.md` | `RealtimeObjectTest` | ✅ Translated (mixed) | Public `get()` + sync events |
| 15 | `value_types.md` | `ValueTypesTest` | ✅ Translated (mixed) | Public `create` surface |

**10 translated, 5 blocked.** The 5 blocked specs are the subject of this document.

> Note: the translated specs that depend on `setupSyncedChannel` (most of the public-view tests)
> compile today but only *run* once the SDK's `OBJECT_SYNC` processing + `RealtimeObject.get()` land.
> That is the same missing engine described below — see [`deviations.md`](./deviations.md) and the
> `helpers.kt` header for the per-test runtime caveat. The blocked specs below are a stronger case:
> they cannot even be *written*.

---

## 2. Why these 5 specs target internals

The objects spec is layered into three tiers (see the skill's `objects-mapping.md`):

1. **Creation value types** — the immutable `LiveMap` / `LiveCounter` blueprints you pass *into* `set`.
2. **Public read/write view** — `PathObject` / `Instance`, what user code navigates and subscribes on.
3. **Internal CRDT graph** — the live conflict-free replicated nodes, the object pool, object-id
   generation and the parent-reference graph. This is the convergence engine.

The 10 translated specs live in tiers 1–2. The 5 blocked specs **are** tier 3. They have to assert on
internal state because the behaviour they pin down — last-write-wins arbitration by site-serial,
idempotent re-application, tombstones, create-op merging, garbage collection, object-id derivation —
is **not observable through the public API**. You cannot verify "the second of two concurrent ops
loses by site-serial" with `get()`/`value()`; you have to reach the node's `siteTimeserials` and call
`applyOperation` directly. So the spec is correct to test internals — that is where the hard logic is.

---

## 3. The two blockers (in order of severity)

### Blocker A — the internal implementation does not exist yet *(primary)*

`:liveobjects` currently implements the **view** layer only. A symbol search of
`liveobjects/src/main/kotlin/io/ably/lib/liveobjects` confirms the CRDT engine these specs assert on
is absent:

| Symbol required by the blocked specs | Found in `:liveobjects`? |
|---|---|
| `ObjectsPool` (the live object pool) | ❌ 0 references |
| `generateObjectId` / object-id derivation (`RTO14`) | ❌ 0 references |
| `applyOperation(...)` (apply op to a live node) | ❌ 0 references |
| `replaceData(...)` | ❌ 0 references |
| `createOperationIsMerged` | ❌ 0 references |
| parent-reference graph (`parentRef…`) | ❌ 0 references |
| pool `syncState` | ❌ 0 references |
| `siteTimeserials` | ⚠️ only on the **wire DTO** (`WireObjectState` / `WireObjectsMapEntry`), not on a live CRDT node |

What *does* exist: `DefaultPathObject`, `DefaultInstance`, the typed `Default*PathObject` /
`Default*Instance` views, the `value/` creation types, and the `message/` + `serialization/` wire layer.
There is **no live `InternalLiveMap` / `InternalLiveCounter` node, no `ObjectsPool`, and no
operation-application engine.**

**Consequence:** even with perfect cross-module visibility there is nothing to instantiate or assert
against. These tests cannot be authored until the engine is implemented.

### Blocker B — Kotlin `internal` is not visible across the module boundary *(secondary, applies once A is done)*

When the engine *is* implemented it will (by the codebase's convention, and because `:liveobjects`
uses `explicitApi()`) be declared `internal` — exactly like the existing `Default*` classes
(`internal class DefaultLiveMap`, `internal class DefaultPathObject`, …).

Kotlin's `internal` is scoped to a **module** = one compilation unit = one Gradle source set's compile
task. The `:uts` test source set is a *different* module from `:liveobjects`'s `main`. The Kotlin
compiler enforces `internal` across that boundary **regardless of dependency classpath scope**. So
`:uts` test code cannot name those declarations at compile time.

This is why the existing helper `buildPublicObjectMessage` (in `helpers.kt`) reaches the internal
wire/message classes by **reflection** (`Class.forName(...)`), enabled by the current
`testRuntimeOnly(project(":liveobjects"))` — runtime-only access. Reflection works for a handful of
constructor/field hops but is the wrong tool for whole-CRDT-state assertions (no type safety, brittle,
unreadable).

---

## 4. Per-spec detail

| Spec | What it asserts on | Required internal symbols | Blocked by |
|---|---|---|---|
| **2 `internal_live_counter.md`** | internal counter node state after applying ops | `InternalLiveCounter` (`.data`, `.siteTimeserials`, `.createOperationIsMerged`, `applyOperation`, `replaceData`) | A + B |
| **4 `internal_live_map.md`** | internal map node state after applying ops | `InternalLiveMap` (`.data`, `.siteTimeserials`, `.isTombstone`, `applyOperation`, `replaceData`) | A + B |
| **7 `object_id.md`** | object-id generation & parsing | `generateObjectId` / object-id type (`RTO14`), `*WithObjectId` derivation | A + B |
| **8 `objects_pool.md`** | the object pool and its sync lifecycle | `ObjectsPool`, `.syncState`, pool entry add/get/clear | A + B |
| **9 `parent_references.md`** | the reverse parent-reference graph | parent-reference tracking on the pool/nodes | A + B |

> Specs 2 and 4 have public counterparts (`internal_live_counter_api.md` / `internal_live_map_api.md`, both translated)
> that cover the *outcome* of these operations through the public API. Specs 7–9 have **no** public
> counterpart — they are purely internal and have no representation in tiers 1–2.

---

## 5. Solution options

The ask was to make all of `liveobjects/src/main/kotlin/io/ably/lib/liveobjects` visible to `:uts`,
probably by changing `testRuntimeOnly(project(":liveobjects"))` to `testImplementation(...)`. Here is
the accurate picture, as lead dev.

### 5.1 Why the `testRuntimeOnly` → `testImplementation` swap alone is *not* sufficient

```kotlin
// uts/build.gradle.kts — current
testRuntimeOnly(project(":liveobjects"))   // runtime classpath only → reflection-only access

// proposed
testImplementation(project(":liveobjects")) // adds COMPILE classpath too
```

`testImplementation` puts `:liveobjects` on the **compile** classpath, which lets `:uts` reference its
**public** API directly (and lets the reflection helpers drop some `Class.forName`). But it does **not**
grant access to `internal` declarations: Kotlin enforces `internal` at the *module* boundary at
compile time, and a dependency-scope change does not cross that boundary. The CRDT engine will be
`internal`, so the swap by itself does not unblock these tests. It is **necessary but not sufficient.**

### 5.2 The Gradle/Kotlin configs that *can* expose internals

There is exactly **one** primitive that grants one Kotlin compilation access to another's `internal`
declarations: the compiler flag **`-Xfriend-paths`**. Everything below is either that flag directly, or
a higher-level wrapper around it.

**(a) `associateWith()` — the *supported* form, but intra-project only.**
The Kotlin Gradle plugin exposes friend-paths through the `associateWith` API on compilations:

```kotlin
kotlin.target.compilations.getByName("test")
    .associateWith(kotlin.target.compilations.getByName("main"))
```

This is how a module's own `test` source set sees its `main` internals (the plugin wires it up
automatically), and how you'd give a *custom* source set (e.g. `integrationTest`) the same access. It is
stable and IDE-aware — **but only between compilations of the same Gradle project.** There is no
supported way to `associateWith` a compilation in a *different* project (`:uts` test ↔ `:liveobjects`
main). Source: KTIJ-7662, KT associated-compilations docs.

**(b) Raw `-Xfriend-paths` across projects — works, but unstable/unsupported.**
You can manually point `:uts`'s test-compile task at `:liveobjects`'s `main` output:
`-Xfriend-paths=…/liveobjects/build/classes/kotlin/main`. Per the Kotlin team this flag has *"no syntax,
no IDE support, and no guarantees of stability — a compiler implementation detail, not a language
feature."* It also hard-couples `:uts` to `:liveobjects`'s internal compile output path. **Not
recommended** for production build config.

**(c) The future fix (not available yet): `shared internal` (KEEP-0451).**
The Kotlin team is *not* stabilizing `-Xfriend-paths`; instead KEEP-0451 proposes a first-class
`shared internal` visibility modifier — declarations visible to designated dependent modules but not
the general public. When it ships this is the clean answer, but it is a proposal today, not usable.

### 5.3 Cleanest technical approach — write the internal-graph tests in `:liveobjects`'s own test source set

> This is the lowest-ceremony option and what the SDK already does for its own internals, but it places
> the tests **outside `uts/unit`**. If keeping them under `uts/unit` is required, prefer §5.4(a) instead.

`:liveobjects` **already has** a unit-test source set and task:

```kotlin
// liveobjects/build.gradle.kts (existing)
tasks.register<Test>("runLiveObjectsUnitTests") {
    filter { includeTestsMatching("io.ably.lib.liveobjects.unit.*") }
}
```

Tests placed under `liveobjects/src/test/kotlin/io/ably/lib/liveobjects/unit/…` see **all** of `main`'s
`internal` declarations automatically (the plugin sets the friend-path for a module's own tests). This
is the standard, supported way to test internal Kotlin code.

**Plan once Blocker A is resolved (engine implemented):**
1. Author specs 2, 4, 7, 8, 9 in `:liveobjects`'s own test source set, e.g. package
   `io.ably.lib.liveobjects.unit.uts`, mirroring the `uts` conventions (one `@Test` per spec case, a
   `/** @UTS objects/unit/… */` KDoc tag, the `deviations.md` discipline).
2. Specs 7–9 (`object_id`, `objects_pool`, `parent_references`) are pure logic with no network/sync —
   they will **run immediately** there, no mock-WebSocket harness needed.
3. Specs 2 and 4 need object state applied to a node; reuse / port the relevant `helpers.kt` builders.
4. Keep `:uts`'s `testRuntimeOnly(project(":liveobjects"))` as-is (reflection helpers stay valid), or
   optionally promote to `testImplementation` purely for compile-time access to the **public** API.

**Trade-off:** these five tests then live outside the `uts` module the skill normally targets. That is
acceptable and correct — they are internal-implementation tests, and the SDK already groups its own
internal tests under `:liveobjects`. The `@UTS` id convention keeps them traceable to the spec.

### 5.4 Keeping the tests under `uts/unit` — what actually works

This is the stated preference, so it gets its own analysis. To assert on `:liveobjects` internals from
test code that physically lives in `:uts`, the realistic options are:

**(a) `java-test-fixtures` bridge — the recommended way to honour the `uts/unit` preference.**
Apply the `java-test-fixtures` plugin to `:liveobjects` and put a thin **inspection/bridge** layer in
`liveobjects/src/testFixtures/kotlin`. Fixture code *belongs to the module*, so it can touch
`:liveobjects` internals; it then re-exposes them as a small **public** API (e.g.
`fun applyAndSnapshot(...): PublicCounterSnapshot`). `:uts` consumes it with:

```kotlin
// uts/build.gradle.kts
testImplementation(testFixtures(project(":liveobjects")))
```

The **assertions stay in `uts/unit`** (calling the fixture's public API) — your preference is satisfied —
while no raw internal is leaked onto `:uts`'s classpath. Caveat: for the *fixture itself* to see Kotlin
`internal`, its compilation must be associated with `main` (Android exposes
`android.experimental.enableTestFixturesKotlinSupport`; for plain JVM Kotlin verify the testFixtures→main
`associateWith` is wired — it reduces back to §5.2(a), which is supported because it is intra-project).
Cost: you design and maintain the bridge surface.

**(b) Reflection from `:uts` (status quo, no build change).**
Current `testRuntimeOnly(project(":liveobjects"))` already lets `:uts` reach internals by reflection at
runtime — this is what `buildPublicObjectMessage` does. Keeps tests in `uts/unit` with zero build
changes, but: stringly-typed, no compile-time safety, brittle to refactors, and verbose for whole-CRDT
assertions. Fine for a couple of accessors; poor for five spec files of state assertions.

> **On `@VisibleForTesting`:** it does **not** change visibility. It is a documentation/lint hint that
> records what the visibility *would* be if not for tests; the actual access is still governed by the
> `public`/`internal` modifier. So "mark it `@VisibleForTesting`" only helps if you *also* make the
> member `public` (e.g. `@VisibleForTesting(otherwise = PRIVATE) public fun …`). It is not, by itself, a
> cross-module visibility mechanism.

---

## 6. The realistic options

Only **three** approaches are real candidates for our situation. (The mechanisms in §5.2 —
`associateWith`, raw `-Xfriend-paths`, `shared internal` — and the bare dependency-scope swap in §5.1 are
*not* viable on their own; see the note below.)

| Option | Keeps tests in `uts/unit`? | Compile-safe? | Trade-off |
|---|---|---|---|
| **A. `java-test-fixtures` bridge** (§5.4a) | ✅ yes | ✅ yes | Design a small public snapshot surface in `:liveobjects`'s `testFixtures`. **Best fit for the preference.** |
| **B. Tests in `:liveobjects/src/test`** (§5.3) | ❌ no — live in `:liveobjects` | ✅ yes | Least effort; internals visible by design. The SDK already tests its own internals this way. |
| **C. Reflection from `:uts`** (§5.4b) | ✅ yes | ❌ no | No build change, but stringly-typed and brittle — fine for a few hops, poor for 5 spec files. |

**Not viable on their own:** the bare `testRuntimeOnly` → `testImplementation` swap (exposes only the
*public* API, not `internal`); `associateWith` (supported but *intra-project* — cannot bridge `:uts` ↔
`:liveobjects`); raw cross-project `-Xfriend-paths` (unstable/unsupported); `shared internal` /
KEEP-0451 (future proposal, not available yet).

## 7. Recommendation & sequencing

1. **Now:** nothing to translate for specs 2, 4, 7, 8, 9 — the internal engine they test is unbuilt
   (Blocker A). Leave them blocked; this document is the record.
2. **When the LiveObjects CRDT engine (`ObjectsPool`, internal live nodes + `applyOperation`,
   object-id generation, parent references) is implemented**, pick the visibility approach:
   - **To honour the `uts/unit` preference (recommended):** the **`java-test-fixtures` bridge** (§5.4a) —
     assertions stay in `uts/unit`, a small fixture in `:liveobjects` exposes the needed internal state
     as a public snapshot. Compile-safe and supported.
   - **If colocation isn't required:** author them in **`:liveobjects/src/test`** (§5.3) — least
     ceremony, internals visible by design (the SDK already tests its own internals this way).
3. **Avoid** the bare `testImplementation` swap *as the internal-access mechanism* (it only exposes the
   public API) and the manual cross-project `-Xfriend-paths` hack (unsupported, fragile).

---

## 8. References

- Kotlin associated compilations / `associateWith` (intra-project internal access):
  KTIJ-7662, Kotlin Multiplatform "Configure compilations" docs.
- `-Xfriend-paths` is an unstable compiler detail; future `shared internal` modifier: KEEP-0451
  ("Shared Internals" proposal).
- `@VisibleForTesting` is a lint/documentation hint and does not change visibility.
- Gradle `java-test-fixtures`: test-fixtures code has access to the module's internal API and is
  consumed via `testImplementation(testFixtures(project(":…")))`; Kotlin support may require
  associating the testFixtures compilation with `main`.
