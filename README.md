# NetPause

Native Kotlin/Jetpack Compose app for a Huawei HG8245X6 with the tested TURKCELL firmware. The Stillwater/Pulse UI now connects to a Wi-Fi-bound HTTP client, reads connected devices, identifies this phone by its Wi-Fi IP, and persists local names, language, and duration preferences. Remembered credentials are encrypted with an Android Keystore key and excluded from backup.

Router control is an initial implementation, not completed hardware acceptance. Timed pause, replacement, and resume use app-owned parental-control objects and verify writes by reading router configuration. The countdown never restores access itself. Timed requests currently must end before 23:59 on the same router date; deadlines round up to the next minute. Indefinite pause remains unavailable pending verification. The actual launcher widget is still deferred, and its saved duration retains the UI reference’s 15-minute default pending resolution of the documented product-default mismatch.

Live testing on the USB-connected Samsung phone verified login with corrected credentials, device discovery, own-phone identification, encrypted credential storage, timed phone-only pause, and recovery of the countdown after restarting the app. Wi-Fi-bound IPv4 internet traffic was blocked while local router access remained available. On 26 October, explicit Wi-Fi-bound HTTP and ping verified automatic restoration with the app force-stopped and the router rule still installed. Early manual resume also restored traffic, and cleanup left no NetPause binding. Shared control still needs two-phone validation; no all-device test was attempted. See the router feasibility report for current evidence and limits.

The HTTP client accepts private IPv4 router addresses only, refuses redirects, uses bounded session reauthentication for reads, and never blindly retries a configuration write. HTTP remains unencrypted on the local Wi-Fi. Router clock disagreement, conflicting parental controls, stale actions, and unexpected response schemas are surfaced as errors.

## Setup

Open the repository root in Android Studio, or use PowerShell with `JAVA_HOME` pointing to a compatible JDK (17 minimum). The build compiles Kotlin/Java to JVM 17. Set `ANDROID_HOME` to your SDK directory, or let Android Studio write the ignored `local.properties` file.

Required SDK packages: `platforms;android-37.2`, `build-tools;37.0.0`, and `platform-tools`. SDK 37.2 is the latest stable platform in Google's package repository as checked on 2026-10-20. Minimum device version: Android 7.0 (API 24). NDK and CMake are unnecessary for this sample.

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
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

With multiple devices, use `ANDROID_SERIAL` for Gradle/ADB and `--device` for Android CLI. Regular instrumentation tests do not pause any device. Gradle is configured to leave APKs installed after tests so phone credentials and settings are preserved. Keep the phone awake and unlocked while running UI tests. Test reports are under `app/build/reports/androidTests/connected/debug/`; lint results are in `app/build/reports/lint-results-debug.html`.

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
