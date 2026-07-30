# Future work — extract shared UTS test infra into a dedicated module

**Status:** proposed / not started. Deferred from `MOVE_COMMON_INFRA/` (which put the infra in
`:uts`'s `testFixtures` variant — the correct incremental step). This doc captures the extraction to
do **when a second consumer beyond `:liveobjects` materialises**.

## 1. Why (the trigger)

The shared UTS test infrastructure (mock WebSocket/HTTP transports, `FakeClock`, client factories,
`SandboxApp`, proxy control) currently lives in **`uts/src/testFixtures/kotlin/io/ably/lib/uts/infra/`**
and is consumed by:

- `:uts`'s own tests (automatically — a module sees its own test fixtures), and
- `:liveobjects` tests via `testImplementation(testFixtures(project(":uts")))`.

Two more consumers are anticipated:

1. **The Chat SDK** — will reuse the mock transport / sandbox provisioning for its own UTS-derived tests.
2. **`lib/src/test`** — i.e. the **`:java` module's test source set** (its build file is `java/build.gradle.kts`;
   sources are wired in via `srcDirs(".../lib/src/test/java")`).

Once infra is shared by three-plus modules, hanging it off `:uts`'s test-fixtures variant becomes a
naming/ownership smell: modules that have nothing to do with UTS would depend on `testFixtures(project(":uts"))`,
and the package is `io.ably.lib.uts.infra`. At that point a **dedicated module** is the clean home — it
gets a name that reflects "shared test support," and IntelliJ shows it as an ordinary module with a normal
`src/main/kotlin` (no special test-fixtures source-root category).

Until then, `testFixtures` is intentionally preferred: zero custom Gradle, test-only, already cross-module
consumable. **Do not do this extraction speculatively** — it only pays for itself once the second consumer
is real.

## 2. Target state

A new module, e.g. `:test-support` (neutral name; not `:uts-test-infra`, since chat/`:java` aren't UTS):

```
test-support/
  build.gradle.kts
  src/main/kotlin/io/ably/lib/testsupport/…      # the infra, moved from uts/src/testFixtures
```

Consumed uniformly:

```kotlin
// uts/build.gradle.kts, liveobjects/build.gradle.kts, java/build.gradle.kts, chat/…
testImplementation(project(":test-support"))
```

`:uts` drops the `java-test-fixtures` plugin and its `testFixtures*` dependency block; the
`testFixtures(project(":uts"))` line in `:liveobjects` becomes `project(":test-support")`.

## 3. The hard constraint — Kotlin infra vs. Java consumers ⚠️

**This is the single biggest consideration and the reason the extraction is non-trivial.**

The infra is **idiomatic Kotlin**: lambda-with-receiver builder DSLs (`MockWebSocket { onConnectionAttempt = … }`),
`suspend` functions (`setupSyncedChannel`, `SandboxApp.create`, `awaitState`), extension functions,
`data class`es, and default arguments.

`lib/src/test` is **pure Java** (verified: 75 `.java` files, 0 `.kt`). Java can put compiled Kotlin on its
classpath, but **cannot ergonomically call this API**:

- `suspend` functions are effectively uncallable from Java (they compile to a hidden `Continuation`
  parameter) — `setupSyncedChannel`, `SandboxApp.create()`, the `await*` helpers.
- Lambda-with-receiver config DSLs don't exist in Java.
- Extension functions become awkward static calls; default args require `@JvmOverloads` to be visible.

**Decision (confirmed): add a Kotlin test source set to `:java`** and write the Java module's new
UTS-derived tests in Kotlin. The infra is Kotlin-first by design and UTS tests are Kotlin, so a Java
facade (`@JvmStatic`/`@JvmOverloads`/blocking `suspend` wrappers/builder classes) would fight the grain;
it's the rejected alternative. Existing Java tests in `lib/src/test/java` are untouched.

(The Chat SDK is Kotlin, so it has no such friction — only `lib/src/test` does.)

### 3.1 Concrete `:java` wiring

`:java` is today a **pure-Java `java-library`** (build file `java/build.gradle.kts`; `sourceCompatibility`/
`targetCompatibility = 1.8`; sources wired from `../lib/src/…/java` via `sourceSets { … srcDirs(…) }`). It
does **not** apply the Kotlin plugin. Steps:

1. Apply the Kotlin JVM plugin: `alias(libs.plugins.kotlin.jvm)` in `java/build.gradle.kts` `plugins {}`.
2. Add a Kotlin test source dir mirroring the existing Java wiring, and create the folder:
   ```kotlin
   sourceSets {
       named("test") {
           java { srcDirs("src/test/java", "../lib/src/test/java") }   // existing
           // new — Kotlin UTS tests + consumed infra
           kotlin { srcDirs("src/test/kotlin", "../lib/src/test/kotlin") }
       }
   }
   ```
   (`mkdir -p lib/src/test/kotlin`.)
3. `testImplementation(project(":test-support"))` + the `--add-opens` JVM args (§5 step 4) +
   `kotlin("test")` as needed.

### 3.2 ⚠️ Guardrail — do NOT ship kotlin-stdlib in the `:java` main artifact

`:java` is the **core, widely-consumed SDK artifact and is currently Kotlin-free at runtime**. Applying
`org.jetbrains.kotlin.jvm` adds `kotlin-stdlib` to the `implementation` (main) configuration by default,
which would leak into the published artifact's runtime dependencies — every ably-java consumer would then
pull kotlin-stdlib. **This must be prevented.** Approach:

- Disable the automatic stdlib dependency for this module and add stdlib to **test scope only**, e.g. set
  `kotlin.stdlib.default.dependency=false` and declare `testImplementation(kotlin("stdlib"))` (verify the
  flag's scope — if `gradle.properties` is project-wide, other Kotlin modules like `:liveobjects` that
  *do* ship Kotlin still need stdlib in main, so prefer a per-module control or an explicit main-scope
  stdlib there).
- **Acceptance check:** after wiring, inspect the published POM / `./gradlew :java:dependencies
  --configuration runtimeClasspath` and confirm **no `kotlin-stdlib`** on `:java`'s main runtime classpath.
- Align the Kotlin `jvmTarget` with `:java`'s Java 8 target (`compileTestKotlin { compilerOptions.jvmTarget
  = JVM_1_8 }`) so test bytecode matches.

This guardrail is the main reason the `:java` change is more than a one-liner — treat the "stdlib stays out
of main" verification as a required gate of the step.

## 4. Dependency & cycle analysis

The infra's actual dependencies (from the current `testFixtures` block — keep these exact scopes):

| Dep | Scope | Why |
|---|---|---|
| `:java` | `api` | `AblyRealtime`/`AblyRest`/`ProtocolMessage`/`Clock`/`ConnectionDetails` appear in fixture signatures |
| `:network-client-core` | `api` | `HttpEngine`/`WebSocketEngine` SPI the mocks implement, in signatures |
| `libs.coroutine.core` | `implementation` | `suspend` helpers |
| `libs.ktor.client.core` + `libs.ktor.client.cio` | `implementation` | `SandboxApp` / `ProxySession` HTTP |

**Cycle safety:**

- `:test-support` main → `:java` main, `:network-client-core` main. Fine.
- **INVARIANT (carried over from `MOVE_COMMON_INFRA` Phase 1): `:test-support` must never depend on
  `:liveobjects`.** That keeps `:liveobjects:test → :test-support → :java` acyclic against
  `:uts`'s existing `testRuntimeOnly(:liveobjects)`.
- `:java:test → :test-support → :java:main` is **not** a cycle — a module's *test* compilation may depend
  on a module that depends on its *main*. (Gradle treats `main` and `test` as separate nodes.) Confirm on
  first `:java:compileTestKotlin`/`compileTestJava`.

## 5. Migration steps (when triggered)

1. **Create `:test-support`**: new dir, `settings.gradle.kts` `include("test-support")`, `build.gradle.kts`
   applying `kotlin("jvm")` with the §4 deps as `api`/`implementation` (NOT `testFixtures*` scopes — this is
   a normal module now).
2. **Move the tree**: `git mv uts/src/testFixtures/kotlin/io/ably/lib/uts/infra` →
   `test-support/src/main/kotlin/io/ably/lib/testsupport` (rename package `io.ably.lib.uts.infra` →
   `io.ably.lib.testsupport` — do this now, while there's one Kotlin consumer, not later). Update imports in
   `:uts` tests and `:liveobjects`'s `uts/unit/Helpers.kt`.
   - Alternative: keep the `io.ably.lib.uts.infra` package to avoid import churn. Weigh churn vs. a
     misleading `uts` name in a shared module. Package rename is a mechanical find/replace across two
     consumers today; it gets more expensive with every new consumer, so **prefer renaming now**.
3. **Rewire consumers**:
   - `:uts` — remove `java-test-fixtures` plugin + `testFixtures*` block; add
     `testImplementation(project(":test-support"))`. Restore the plain `plugins { alias(libs.plugins.kotlin.jvm) }`.
   - `:liveobjects` — `testImplementation(testFixtures(project(":uts")))` → `testImplementation(project(":test-support"))`.
4. **JVM args parity**: consumers running the mock transport need the same
   `--add-opens java.base/java.time=ALL-UNNAMED` / `java.base/java.lang=ALL-UNNAMED` flags that
   `uts/build.gradle.kts` and `liveobjects/build.gradle.kts` already set. Add to any new consumer's
   `tasks.withType<Test>` (needed by the coroutines/FakeClock machinery, not by the `ConnectionDetails`
   reflection — that targets a plain classpath class and needs no `--add-opens`).
5. **Proxy system property**: the `uts.proxy.localPath` / `UTS_PROXY_LOCAL_PATH` forwarding in
   `uts/build.gradle.kts`'s `tasks.withType<Test>` must be replicated by any module that runs proxy-tier
   fixtures. Consider extracting it into a shared Gradle convention/snippet at that point. (`:liveobjects`
   does not run proxy fixtures today, so it doesn't need this yet.)
6. **`:java` consumption** (when `lib/src/test` is a driver): follow §3.1 (apply `kotlin("jvm")`, add the
   `../lib/src/test/kotlin` source dir) **and §3.2 (keep kotlin-stdlib out of the `:java` main artifact —
   required gate)**. Existing Java tests are unaffected.
7. **Docs**: update the three places that describe infra location — `uts/README.md` §4/§4.2/Appendix B,
   `.claude/skills/uts-to-kotlin/SKILL.md` (Step 3 infra paths, Step 3 objects/unit note, integration
   Infrastructure section), and `.claude/skills/uts-to-kotlin/references/objects-mapping.md` §13. All
   currently say `uts/src/testFixtures/kotlin/io/ably/lib/uts/infra/…`.

## 6. Cross-repo caveat (Chat)

If the Chat SDK lives in a **separate repository** (not a module in this monorepo), "sharing" is not a
`project(":test-support")` dependency — it requires **publishing** `:test-support` as a versioned artifact
(Maven coordinates, `maven-publish`, a release cadence). That is a materially bigger commitment than an
in-repo module and turns test infra into a maintained public-ish artifact. Confirm whether chat is:

- **In-repo** (a future module here) → plain `project(":test-support")`, this doc's plan as written; or
- **Separate repo** → decide publish-and-version vs. source-copy vs. a shared git submodule. Do **not**
  assume publishing without an explicit decision — it changes ownership, versioning, and CI.

## 7. What NOT to do (rejected alternatives, for the record)

- **Move infra to `src/main` of `:uts`** — makes it part of a publishable main artifact, drags
  mockk/ktor into non-test scopes, and `:uts` stops being self-evidently test-only.
- **Move infra back to `src/test`** — invisible to other modules; breaks the `:liveobjects` consumption
  that `MOVE_COMMON_INFRA` established.
- **Rename `src/testFixtures` → `src/testInfra` while keeping `java-test-fixtures`** — the plugin's source
  set stays named `testFixtures` and the consumer accessor stays `testFixtures(project(":uts"))`, so you
  get a directory/source-set name mismatch that is *more* confusing than the convention. A custom-named
  consumable test source set means hand-rolling the Gradle variant + capability (fragile). The dedicated
  module in §2 is the only clean way to get a chosen name.

## 8. Related future work — simplify `uts-package-mapping.json` to one full path per tier

**Motivation.** The skill's `.claude/skills/uts-to-kotlin/uts-package-mapping.json` today uses a **hybrid**
schema: a global relative `testRoot` (`uts/src/test/kotlin/io/ably/lib/uts`) with each tier a string
*relative to it*, **except** tiers that live in another module, which carry an explicit `{root, path}`
override (this is what `objects.unit` → `:liveobjects` needed). The resolver
(`scripts/resolve_uts.py`) branches on string-vs-object to handle both.

That special-casing is fine for one out-of-`:uts` tier, but **it multiplies as tiers land in more
modules** — which is exactly what this doc's extraction (`:test-support`) and the `:java`/chat consumers
bring. Every future "this tier lives in module X" becomes another `{root, path}` object and keeps the
resolver's two-form branch alive.

**Proposed change.** Drop the global `testRoot` and the `{root, path}` object form; make **every tier a
single full, repo-root-relative path string**. Uniform, no special-casing, self-describing:

```jsonc
// before (hybrid)
{
  "testRoot": "uts/src/test/kotlin/io/ably/lib/uts",
  "packages": {
    "realtime": { "unit": "unit/realtime", "integration": "integration/standard/realtime", "proxy": "integration/proxy/realtime" },
    "objects":  { "unit": { "root": "liveobjects/src/test/kotlin/io/ably/lib/liveobjects", "path": "uts/unit" },
                  "integration": "integration/standard/liveobjects", "proxy": "integration/proxy/liveobjects" }
  }
}

// after (one full path per tier)
{
  "packages": {
    "realtime": {
      "unit":        "uts/src/test/kotlin/io/ably/lib/uts/unit/realtime",
      "integration": "uts/src/test/kotlin/io/ably/lib/uts/integration/standard/realtime",
      "proxy":       "uts/src/test/kotlin/io/ably/lib/uts/integration/proxy/realtime"
    },
    "objects": {
      "unit":        "liveobjects/src/test/kotlin/io/ably/lib/liveobjects/uts/unit",
      "integration": "uts/src/test/kotlin/io/ably/lib/uts/integration/standard/liveobjects",
      "proxy":       "uts/src/test/kotlin/io/ably/lib/uts/integration/proxy/liveobjects"
    }
  }
}
```

**Resolver change** (`scripts/resolve_uts.py`): delete the `testRoot` read and the `isinstance(dict)`
branch; `target_dir = entry[tier]` directly. `package_for()` is unchanged — it already derives the Kotlin
package by splitting each path on `src/test/kotlin/`, which every full path still contains. `--create` emits
full-path entries (its `unit`/`integration`/`proxy` template becomes the three full paths for the chosen
module). Update the `_comment` accordingly.

> **⚠️ "Absolute" must mean repo-root-relative, NOT machine-absolute.** Do **not** put
> `/Users/<name>/IdeaProjects/ably-java/...` in this committed file — a machine-specific path breaks CI,
> every other developer, and the skill (which resolves paths from the ably-java repo root). "Full path"
> here = the complete path **from the repo root**, exactly as the values above.

**Trade-off.** More verbose (each tier repeats the `uts/src/test/kotlin/io/ably/lib/uts/...` prefix; the
shared-`testRoot` DRY is lost) in exchange for a uniform, branch-free schema that scales cleanly to
tiers in arbitrary modules. Given the direction (infra + tiers spreading across `:uts` / `:liveobjects` /
`:java` / chat), the uniformity wins. Do this **together with** the §7 doc-path updates so the mapping,
`SKILL.md` Step B/2/5/6, and `objects-mapping.md` §13 all describe the single-form schema at once.

## 9. References

- `MOVE_COMMON_INFRA/` — the completed 5-phase consolidation this defers from (esp. Phase 1 fixtures
  extraction and its cycle invariant).
- `uts/README.md` §4.2 "Cross-module exception" — current infra location + the objects-unit-in-`:liveobjects` split.
- `.claude/skills/uts-to-kotlin/SKILL.md` Step 3 + `references/objects-mapping.md` §13/§17 — how the skill
  points at the infra and the objects/unit destination.
- Current infra deps: `uts/build.gradle.kts` (`testFixturesApi`/`testFixturesImplementation` block).
