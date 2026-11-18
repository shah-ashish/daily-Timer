# Daily Timer ⏱️

A modern, high-energy time management and daily scheduling application built with **100% Native Kotlin**, **Jetpack Compose**, **Material 3**, and **SQLite**.

---

## 🚀 Overview

Daily Timer provides an intuitive, distraction-free environment for planning daily tasks, tracking active focus blocks in real time, and receiving timely reminder notifications.

### Core Features

- **Today's Focus (Home Screen)**:
  - Dynamic "Current Focus" hero card with live 30-second ticker updates.
  - Phase status indicators (`UPCOMING`, `IN_PROGRESS`, `OVERDUE`) computed dynamically against real-time clock.
  - Reactive SQLite change collection (`SharedFlow`) ensures the UI stays instantly up-to-date without stale polling.
  - Automatic midnight rollover recalculation and "All Caught Up" empty state when all daily tasks are completed.
- **Daily Planner (Tasks Screen)**:
  - Interactive date navigation bar with quick Day/Week hopping and a calendar picker.
  - Strict chronological task ordering powered by indexed `start_millis` timestamps.
  - One-tap task completion toggling and task deletion with immediate UI reflection.
- **Task Creation**:
  - Contextual date selection honoring the active planner view.
  - 12-hour time pickers with AM/PM support and strict start-before-end chronological validation.
  - Seamless navigation back to the scheduled task's date upon creation.
- **Smart Reminders & Alarm Scheduling**:
  - Reliable reminder alarms scheduled via Android `AlarmManager`.
  - Dynamic notification copy computed at trigger time (`"in X minutes"` vs `"starting now"`).
  - "Open Task" notification deep-linking that brings `MainActivity` to the foreground and focuses the target task.
  - Automatic boot rescheduling (`BootReceiver`) running asynchronously via `goAsync()`.
- **Settings & Theme Customization**:
  - Persistent Dark / Light appearance toggle honoring user preferences across app launches.
  - Configurable notification reminder lead time (clamped between 1 and 180 minutes) with keystroke-decoupled commit logic.
- **Cryptographically Verified In-App Self-Updater**:
  - Fully native Jetpack Compose updater card with animated states (`Check for Update` → `Download` → `Live Byte Progress` → `Install Now`).
  - Strict HTTPS endpoint enforcement and SHA-256 cryptographic checksum verification to prevent corrupted or tampered APK installations.
  - Connection leak prevention via OkHttp `.use {}` resource lifecycle management.
  - Preserved coroutine cancellation transparency (`CancellationException` propagation).
  - Explicit URI permission grants compatible with OEM package installers (Samsung, Xiaomi, Vivo, ColorOS).
- **Automated Unit Testing**:
  - Comprehensive JUnit test suite for `TaskTime` (case-insensitivity, NBSP/NNBSP tolerance, non-Gregorian locale safety) and `TaskPhaseResolver` state transitions.

---

## 📁 Project Architecture & Structure

```text
DailyTimer/
├── .github/
│   └── workflows/
│       ├── ci.yml                     # PR/Push: Lint, JUnit unit tests, Gradle build & debug APK artifact
│       └── release.yml                # Tag: Release build, dynamic keystore decode, SHA-256 generation & signed release APK
│
├── android/
│   ├── build.gradle.kts               # Root Gradle build script
│   ├── settings.gradle.kts            # Module registry
│   ├── gradle.properties              # JVM & AndroidX settings
│   │
│   └── app/
│       ├── build.gradle.kts           # App config, Compose BOM, OkHttp, test dependencies, secure signingConfigs
│       ├── keystore/
│       │   └── keystore.properties.example # Template for local uncommitted release credentials
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml       # Permissions, singleTop launchMode, receivers, and FileProvider
│           │   ├── res/                      # Drawables, strings, XML file provider paths
│           │   └── java/com/dailytimer/app/
│           │       ├── MainActivity.kt        # Root single-activity host, navigation, deep linking & theme
│           │       ├── data/
│           │       │   ├── db/
│           │       │   │   └── TaskDatabaseHelper.kt # SQLite v3 (start_millis index, singleton, SharedFlow)
│           │       │   ├── model/
│           │       │   │   ├── Task.kt        # Task entity
│           │       │   │   └── TaskStatus.kt  # UPCOMING, COMPLETED constants
│           │       │   └── preferences/
│           │       │       └── SettingsPreferences.kt # SharedPreferences (dark mode, reminder minutes)
│           │       ├── domain/
│           │       │   ├── TaskTime.kt        # Centralized java.time parsing, ISO format, NBSP tolerance
│           │       │   └── TaskPhase.kt       # TaskPhase enum and TaskPhaseResolver state machine
│           │       ├── notification/
│           │       │   ├── NotificationScheduler.kt  # Exact AlarmManager scheduler & LateReminderPolicy
│           │       │   ├── TaskNotificationReceiver.kt # BroadcastReceiver with goAsync() & dynamic copy
│           │       │   └── BootReceiver.kt           # Device boot rescheduling with goAsync()
│           │       ├── ui/
│           │       │   ├── create/CreateTaskScreen.kt # Task creation with validation
│           │       │   ├── home/HomeScreen.kt         # Today's Focus hero card & live countdown
│           │       │   ├── settings/SettingsScreen.kt # Appearance toggle & reminder lead time
│           │       │   ├── tasks/TasksScreen.kt       # Daily planner, date navigation, overdue badges
│           │       │   ├── theme/                     # Color palette, Typography, Material 3 Theme
│           │       │   └── updater/UpdateCard.kt      # Compose self-updater UI component
│           │       └── updater/
│           │           └── AppUpdater.kt      # Kotlin OkHttp Flow updater with SHA-256 verification
│           └── test/
│               └── java/com/dailytimer/app/domain/
│                   ├── TaskTimeTest.kt        # Unit tests: 12h time, NBSP, locale, sorting
│                   └── TaskPhaseResolverTest.kt # Unit tests: Upcoming, In-Progress, Overdue, Completed
│
├── version.json                       # Remote update manifest (versionCode, minSdk, sha256, downloadUrl)
├── .gitignore                         # Exclusions (*.jks, *.keystore, keystore.properties, specs)
├── agent.md                           # Core development instructions & constraints
└── coder.md                           # Architecture and setup guidelines
```

---

## 🛠️ Building & Running

### Prerequisites
- JDK 17
- Android SDK (compileSdk 34, minSdk 26)

### Build Commands (Local Terminal)
```bash
# Navigate to the android module directory
cd android

# Run Unit Tests & Linting
./gradlew testDebugUnitTest lintDebug

# Build Debug APK
./gradlew assembleDebug

# Build Release APK (unsigned locally unless keystore credentials are provided)
./gradlew assembleRelease
```

Generated APKs are output to:
- `android/app/build/outputs/apk/debug/app-debug.apk`
- `android/app/build/outputs/apk/release/app-release-unsigned.apk` (or signed when configured)

---

## 🔄 Self-Update Engine (`version.json`)

The application polls the remote `version.json` manifest:
```json
{
  "versionCode": 8,
  "versionName": "1.0.7",
  "minSdk": 26,
  "downloadUrl": "https://github.com/shah-ashish/daily-Timer/releases/download/v1.0.7/app-release.apk",
  "sha256": "90aa2da4fe6f6d54c9d47130b52c959da576d67428e6f46d399ea4628c331d1b",
  "releaseNotes": "v1.0.7: Package installer compatibility and stability improvements."
}
```

1. **Check**: Queries manifest over HTTPS and compares `versionCode` and `minSdk`.
2. **Download**: Emits byte-level streaming progress to the Compose UI.
3. **Verify**: Computes the downloaded file's running SHA-256 hash and validates it against the manifest checksum before staging.
4. **Install**: Triggers Android's `PackageInstaller` via `FileProvider` with URI permissions.
