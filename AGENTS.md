# NetPause - Agent Guide

## Overview

NetPause lets users control the internet connection of the devices connected to the same network.

It is a personal side project, replacing the router's complex admin panel with a modern, easy-to-use native Kotlin Android app. Target the Huawei HG8245X6 and local network access only. Support English and Arabic, including RTL.

Main use cases:

- View a list of connected devices.
- Pause a specific device (including this phone) or all devices, indefinitely or for a user-chosen duration in minutes. Typical use is 10 or 20 minutes; no hours-based input is needed.
- Offer to replace the current pause when selecting a new target, retaining the one-active-pause rule.
- Resume manually at any time, cancelling the active timer, or resume automatically when the timer ends.
- Use a home-screen widget that follows the current pause for pause/resume and countdown display. When idle, it offers indefinite or timed all-device pause. Its timed action defaults to 15 minutes, configurable in settings.
- Identify this phone and save custom device names locally without renaming devices on the router.

Version one supports one active pause at a time: either one device or all devices. Remember user-entered router credentials securely. Default the editable router address to `192.168.1.1`. Follow the phone's language by default and offer an English/Arabic setting. Multiple phones may read and control the shared pause by refreshing from the router; immediate cross-phone updates are deferred. Shared control still needs two-phone validation.

## Repository map

- `docs/project-context.md` - Persistent project context, confirmed decisions, and open questions. Read this at the start of a session.
- `docs/router-feasibility.md` - Router protocol research, verified behavior, and remaining hardware acceptance checks. Read before implementing router control.
- `docs/router-test-evidence.json` - Sanitized evidence from the isolated router test.

## Architecture

The native Kotlin/Compose UI uses the Stillwater/Pulse design and Lively motion, with English/Arabic and RTL. `NetPauseApplication` shares one `NetworkController` between activities and widget workers, serializing router operations with a mutex. `RouterClient` handles Wi-Fi-bound HTTP, login, cookies and tokens; `RouterPauses` reads and writes owned parental rules. `AppStorage` encrypts credentials with Android Keystore and persists preferences and local names. `NetPauseWidget` uses RemoteViews and a launcher chronometer; WorkManager runs actions and refreshes, with stale-action checks and no automatic write replay. The widget requires remembered credentials. Indefinite and same-day timed pauses are implemented and have live router evidence; widget hardware acceptance remains separate. It compiles with SDK 37.2, targets API 37, and supports API 24+. Real restoration uses router-side schedules, never a phone timer.

## Commands

Use a compatible JDK (17 minimum) and the Android SDK. See `README.md` for setup.

- Build: `.\gradlew.bat :app:assembleDebug`
- Unit tests: `.\gradlew.bat :app:testDebugUnitTest`
- Lint: `.\gradlew.bat :app:lintDebug`
- Phone smoke test: `.\gradlew.bat :app:connectedDebugAndroidTest`
- Install and launch: `android run --apks=app/build/outputs/apk/debug/app-debug.apk`

NDK and CMake are not needed for the sample. Do not install or update either without asking the owner.

## Maintaining project knowledge

- Keep this guide short. Store durable product decisions and their reasons in `docs/project-context.md`; keep protocol details in the feasibility report.
- Record confirmed requirements separately from proposals, assumptions, and untested behavior. Do not turn an open question into a requirement without evidence or a user decision.
- Update the relevant document when a decision changes or testing establishes new behavior. Replace obsolete guidance and preserve useful reasons for the change.
- Do not treat documented past router tests as authorization to interrupt devices in a new session.

## Change rules

- Preserve unrelated working-tree changes.
- Test Arabic RTL when changing UI or translations.
- This repository is public. Never commit/expose actual device MAC addresses, household public IP addresses, router credentials, or session tokens. Plan for user-entered router credentials rather than hardcoded defaults.
- Update this guide when the architecture or the commands change.
