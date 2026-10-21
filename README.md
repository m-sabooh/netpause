# NetPause

Minimal Kotlin/Jetpack Compose Android sample for verifying the development environment. It displays "Hello Android!" (or its Arabic translation). The planned router controls and widget are not implemented.

## Setup

Open the repository root in Android Studio, or use PowerShell with `JAVA_HOME` pointing to a compatible JDK (17 minimum). The build compiles Kotlin/Java to JVM 17. Set `ANDROID_HOME` to your SDK directory, or let Android Studio write the ignored `local.properties` file.

Required SDK packages: `platforms;android-37.2`, `build-tools;37.0.0`, and `platform-tools`. SDK 37.2 is the latest stable platform in Google's package repository as checked on 2026-10-20. Minimum device version: Android 7.0 (API 24). NDK and CMake are unnecessary for this sample.

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Gradle and Maven dependencies download automatically on the first build. The wrapper pins Gradle 9.6.0 and verifies its SHA-256 checksum.

## Run on a phone

Enable USB debugging, connect the phone, and accept its debugging authorization prompt.

```powershell
adb devices
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

The app follows the phone language. For Arabic RTL verification on Android 13+, change only this app's locale, then clear the override afterward:

```powershell
adb shell cmd locale set-app-locales com.example.netpause --locales ar
android run --apks=app/build/outputs/apk/debug/app-debug.apk
adb shell cmd locale set-app-locales com.example.netpause
```

## Verification on 2026-10-20

Built and launched on a Samsung Galaxy A16 (SM-A165F) running Android 16. The launch smoke test passed in English and Arabic, and both screens were visually inspected. The app's locale override was cleared afterward. Build and lint completed with no errors; lint reports three available updates for Gradle, the Compose BOM, and the Kotlin Compose compiler plugin. NDK and CMake were not installed or updated.

Product decisions and remaining hardware checks live in [project context](docs/project-context.md) and [router feasibility](docs/router-feasibility.md).
