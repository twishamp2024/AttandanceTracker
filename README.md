# Attendance

A personal, offline-first college attendance tracker for Android.

Open the app after a class, tap **Present**, done. Everything else — percentages,
how many classes you can still afford to miss, how many you must attend to climb
back over the line — is derived from that one tap.

---

## Contents

- [What it does](#what-it-does)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Reusable Kotlin Library (`attendance-core`)](#reusable-kotlin-library-attendance-core)
- [Attendance calculation rules](#attendance-calculation-rules)
- [Database structure](#database-structure)
- [Setup](#setup)
- [Building](#building)
- [Installing on a phone](#installing-on-a-phone)
- [Testing](#testing)
- [Export and import format](#export-and-import-format)
- [Privacy](#privacy)
- [Known limitations](#known-limitations)

---

## What it does

**Today** — the classes scheduled for today, with the one that is happening now (or
next) promoted into a large card carrying **Present** and **Absent** buttons. Marking
a class is a single tap; cancelled, excused and notes live one tap deeper.

**Overview** — pooled attendance across the semester, then a card per subject showing
the percentage, attended/conducted, the subject's own requirement, a status, and the
sentence that actually matters: *"You can miss 3 more classes"* or *"Attend the next
7 classes to reach 75%."*

**Timetable** — the weekly plan, day by day. Sessions for real dates are generated
from it automatically.

**Calendar** — a month grid where each day carries a dot summarising how it went.
Tapping a day lists its classes; any past record can be corrected.

**Subject detail** — full statistics, a running-attendance chart against the
requirement line, targets above the requirement (80/85/90%), the complete history,
and a **What if?** simulator that projects the effect of attending or missing future
classes without ever touching your data.

**Settings** — theme, how excused absences are counted, the caution threshold,
optional daily reminder, semester management, JSON/CSV export, validated import,
demo data, and a full reset.

---

## Technology stack

Chosen after inspecting what the machine already had, and pinned to current stable
releases.

| Layer | Choice | Version |
|---|---|---|
| Language | Kotlin | 2.4.20 (via AGP's built-in Kotlin) |
| Build | Gradle / Android Gradle Plugin | 9.8.0 / 9.4.1 |
| UI | Jetpack Compose + Material 3 | Compose BOM 2026.09.00 |
| Navigation | Navigation Compose | 2.10.2 |
| Persistence | Room + KSP | 2.8.5 / KSP 2.3.12 |
| Async | Coroutines + Flow | 1.11.0 |
| Serialization | kotlinx.serialization | 1.10.0 |
| Tests | JUnit4, Robolectric, coroutines-test | 4.13.2 / 4.16 |
| JDK | Android Studio's bundled JBR | 21 |
| SDK | compileSdk 37.2, targetSdk 36, minSdk 26 | |

Notes on a few of these:

- **AGP 9 supplies Kotlin itself.** Applying `org.jetbrains.kotlin.android` alongside
  it is now an error, so the app module does not declare it.
- **No DI framework.** One module, one database, roughly a dozen collaborators —
  `AppContainer` with `by lazy` does the job in ~50 lines without an extra annotation
  processor. See `di/AppContainer.kt`.
- **No charting library.** The trend chart is one Compose `Canvas`; it inherits theme
  colours and costs no dependency.
- **minSdk 26** gives `java.time` natively, with no core-library desugaring.
- **targetSdk 36, not 37.** compileSdk is 37 so the newest APIs compile, but opting
  in to a platform's new *runtime* behaviours is a decision to make after testing on
  that platform. The corresponding lint check is disabled with that reasoning in
  `app/build.gradle.kts`.

---

## Architecture

Unidirectional data flow, three layers, dependencies pointing inward.

```
  ui/          Compose screens + ViewModels
               Reads immutable UiState, emits events. Does no arithmetic.
                 |
                 v
  domain/      Models, attendance mathematics, use cases, repository interfaces.
               Pure Kotlin. No Android imports. Fully unit-testable.
                 ^
                 |
  data/        Room entities, DAOs, repository implementations, backup, sample data.
               Implements the domain's interfaces.
```

The rule that earns its keep: **attendance arithmetic never appears in a composable.**
`AttendanceCalculator`, `AttendanceCounts` and `WhatIfSimulator` are plain objects
with no dependencies, which is why the edge cases can be tested exhaustively in
milliseconds instead of through the UI.

State flows one way: Room `Flow` → repository → use case → `StateFlow<UiState>` →
Compose. Writes go straight to Room and the UI re-renders from the resulting Flow, so
the screen can never show a mark that failed to save.

Key files:

| Path | Role |
|---|---|
| `domain/attendance/AttendanceCalculator.kt` | Percentages, safe misses, classes needed, status |
| `domain/attendance/AttendanceCounts.kt` | Which statuses count, and which do not |
| `domain/attendance/WhatIfSimulator.kt` | Projections; structurally unable to write |
| `domain/model/Percent.kt` | Basis-point percentage, exact integer comparisons |
| `domain/usecase/AttendanceUseCases.kt` | Joins subjects to sessions, one place only |
| `data/repository/SessionRepositoryImpl.kt` | Timetable → session materialisation |
| `data/backup/BackupManager.kt` | Export, and validate-then-import |
| `di/AppContainer.kt` | Manual dependency container |

---

## Reusable Kotlin Library (`attendance-core`)

The attendance calculation engine, percentage representation, safe-miss calculations, what-if simulator, and eligibility logic have been extracted into a standalone, reusable Kotlin/JVM library module: `:attendance-core`.

The Android application (`:app`) consumes `:attendance-core` directly as a Gradle dependency (`implementation(project(":attendance-core"))`), ensuring a single source of truth without duplicating calculation logic.

### Maven Coordinates

| Field | Value |
|---|---|
| **Group ID** | `io.github.twishamp2024` |
| **Artifact ID** | `attendance-core` |
| **Version** | `0.1.0` |
| **Packaging** | `jar` (with companion `-sources.jar` and `-javadoc.jar`) |
| **Target Runtime** | Kotlin 2.0+, JVM (Java 17+), Android API 26+ |
| **Repository** | GitHub Packages Maven Registry |
| **Registry URL** | `https://maven.pkg.github.com/twishamp2024/AttandanceTracker` |

### Key Modules & Capabilities

- **`Percent`** (`com.twisham.attendance.core.Percent`): Basis-point (1/10,000) exact integer representation. Prevents floating-point boundary errors when comparing ratios against required percentages (e.g., 39/52 vs 75%).
- **`AttendanceCalculator`** (`com.twisham.attendance.core.AttendanceCalculator`):
  - `meetsRequirement`: Exact integer ratio comparison.
  - `safeMisses`: Number of consecutive future classes affordable before dropping below requirement.
  - `classesNeededToReach`: Minimum consecutive attendances required to reach target percentage.
  - `health`: Traffic-light classification (`SAFE`, `CAUTION`, `CRITICAL`, `NO_DATA`).
  - `trend`: Sliding-window attendance trajectory (`IMPROVING`, `STEADY`, `DECLINING`).
- **`AttendanceCounts`** (`com.twisham.attendance.core.AttendanceCounts`):
  - Pure tallies across `PRESENT`, `ABSENT`, `EXCUSED`, `CANCELLED`, and `NOT_MARKED`.
  - Configurable `ExcusedPolicy` (`IGNORE` vs `COUNT_AS_PRESENT`).
  - Strict cancellation rule: cancelled classes never enter the denominator.
- **`WhatIfSimulator`** (`com.twisham.attendance.core.WhatIfSimulator`):
  - Pure projections for hypothetical future attendances and absences (`project`, `missStreak`, `attendStreak`).
  - Zero state mutations and zero database access.
- **`AttendanceEligibility`** (`com.twisham.attendance.core.AttendanceEligibility`):
  - Evaluates whether session marks can be submitted (available from start time on session date, with no closing cutoff).

### How to Consume the Library

#### 1. GitHub Packages Authentication

GitHub Packages requires authenticated requests. Generate a GitHub Personal Access Token (classic) with the `read:packages` scope (or fine-grained PAT with package read access).

Provide the credentials via environment variables:

```bash
export GITHUB_ACTOR="<your-github-username>"
export GITHUB_TOKEN="<your-github-token-with-read:packages>"
```

Or in your global `~/.gradle/gradle.properties`:

```properties
gpr.user=<your-github-username>
gpr.key=<your-github-token-with-read:packages>
```

#### 2. Configure Gradle Repository

In the consuming project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/twishamp2024/AttandanceTracker")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: (findProperty("gpr.user") as? String)
                password = System.getenv("GITHUB_TOKEN")
                    ?: (findProperty("gpr.key") as? String)
            }
        }
    }
}
```

#### 3. Add Dependency

In the consumer's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.twishamp2024:attendance-core:0.1.0")
}
```

### Local Development and Publishing

- **Run unit tests**:
  ```bash
  ./gradlew :attendance-core:test
  ```
- **Publish to local Maven cache (`~/.m2/repository`)**:
  ```bash
  ./gradlew :attendance-core:publishToMavenLocal
  ```
- **Publish to GitHub Packages** (requires token with `write:packages` scope):
  ```bash
  ./gradlew :attendance-core:publishMavenJavaPublicationToGitHubPackagesRepository \
      -Pgpr.user="twishamp2024" \
      -Pgpr.key="<GITHUB_TOKEN_WITH_WRITE_PACKAGES>"
  ```

---

## Attendance calculation rules

These are the rules the whole app rests on. They are documented here, in
`AttendanceCounts.kt`, and surfaced to the user on the Overview screen.

### What counts

| Status | Counts as attended | Counts as conducted |
|---|---|---|
| `PRESENT` | yes | yes |
| `ABSENT` | no | yes |
| `EXCUSED` | only if policy = *Count as present* | only if policy = *Count as present* |
| `CANCELLED` | **no** | **no** |
| `NOT_MARKED` | no | no |

- **Cancelled classes never count.** A class that did not happen must not be held
  against you. Counting cancellations as conducted is the most common bug in
  attendance apps; the table above is why it cannot happen here.
- **Unmarked classes never count.** Silence means "not answered yet", not "absent".
- **Extra classes count like any other class.** `SessionKind.EXTRA` raises conducted,
  and attended if you were present.
- **Excused is a policy**, because institutions genuinely differ. Default is
  *Don't count at all* — removed from both sides of the fraction.

### The formulas

With `A` = attended, `C` = conducted, `R` = requirement in basis points
(75% = 7500), all arithmetic in exact `Long`:

**Percentage**

```
A / C,  undefined when C = 0   (0/0 is unknown, not 0%)
```

**Meets requirement** — cross-multiplied so a student sitting exactly on 75% is
never told they are below it:

```
A × 10000 ≥ R × C
```

**Safe misses** — the largest `k ≥ 0` you can miss consecutively and stay at or above
the requirement:

```
k = floor( (A × 10000 − R × C) / R ),  clamped to ≥ 0
```

**Classes needed** — the smallest `n ≥ 0` you must attend consecutively to reach a
target `T`:

```
n = ceil( (T × C − A × 10000) / (10000 − T) ),  clamped to ≥ 0
```

Unreachable only when `T = 100%` and a class has already been missed: past absences
stay in the denominator forever, so the ratio approaches but never reaches 1. The UI
says so explicitly rather than showing a blank.

**Worked examples** (all covered by tests):

| A / C | Requirement | Safe misses | Classes needed |
|---|---|---|---|
| 38 / 50 (76.0%) | 75% | 0 | 0 |
| 40 / 52 (76.9%) | 75% | 1 | 0 |
| 48 / 52 (92.3%) | 75% | 12 | 0 |
| 35 / 50 (70.0%) | 75% | 0 | 10 |
| 35 / 49 (71.4%) | 75% | 0 | 7 |

### Status bands

| Band | Meaning |
|---|---|
| **Safe** | At or above requirement with margin to spare |
| **Caution** | At or above requirement, but `safeMisses ≤ threshold` (default 1) |
| **Critical** | Below requirement |
| **No data** | Nothing conducted yet |

The caution threshold is a setting, not a magic number — how much margin feels
comfortable is a personal judgement.

### Overall attendance

**Pooled**: total attended across active subjects ÷ total conducted. Not the mean of
per-subject percentages, which would weight a 2-class seminar the same as a 40-class
lecture course. Because subjects can carry different requirements, a single overall
figure cannot say whether you are safe, so the counts of safe/caution/critical
subjects are always shown beside it.

### Precision

Requirements are held as **basis points** (integers), never `Double`. Every
comparison is cross-multiplied into `Long`. Rounding to a displayed percentage is
half-up and happens only at the display boundary. This is what makes "exactly 75%"
behave correctly instead of failing by a floating-point hair.

---

## Database structure

Room, schema version 1, exported to `app/schemas/`.

| Table | Purpose |
|---|---|
| `semesters` | Name, year, dates, default requirement, active/archived |
| `subjects` | Name, code, teacher, **own requirement**, type, colour, active |
| `timetable_slots` | Recurring weekly entries (day, start, end, room) |
| `class_sessions` | One real class on one real date — the unit of attendance |
| `settings` | Single row (id = 0) of user preferences |
| `materialised_days` | Days whose timetable has already been expanded |

Storage choices:

- Dates are **epoch days**, times are **second-of-day** — plain integers that sort
  and range-query in SQL and are timezone-free. A class on 25 Sep is on 25 Sep
  wherever the phone is.
- Enums are stored **by name**, not ordinal, so reordering an enum can never
  reinterpret existing records.
- Unknown enum values are read defensively: a bad row degrades to `NOT_MARKED`
  rather than making the semester unreadable.
- Unique index on `(subjectId, dateEpochDay, startSecond)` — the guard against
  duplicate sessions, which makes re-expansion idempotent.
- `slotId` on a session deliberately has **no** foreign key: deleting a timetable slot
  must not delete the attendance recorded against it. History outlives the plan.
- There is **no** `fallbackToDestructiveMigration`. The database is not a cache; an
  app that silently wipes a semester of attendance is worse than one that refuses to
  open. Future schema changes must ship a real migration.

### Session materialisation

The timetable is a *template*. Sessions are created for real dates lazily — a rolling
window around today, plus whatever month the calendar is showing — and every expanded
day is recorded in `materialised_days`. That gives three properties:

1. **Idempotent** — opening the same day twice never duplicates anything.
2. **Deletions stick** — a session you deleted is not resurrected next time.
3. **History is immutable** — editing the weekly timetable re-expands only from today
   forward. Changing your plan today never rewrites last month's attendance.

---

## Setup

Everything needed is already installed by the steps below or was present on this
machine. Nothing is required on any particular drive; Gradle caches live in
`~/.gradle` (~2 GB after the first build).

**Requirements**

- macOS (this project was built and verified on macOS 26.6, Apple Silicon)
- Android Studio (for its bundled JBR 21) or any JDK 21
- Android SDK with **platform 37.2** and **build-tools 37.0.0**

`local.properties` holds the machine-specific bits and is git-ignored:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
releaseStorePassword=...
releaseKeyAlias=attendance
releaseKeyPassword=...
```

`gradle.properties` pins the Gradle daemon to Android Studio's JBR 21, because the
system default JDK on this machine is Temurin 25, which AGP does not support. On a
different machine, update or remove `org.gradle.java.home`.

---

## Building

```bash
./gradlew assembleDebug
```

```bash
./gradlew assembleRelease
```

```bash
./gradlew test lint
```

Outputs:

| Build | Path | Size |
|---|---|---|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | ~20 MB (includes Compose tooling) |
| Release | `app/build/outputs/apk/release/app-release.apk` | ~1.9 MB (R8 shrunk, signed) |

Release builds are signed with `keystore/release.jks` using the credentials in
`local.properties`. Both the keystore and `local.properties` are git-ignored — **keep
a backup of them**. Losing them means a future build can no longer upgrade an already
installed copy of the app, only replace it (which wipes its data).

---

## Installing on a phone

**Over USB**

1. On the phone: Settings → About phone → tap **Build number** seven times to enable
   Developer options.
2. Settings → System → Developer options → enable **USB debugging**.
3. Connect the phone, accept the "Allow USB debugging?" prompt.
4. From this directory:

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
```

**Without a cable**

Copy `app-release.apk` to the phone (email it to yourself, AirDrop to Files, a USB
stick, whatever), open it in Files, and allow your file manager to install unknown
apps when prompted.

The app asks for no permissions on launch. It only requests notification permission
if and when you switch the daily reminder on.

---

## Testing

```bash
./gradlew test
```

94 tests across `:attendance-core` and `:app`, all pure JVM (Robolectric provides Android for the database tests, so no
device or emulator is needed).

| Module | Suite | Covers |
|---|---|---|
| `:attendance-core` | `PercentTest` | Basis-point conversion, exact ratios, half-up rounding, 0/0, formatting |
| `:attendance-core` | `AttendanceCalculatorTest` | 100%, 0%, exactly 75%, just below, safe misses and classes-needed verified against brute-force search, impossible 100% target, per-subject requirements, configurable caution band |
| `:attendance-core` | `AttendanceCountsTest` | Cancelled excluded, unmarked excluded, extra classes counted, both excused policies, pooling |
| `:attendance-core` | `WhatIfSimulatorTest` | Miss/attend streaks, order independence, remaining margin, negative input |
| `:attendance-core` | `AttendanceEligibilityTest` | Starting time eligibility, no closing time cutoff, past/future marks |
| `:app` | `DatabaseIntegrationTest` | Semester separation, editing history in place, cancel-after-the-fact, notes surviving status changes, idempotent materialisation, marks not overwritten, deletions not resurrected, duplicate rejection, cascade deletes, single-active-semester invariant, excused policy changing the reported figure |
| `:app` | `BackupTest` | Export→import round trip, merge vs replace, no duplication after import, rejection of foreign files, malformed JSON, newer format versions, unknown statuses, impossible dates, out-of-range percentages, CSV shape |
| `:app` | `DashboardAndSubjectScreenTest` | Dashboard rendering, subject card states, navigation integration |
| `:app` | `TodayScreenTest` | Markable state, one-tap actions, focus session |

The safe-miss and classes-needed tests do not just assert an expected number — they
replay the hypothetical to prove the boundary is exactly where the calculator says it
is, in both directions.

```bash
./gradlew lint
```

Lint is clean apart from one unavoidable informational hint (`mipmap-anydpi-v26`:
AAPT2 refuses an adaptive icon outside a `-v26` folder, so the folder cannot be
merged as lint suggests).

### Verified on a device

Beyond the automated suite, the **release** APK was installed on an Android 16
(API 36, arm64) emulator and driven through the real flows:

| Checked | Result |
|---|---|
| Fresh install → onboarding | Creates the first semester, then lands on Today |
| Add subject → add timetable slot | Session for today generated automatically |
| One-tap Present | Persisted; Undo offered; dashboard updated |
| Attendance maths on device | 1/1 → 100%, "Caution", "You cannot miss any more classes" |
| Correcting a past record | Present → Cancelled left the denominator: `0 / 0`, "—", not 0% |
| What-if simulator | 1/1 → miss 1 → 50.0%, and the database was provably unchanged |
| Demo data | 6 subjects, 118 classes, producing 3 Safe / 2 Caution / 1 Critical |
| Dark mode | Full repaint, all contrast preserved |
| Rotation to landscape | Bottom bar becomes a navigation rail; state preserved; no crash |
| Force-stop and relaunch | All data intact; cold start 201 ms (488 ms with data) |
| Whole session | Zero `FATAL EXCEPTION` and zero ANRs in logcat |

Two defects were found this way and fixed: a bright band behind the status bar in
landscape (the strip beside the inset navigation rail was unpainted), and two
near-identical grey dots in the calendar legend ("not marked" vs "all cancelled",
now distinguished by shape as well as shade).

---

## Export and import format

**JSON** (`Settings → Export backup`) is complete and restorable. Nested
semester → subject → timetable/sessions, so a subject can never be orphaned from its
semester. Dates are ISO-8601, times are `HH:mm`, enums are names, percentages are
plain numbers — all readable in any text editor.

```json
{
  "format": "college-attendance-tracker",
  "version": 1,
  "exported_at": "2026-09-25T18:30:00",
  "app_version": "1.0",
  "settings": {
    "theme_mode": "SYSTEM",
    "excused_policy": "IGNORE",
    "caution_safe_miss_threshold": 1,
    "trend_window_size": 6,
    "reminders_enabled": false,
    "reminder_time": "18:30",
    "week_starts_on": "MONDAY",
    "stretch_targets_percent": [80.0, 85.0, 90.0]
  },
  "semesters": [
    {
      "name": "Semester 5",
      "academic_year": "2026-27",
      "start_date": "2026-07-20",
      "end_date": "2026-11-30",
      "default_requirement_percent": 75.0,
      "is_active": true,
      "is_archived": false,
      "subjects": [
        {
          "name": "Database Management Systems",
          "code": "DBMS",
          "teacher": "Prof. S. Nair",
          "requirement_percent": 75.0,
          "type": "THEORY",
          "color_index": 1,
          "is_active": true,
          "timetable": [
            { "day_of_week": "TUESDAY", "start_time": "09:00",
              "end_time": "10:50", "room": "204", "is_active": true }
          ],
          "sessions": [
            { "date": "2026-09-22", "start_time": "09:00", "end_time": "10:50",
              "status": "PRESENT", "kind": "SCHEDULED", "note": null, "room": "204" }
          ]
        }
      ]
    }
  ]
}
```

**CSV** is a flat row per class for spreadsheets. Deliberately lossy — JSON is the
format that round-trips.

```csv
semester,academic_year,subject,subject_code,date,start_time,end_time,status,kind,note
Semester 5,2026-27,Database Management Systems,DBMS,2026-09-22,09:00,10:50,PRESENT,SCHEDULED,
```

**Import** parses and fully validates the file — every date, time, enum and
percentage, plus duplicate detection — *before writing a single row*, and then writes
inside one transaction. A malformed or truncated backup therefore cannot leave the
database half-imported: either it succeeds completely or nothing changed. You then
choose **Add alongside** (keeps existing data) or **Replace all** (deletes first, with
confirmation).

Import and export both go through the Storage Access Framework, so the app needs no
storage permission and the file lands wherever you chose.

---

## Privacy

- **No `INTERNET` permission.** Not declared at all, so the app cannot make a network
  request even if a dependency tried to.
- No analytics, ads, tracking, crash reporting or accounts.
- No location, contacts, camera, microphone or storage permissions.
- The only permissions declared are `POST_NOTIFICATIONS` (requested only when you
  switch the reminder on) and `RECEIVE_BOOT_COMPLETED` (to re-arm that reminder after
  a reboot).
- All data stays in the app's private database. It leaves the device only when you
  explicitly export it.

---

## Known limitations

- **Single device.** No sync. The export file is the way to move data between phones.
- **Reminders are inexact.** The daily nudge uses an inexact alarm so it needs no
  special permission; it may arrive a few minutes late. It only fires when classes
  are actually unmarked.
- **No recurring exceptions.** A public holiday has to be handled by marking that
  day's classes cancelled, rather than by a holiday calendar.
- **Rescheduling is a marker, not a move.** `SessionKind.RESCHEDULED` labels a
  session; moving a class to another day means editing or adding it.
- **No widget or quick-settings tile.** Marking still requires opening the app.
- **Single-module build.** Appropriate at this size; a multi-module split would pay
  off only if the codebase grew substantially.
- **Verified on Android 16 (API 36) and an emulator.** Not tested across the full
  device matrix, nor on tablets beyond the responsive layout switching to a
  navigation rail at ≥600dp.
