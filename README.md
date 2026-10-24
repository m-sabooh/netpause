# NetPause

Native Kotlin/Jetpack Compose UI preview based on `design-explorations/codex/11-stillwater-pulse-motion.html`, with the selected Lively motion and Stillwater/Pulse palette.

Launch directly into the fictional network. Open a device to rename it or try a timed/indefinite pause, replace the current pause, resume, and explore Settings and connection setup. Settings includes English/Arabic switching with RTL and the widget duration field. Android Back and the top back arrow return through the screens. Spring sheets, touch ripples, press compression, sliding duration selection, staggered entrances, and a one-shot confirmation ring respect the system animation setting.

Everything is a local UI simulation. No router requests run, credentials are discarded, and no internet permission is requested. Preview state survives activity recreation; durable settings, secure credential storage, real router control, and the actual home-screen widget remain unimplemented. The preview uses the reference’s 15-minute widget default; the eventual product default still needs resolution in `docs/project-context.md`.

The UI reuses the existing Compose/Material 3 dependencies, with the Material 3 experimental API opt-in limited to modal sheets. The reference’s fixed light palette is used in both system themes. No new dependencies or SDK packages are needed. Line icons are adapted from the reference’s Lucide assets; see `docs/licenses/lucide.txt`.

UI verification on 2026-10-24 used the Android 16 emulator at 411 × 914 dp and 360 × 640 dp, including Arabic RTL, 200% text size, activity recreation, and a pause/resume check with system animations disabled. The five instrumented navigation scenarios pass. Lint has no errors; its remaining warnings concern existing dependency versions. A physical phone has not been tested yet.

## Setup

Open the repository root in Android Studio, or use PowerShell with `JAVA_HOME` pointing to a compatible JDK (17 minimum). The build compiles Kotlin/Java to JVM 17. Set `ANDROID_HOME` to your SDK directory, or let Android Studio write the ignored `local.properties` file.

Required SDK packages: `platforms;android-37.2`, `build-tools;37.0.0`, and `platform-tools`. SDK 37.2 is the latest stable platform in Google's package repository as checked on 2026-10-20. Minimum device version: Android 7.0 (API 24). NDK and CMake are unnecessary for this sample.

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Gradle and Maven dependencies download automatically on the first build. The wrapper pins Gradle 9.6.0 and verifies its SHA-256 checksum.

## Run on a phone

Enable USB debugging, connect the phone, and accept its debugging authorization prompt. Build the APK before running:

```powershell
adb devices
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

With multiple devices, use `ANDROID_SERIAL` for Gradle/ADB and `--device` for Android CLI. Test reports are under `app/build/reports/androidTests/connected/debug/`; lint results are in `app/build/reports/lint-results-debug.html`.

If Android CLI or Gradle on Windows fails with `Unable to establish loopback connection`, use an explicit temporary directory for that PowerShell session:

```powershell
$androidTemp = "$env:USERPROFILE\.android\tmp"
New-Item -ItemType Directory -Path $androidTemp -Force | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$androidTemp -Djava.io.tmpdir=$androidTemp"
```

The app follows the phone language by default. You can switch between English, Arabic, and Follow phone in Settings. For an external Arabic RTL check on Android 13+, leave Follow phone selected, change only this app's locale, then clear the override afterward:

```powershell
adb shell cmd locale set-app-locales com.example.netpause --locales ar
android run --apks=app/build/outputs/apk/debug/app-debug.apk
adb shell cmd locale set-app-locales com.example.netpause
```

## Verification on 2026-10-20

Built and launched on a Samsung Galaxy A16 (SM-A165F) running Android 16. The launch smoke test passed in English and Arabic, and both screens were visually inspected. The app's locale override was cleared afterward. Build and lint completed with no errors; lint reports three available updates for Gradle, the Compose BOM, and the Kotlin Compose compiler plugin. NDK and CMake were not installed or updated.

Product decisions and remaining hardware checks live in [project context](docs/project-context.md) and [router feasibility](docs/router-feasibility.md).
