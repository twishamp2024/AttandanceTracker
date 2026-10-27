# attendance-core

A pure Kotlin/JVM domain engine and mathematics library for class attendance tracking, threshold calculations, and hypothetical simulations.

`attendance-core` contains zero Android dependencies, zero UI dependencies, and performs all ratio comparisons using exact integer basis-point arithmetic to eliminate floating-point precision drift.

---

## Maven Coordinates

- **Group ID**: `io.github.twishamp2024`
- **Artifact ID**: `attendance-core`
- **Version**: `0.1.0`
- **Package Registry**: GitHub Packages Maven Registry (`https://maven.pkg.github.com/twishamp2024/AttandanceTracker`)

---

## Features

- **Exact Percentage Representation (`Percent`)**:
  Stores percentages in basis points (1/100th of 1%, so 75% = 7,500 bp). Ratios like 39/52 are evaluated in exact `Long` integer cross-multiplication, avoiding classic IEEE-754 floating-point inaccuracies.
- **Attendance Tallying (`AttendanceCounts`)**:
  Applies definitive status rules:
  - `PRESENT`: counts as attended and conducted.
  - `ABSENT`: counts as conducted only.
  - `EXCUSED`: configurable via `ExcusedPolicy` (`IGNORE` vs `COUNT_AS_PRESENT`).
  - `CANCELLED`: never counted in numerator or denominator.
  - `NOT_MARKED`: excluded from conducted calculations (pending marks).
  - Extra classes tracked and counted in conducted sessions.
- **Attendance Mathematics (`AttendanceCalculator`)**:
  - `meetsRequirement`: exact comparison against requirement threshold.
  - `safeMisses`: calculates maximum consecutive future classes affordable before falling below requirement.
  - `classesNeededToReach`: calculates minimum consecutive future attendances required to reach a target.
  - `health`: classifies status into `NO_DATA`, `SAFE`, `CAUTION`, or `CRITICAL`.
  - `trend`: detects whether recent attendance over a sliding window is `IMPROVING`, `STEADY`, or `DECLINING`.
- **What-If Simulation Engine (`WhatIfSimulator`)**:
  Pure simulation projections without state mutations:
  - `project`: test arbitrary combinations of attended and missed future sessions.
  - `missStreak`: project consequence of missing the next 1..N sessions.
  - `attendStreak`: project consequence of attending the next 1..N sessions.
- **Eligibility Rules (`AttendanceEligibility`)**:
  Determines whether attendance marking is unlocked (starting from session start time on its date, with no closing cutoff).

---

## Requirements

- **Kotlin**: 2.0+
- **JVM**: Java 17+ (compatible with Android API 26+ or any standard JVM application)

---

## Consuming via GitHub Packages

### 1. GitHub Packages Authentication

GitHub Packages requires authenticated requests even for public packages. Generate a GitHub Personal Access Token (classic) with the `read:packages` scope (or use a fine-grained PAT with Packages read permissions).

Export your credentials as environment variables:

```bash
export GITHUB_ACTOR="<your-github-username>"
export GITHUB_TOKEN="<your-github-token-with-read:packages>"
```

Alternatively, configure them in `~/.gradle/gradle.properties`:

```properties
gpr.user=<your-github-username>
gpr.key=<your-github-token-with-read:packages>
```

### 2. Configure Repository in Gradle

In `settings.gradle.kts` (or root `build.gradle.kts`):

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

### 3. Add Dependency

In your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.twishamp2024:attendance-core:0.1.0")
}
```

---

## Building and Testing Locally

Run all unit tests:

```bash
./gradlew :attendance-core:test
```

Publish to your local Maven cache (`~/.m2/repository/`):

```bash
./gradlew :attendance-core:publishToMavenLocal
```

Publish to GitHub Packages (requires `write:packages` scope):

```bash
./gradlew :attendance-core:publishMavenJavaPublicationToGitHubPackagesRepository \
    -Pgpr.user="<your-github-username>" \
    -Pgpr.key="<your-token-with-write:packages>"
```
