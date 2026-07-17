---
description: "Create a release branch, bump version, and update CHANGELOG. Usage: /release patch|minor|major"
allowed-tools: Bash, Read, Edit, Write
---

Read the current version from `gradle.properties` (the `VERSION_NAME` property).

The bump type is: $ARGUMENTS

Compute the new version by incrementing the appropriate component of the current version:
- `patch` — increment the third number, keep major and minor (e.g. 1.7.0 → 1.7.1)
- `minor` — increment the second number, reset patch to 0 (e.g. 1.7.0 → 1.8.0)
- `major` — increment the first number, reset minor and patch to 0 (e.g. 1.7.0 → 2.0.0)

Then perform these steps in order:

1. Run `git checkout -b release/NEW_VERSION`
2. Replace `OLD_VERSION` with `NEW_VERSION` in all of the following files:
   - `gradle.properties` — the `VERSION_NAME` property
   - `README.md` — 3 places: Maven `<version>`, Gradle `implementation '…'`, and LiveObjects `runtimeOnly("…")`
   - `CONTRIBUTING.md` — the `implementation files('libs/ably-android-OLD_VERSION.aar')` line
   - `lib/src/test/java/io/ably/lib/test/realtime/RealtimeHttpHeaderTest.java` — the hardcoded `"ably-java/OLD_VERSION"` string in the agent header assertion
   Commit all five files together with message: `chore: bump version to NEW_VERSION`
3. Fetch merged PRs since the last release tag using:
   ```
   gh pr list --state merged --base main --json number,title,mergedAt --limit 200
   ```
   Then get the date of the last release tag with:
   ```
   git log vOLD_VERSION --format="%aI" -1
   ```
   Filter the PRs to only those merged after that tag date. Format each as:
   ```
   - Short, one sentence summary from PR title and description [#NUMBER](https://github.com/ably/ably-java/pull/NUMBER)
   ```
   If the tag doesn't exist or there are no merged PRs, use a single `-` placeholder bullet instead.

4. In `CHANGELOG.md`, insert the following block immediately after the `# Change Log` heading (and its trailing blank line), before the first existing `## [` version entry:

```
## [NEW_VERSION](https://github.com/ably/ably-java/tree/vNEW_VERSION)

[Full Changelog](https://github.com/ably/ably-java/compare/vOLD_VERSION...vNEW_VERSION)

### What's Changed

BULLETS_FROM_STEP_3

```

5. Commit `CHANGELOG.md` with message: `docs: update CHANGELOG for NEW_VERSION release`

After completing all steps, show the user a summary of what was done. If PRs were found, list them. If the placeholder `-` was used instead, remind them to fill in the `### What's Changed` bullet points in `CHANGELOG.md` before merging.
