# Stage 14.6: Controlled Change Impact

## Method

Two independent implementations were made in isolated copies of the same `git archive HEAD` baseline, commit `dfb9704407b33641cd2cee13cbc43e0484c68cf4`. A third unchanged copy served as a compilation control. The source repository's existing untracked files were excluded. Only the evidence directory was added to the main repository; its production code, tests and build configuration were not edited. No emulator, device, commit or push was used.

Copies reside under the approved temporary directory in the unique folder `stage146-impact-20261028-2250`, with children `baseline`, `exercise-a`, and `exercise-b`. The existing `local.properties` was copied privately to each isolated copy for SDK discovery; it is not included in evidence. No Gradle configuration was changed. Manual implementation edits used `apply_patch`.

`measure_change_impact.py` compares actual Kotlin source files against the unchanged copy using `git diff --no-index --ignore-space-at-eol --numstat`. This excludes CRLF/LF-only noise introduced by patching Windows files. Added and deleted lines include comments and actual formatting edits. Changed lines means additions plus deletions, not unique original line positions. Subsystems are distinct first-level package directories under `ru/course/cityservices`; screen and component both belong to `ui`. Generated files, build outputs, local SDK configuration and evidence files are excluded from implementation counts. Absolute paths are stripped from saved patch headers. JSON contains per-file counts, patch hashes and the baseline source hash. These are measured edits, not estimates from dependency reachability.

Reproduction from the repository root, supplying the locally retained isolated root:

```powershell
python mob_curs_docs/stage1/extended-baseline/change-impact/measure_change_impact.py <isolated-root>
```

## Independent Results

| Exercise | Edited files | Added | Deleted | Changed lines | Net | Subsystems |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| A: optional locationHint | 7 | 43 | 5 | 48 | 38 | 5 |
| B: Rejected / Отклонена | 3 | 16 | 10 | 26 | 6 | 3 |

Do not combine the two rows as the cost of one change. They start from the same original baseline rather than from each other.

## Exercise A

Added nullable `locationHint: String? = null` to the shared API/Room request DTO and optional create API parameter. The form trims whitespace and converts an empty value to null. ServiceLocator carries it into the API and the optimistic local Room row. FakeApi preserves it in both successful creation and the existing server-accepted/client-timeout path. History renders either the hint or an explicit missing-hint label. Existing DAO insert/read operations use the shared entity and therefore need no edits. The standalone, unused `ServiceRequest` domain DTO is not on this application's request path and was deliberately not modified.

Room schema is version 2. Registered `MIGRATION_1_2` runs `ALTER TABLE requests ADD COLUMN locationHint TEXT`, preserving the table, keys and existing row values; old rows receive null. There is no destructive migration fallback. Scope is the existing version-1 schema to version 2, not arbitrary earlier or externally modified schemas. Data preservation is the intended SQL property, not an empirically verified runtime migration result.

Edited subsystems: `core`, `model`, `network`, `storage`, `ui`.

## Exercise B

Added `REJECTED` with title `Отклонена` to RequestStatus. Rejection is terminal; the happy-path `next()` still advances Created to Accepted. Domain allowed transitions now branch from Created to Accepted or Rejected. FakeApi consumes these rules: its existing 35% chance of advancing a request is retained, with a random allowed destination; Accepted and InProgress cannot reject, and Done/Rejected do not advance. StatusBadge explicitly displays rejection in red. String-based API/Room storage already accommodates this value, so no schema or DTO change is needed.

History filter options already use `RequestStatus.entries.map { it.title }` and filter by exact status title (`RequestsScreen.kt:96,104`). The new enum entry is therefore displayed and filterable without editing that file. This is source inspection and compilation evidence, not a device interaction test. A reachable dependency does not automatically count as an edited file.

Edited subsystems: `domain`, `network`, `ui`.

## Validation And Limits

All three isolated copies ran `gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon` with process-local `JAVA_HOME` pointing to the requested Azul 21.0.13 JDK and its `bin` prepended to PATH. All completed `BUILD SUCCESSFUL`, including KSP/Room generation, with seven executed tasks each. Gradle reported baseline 27 s, A 39 s, B 38 s. These single runs are validation observations, not a performance benchmark; A and B compiled concurrently.

All runs emit the existing Room warning that a schema export directory is not configured. Configuration was deliberately left unchanged. There was no runtime database opening/migration test, UI test, APK installation, emulator interaction or unit-test execution for these exercises. Compilation does not prove runtime persistence, migration correctness, filter interaction or random transition coverage.

Saved evidence: `exercise-a.patch`, `exercise-b.patch`, `measurements.json`, and the reproducible measurement script. Patches are normalized to LF; the archived baseline contains CRLF context in some files. Both passed `git apply --check --ignore-space-change <patch>` against the untouched isolated baseline. Plain `git apply --check` fails on those line-ending differences. Each patch is independently applicable with that whitespace option; do not apply one on top of the other for interpreting these measurements.
