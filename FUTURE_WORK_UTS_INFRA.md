# UTS shared test infra — decision record (supersedes the deferred extraction plan)

**Status: DECIDED and IMPLEMENTED (Phases 1–6, 2026-08-24 — staged for local review) — plan and
per-phase records in [`UTS_REFACTORING_PLAN/`](UTS_REFACTORING_PLAN/README.md). Phase 7 (publishing)
remains gated/unscheduled.**

This document originally proposed extracting the shared UTS infra out of `:uts`'s `testFixtures`
variant into a dedicated `:test-support` module, *deferred until a second consumer materialised*.
That trigger has been **deliberately pulled** (lead-dev decision, 2026-08-24), with one change of
target: instead of a new module, the infra is **promoted in place** to `:uts`'s main source set.
The full reasoning, phase breakdown, verification gates and CI re-pointing live in
`UTS_REFACTORING_PLAN/` — this file records what was decided, what changed versus the original
proposal, and where each original concern is addressed.

## 1. The decision (target state)

1. **Infra becomes a real source set:** `uts/src/testFixtures/kotlin/io/ably/lib/uts/infra/` →
   `uts/src/main/kotlin/io/ably/lib/uts/infra/`. Packages unchanged (`io.ably.lib.uts.infra.*` —
   zero import churn). `:uts` drops `java-test-fixtures`; consumers use plain
   `testImplementation(project(":uts"))`. This makes `:uts` importable from any module without
   ceremony, and publishable later without restructuring (`UTS_REFACTORING_PLAN/PHASE_1`, `PHASE_7`).
2. **UTS suites move to their owning modules** (per the updated
   `.claude/skills/uts-to-kotlin/uts-package-mapping.json` / `references/objects-mapping.md`):
   - realtime (and future rest) unit/integration/proxy → `:java`, at
     `lib/src/test/kotlin/io/ably/lib/uts/...` (packages unchanged) — `PHASE_4`;
   - objects integration/proxy → `:liveobjects`, at
     `liveobjects/src/test/kotlin/io/ably/lib/liveobjects/uts/{integration,proxy}` (joining the
     existing `uts/unit`) — `PHASE_3`.
3. **`:uts` keeps permanent, deep tier smoke tests** (one per tier — unit / integration / proxy),
   modeled on ably-cocoa PR #2223's `IntegrationSmokeTest` / `ProxyInfraSmokeTests`. They are the
   infra acceptance gate and the worked examples `uts/README.md` teaches from — `PHASE_2`.
4. **Mapping schema simplifies** to one full repo-root-relative path per tier (this doc's old §8,
   adopted), with the resolver additionally emitting the owning Gradle module derived from the path's
   first segment (`lib/` → `:java`) — `PHASE_5`.

## 2. What changed versus the original proposal, and why

| Original | Decided | Why |
|---|---|---|
| Extract to a new `:test-support` module, package-renamed to `io.ably.lib.testsupport` | Promote to `:uts`'s own `src/main`, packages unchanged | After the suites move out, `:uts`'s identity *is* the shared UTS test-support library — promoting in place needs no settings.gradle change and zero import churn (~30 consumer files), which was the point of the ask. The name `:uts` stays accurate (it ships UTS infra + UTS smoke examples). Extraction/rename remains a cheap later step if ever wanted. |
| "Do not do this extraction speculatively — wait for the second consumer" | Done now | The second consumer is no longer speculative: `:java` becomes a consumer the moment the realtime suites move into it (same refactor), and chat is anticipated. Bundling the infra promotion with the suite redistribution avoids doing the consumer wiring twice. |
| §7 rejected "move infra to `src/main` of `:uts`" | That rejection is withdrawn **for the new context only** | It was rejected because `:uts` was a test-host module whose main artifact would be accidental. Audit facts that made it clean now: no infra file imports any test library (kotlin.test/JUnit/mockk — verified across all 16 files); mockk was only ever a `:uts` test-scope dep (and is unused). The other §7 rejections (infra back to `src/test`; renamed testFixtures source set) stand. |

## 3. Original concerns → where addressed

Every concern from the previous version of this document is carried into the plan; none were dropped:

| Concern (old §) | Where addressed |
|---|---|
| Kotlin infra vs. pure-Java `lib/src/test` (§3) — Kotlin test sources for `:java`, no Java facade | `PHASE_4` steps 1–3. The no-facade decision stands; existing Java tests untouched. |
| Concrete `:java` wiring (§3.1) | `PHASE_4` step 1 (kotlin srcDirs on the existing `test` source set; `jvmTarget` 1.8), step 3 (dedicated Jupiter tasks — a refinement the original didn't have: `:java`'s 64 JUnit4 files and its `test-retry`/suite tasks stay on the JUnit4 runner untouched, no vintage engine). |
| ⚠️ kotlin-stdlib must not ship in `:java`'s main artifact (§3.2) | `PHASE_4` step 4 — **required merge gate** (runtimeClasspath + POM + jar-content checks), surgical per-module removal recommended, global `kotlin.stdlib.default.dependency=false` documented as fallback. Invariant I5 in the plan. |
| Dependency scopes & cycle analysis (§4) | `PHASE_1` step 2 preserves the exact scopes (`api(:java)`, `api(:network-client-core)`, `implementation(coroutines/ktor)`). Invariant I1: `:uts` **main** never depends on `:liveobjects`. The old test-runtime cycle concern dissolves in `PHASE_3` when `:uts` drops `testRuntimeOnly(:liveobjects)` entirely. |
| Migration steps §5.1–5.3 (module creation / tree move / consumer rewire) | `PHASE_1` steps 1–3 — no new module or `settings.gradle` entry needed; the move is `git mv` + two build files. |
| `:java` consumption (§5.6) | `PHASE_4` (the whole phase — wiring, deps, tasks, stdlib gate). |
| JVM-args parity (§5.4) and proxy sysprop replication (§5.5) | Plan invariant I6; applied in `PHASE_3` step 3 (`:liveobjects`) and `PHASE_4` step 3 (`:java`). The shared-convention idea is recorded (deferred) in `PHASE_4` §Notes. |
| Doc updates (§5.7) | `PHASE_5` (skill: SKILL.md, objects-mapping.md §13/§14, mapping json, resolver — audited line-by-line edit lists) + `PHASE_6` (uts/README.md rewrite, CLAUDE.md, module docs, repo-wide grep gates). |
| Cross-repo chat caveat (§6) — publishing is a commitment, not a default | `PHASE_7`, verbatim decision tree, explicitly gated and unscheduled. |
| Mapping simplification to one full path per tier (§8), incl. the "repo-root-relative, never machine-absolute" warning | `PHASE_5` §1–2, with the warning carried into the json `_comment`, plus the new `module` emission (`lib/` → `:java` being the non-obvious pair). |

## 4. New concerns the plan adds (not in the original)

- **No silent-green CI (I3):** `check.yml`'s `:uts:runUtsUnitTests` and `integration-test.yml`'s
  `check-uts` would keep passing on smoke tests while real coverage moved — every move phase
  re-points CI in the same PR (`PHASE_3` step 7, `PHASE_4` step 6).
- **JUnit Platform adoption:** `:liveobjects` flips wholesale (vintage engine for its 9 JUnit4
  files; count-parity gates), `:java` does **not** flip — isolation by task/engine instead
  (`PHASE_3` step 2, `PHASE_4` step 3).
- **Test-ID parity (I8):** `@UTS(...)` id sets must be identical before/after every move.
- **Skill freeze (I9):** `/uts-to-kotlin` must not run between Phase 3 and Phase 5.

## 5. References

- `UTS_REFACTORING_PLAN/README.md` — decisions D1–D8, invariants I1–I9, phase table, audit fact base.
- `MOVE_COMMON_INFRA/` — the completed prior consolidation whose invariants (fixture package
  stability, cycle rule, public-API test convention, one-green-PR-per-phase) this plan inherits.
- ably-cocoa PR #2223 — the smoke-test model for `PHASE_2`.
- `uts/README.md` — rewritten in `PHASE_6` to describe the target state.
