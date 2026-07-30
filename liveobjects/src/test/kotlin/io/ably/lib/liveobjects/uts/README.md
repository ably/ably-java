# UTS objects unit suite

Skill-generated tests for the UTS `objects/unit` specs (`/uts-to-kotlin`), one class per spec,
package `io.ably.lib.liveobjects.uts.unit`. Run: `./gradlew :liveobjects:runLiveObjectsUnitTests`.

- This suite lives in `:liveobjects`'s own test source set so the internal-graph specs can reach
  `internal` members — the symbol map is `.claude/skills/uts-to-kotlin/references/objects-mapping.md` §17.
- **Convention:** public-tier spec tests use only the public API + `unit/Helpers.kt`; only the five
  internal-graph specs (`internal_live_counter`, `internal_live_map`, `object_id`, `objects_pool`,
  `parent_references`) and documented deviations may reference `io.ably.lib.liveobjects` internals.
- Transport bootstrap comes from the shared `:uts` fixtures (`io.ably.lib.uts.infra.*`); message
  builders are the typed `Wire*` constructions in `unit/Helpers.kt`.
- Deviations are recorded in [`deviations.md`](deviations.md) (same discipline as
  `uts/src/test/kotlin/io/ably/lib/uts/deviations.md`).
