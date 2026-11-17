# Daily Timer ⏱️

A high-energy, motivational time management app built with native **Kotlin**, **Tailwind CSS**, and **SQLite**, designed strictly following **Apple / iOS Human Interface Guidelines (HIG)**.

---

## 🚀 Overview (Task 1: Setup & Settings)

Daily Timer is crafted with an incremental, modular architecture. The initial setup provides a native Android shell hosting an iOS-styled Settings experience featuring an isolated, in-app self-updater engine.

### Core Features in Task 1
- **iOS HIG Settings Screen**:
  - **Appearance Switcher**: Instant Dark / Light mode toggle with stored user preference.
  - **Task Reminder Offset**: Pre-configured notification alert intervals (5m, 10m, 15m, 30m).
  - **iOS Tab Bar**: Bottom navigation with smooth blur effects and motivational accent highlights.
- **Independent In-App Updater Engine**:
  - Completely encapsulated Web Component (`<daily-timer-updater>`) and native Kotlin controller (`AppUpdater.kt`).
  - Queries `version.json` remote endpoint for release updates.
  - Interactive states: `Check for Update` → `Download vX.X.X` → **Live Byte Progress Bar** → `Install Now`.
  - Android `FileProvider` intent integration for direct APK package installation.
- **Security & CI/CD Pipeline**:
  - Dedicated `keystore.jks` signing to guarantee consistent app signatures across OTA updates.
  - GitHub Actions CI pipeline with Gradle caching for lint, unit tests, and debug APK artifact uploads.
  - GitHub Actions Release pipeline for generating and archiving signed release APKs.

---

## 📁 Project Structure

```text
DailyTimer/
├── .github/
│   └── workflows/
│       ├── ci.yml                     # PR/Push: Lint, test, Gradle cache & debug APK artifact
│       └── release.yml                # Tag: Release build, keystore decode & signed APK upload
│
├── android/
│   ├── build.gradle.kts               # Root Gradle build script
│   ├── settings.gradle.kts             # Module registry
│   ├── gradle.properties              # JVM & AndroidX settings
│   │
│   └── app/
│       ├── build.gradle.kts           # App config, dependencies, signingConfigs
│       ├── keystore/
│       │   ├── keystore.jks           # Dedicated signing certificate
│       │   └── keystore.properties.example
│       └── src/main/
│           ├── AndroidManifest.xml       # Internet, Install permissions & FileProvider
│           ├── res/xml/file_paths.xml    # FileProvider path definitions
│           ├── assets/web/
│           │   ├── settings.html         # Settings view (Appearance, Reminder, Updater)
│           │   ├── logo.png              # App branding
│           │   ├── components/
│           │   │   └── updater-component.js # Standalone Update Component (Button + Progress bar)
│           │   └── css/
│           │       └── tailwind.css      # iOS HIG styles with high-energy accents
│           └── java/com/dailytimer/app/
│               ├── MainActivity.kt        # Hosts WebView and updater native bridge
│               └── updater/
│                   └── AppUpdater.kt      # Idiomatic Kotlin (UpdateStatus Flow, OkHttp, FileProvider)
│
├── version.json                       # Version manifest for self-update verification
├── .gitignore                         # Build and environment exclusions
├── agent.md                           # Core development instructions & constraints
└── coder.md                           # Setup & architectural specification
```

---

## 🛠️ Building & Running

### Prerequisites
- JDK 17
- Android SDK (API 34)

### Local Build Commands
```bash
# Navigate to the android directory
cd android

# Build Debug APK
./gradlew assembleDebug

# Build Signed Release APK
./gradlew assembleRelease
```

Generated APKs will be output to:
- `android/app/build/outputs/apk/debug/app-debug.apk`
- `android/app/build/outputs/apk/release/app-release.apk`

---

## 🔄 Self-Update Workflow (`version.json`)

The app verifies updates against the `version.json` file:
```json
{
  "versionCode": 1,
  "versionName": "1.0.0",
  "minSdk": 26,
  "downloadUrl": "https://github.com/shah-ashish/daily-Timer/releases/download/v1.0.0/app-release.apk",
  "releaseNotes": "Initial release: Settings and In-App Update Engine."
}
```
When `versionCode` on the remote endpoint is higher than the currently installed version:
1. The update card reveals the **Download** action and release notes.
2. Clicking download streams the APK with a real-time progress bar.
3. Once complete, it prompts **Install Now**, triggering the Android package installer.
