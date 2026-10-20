# NetPause project context

Last updated: 2026-10-20. Initial product interview complete; the requirements below are the baseline for future sessions.

## Purpose and current state

NetPause controls internet access for devices on the same network. The baseline agent guide lists device discovery, individual or all-device pause, and timed restoration as its main use cases.

This repository contains documentation only. There is no application implementation, build setup, or test runner. The owner wants durable repository knowledge so work can continue across Codex sessions without repeating project discovery.

## Product decisions confirmed on 2026-10-20

- **Audience and purpose:** A personal side project for the owner and a family member. Replace the old, complex router admin panel with a modern, easy-to-use mobile app for managing connected devices.
- **Quality and scope:** This is not intended as a production-grade or broadly supported product. Keep implementation proportional to household use while retaining correct behavior, error handling, and safe restoration of access.
- **Platform:** Native Kotlin Android app.
- **Router:** Huawei HG8245X6. Support for other router models is outside the current scope. The tested firmware is listed below; compatibility with other firmware is not established.
- **Connectivity:** Local network control only. Remote access is outside scope.
- **Languages:** English and Arabic, including Arabic RTL support.
- **Setup defaults:** Start the router address at `192.168.1.1` and allow editing. Follow the phone's language by default, with an English/Arabic override in app settings.
- **Credentials:** Users enter their router credentials because defaults differ. Remember credentials securely so users do not have to enter them whenever the app opens and widget actions can authenticate without a login form. Follow the feasibility report's Keystore-backed storage guidance; do not hardcode shared defaults.
- **Multiple phones:** The owner and family member may control the same router from separate phones. Shared pause status and control on router refresh are sufficient for version one. Immediate cross-phone updates are deferred. This is a confirmed requirement awaiting hardware validation.
- **Excluded feature:** Board/display-brightness control was copied into the baseline guide by mistake and has been removed. This project has no board/display requirement.

## Core behavior confirmed on 2026-10-20

- **Pause targets:** A specific device or all devices. Users must be able to identify and pause their own phone.
- **One active pause:** For version one, there is one active NetPause pause at a time, targeting either one device or all devices. Independent simultaneous device pauses and overlapping global/individual pauses are outside first-version scope.
- **Replacing a pause:** When selecting another target during an active pause, offer to replace the current pause rather than requiring a separate manual resume first. Replacement must retain the one-active-pause rule and retire the old timer. Do not report the new pause as active until router readback confirms it, or assume replacement is an atomic router operation.
- **Pause modes:** Pause indefinitely until manually resumed, or pause for a duration chosen by the user.
- **Duration entry:** Use minutes. The owner's expected use is 10 or 20 minutes; hours-based input is unnecessary. This does not change the previously specified 30-minute widget default or impose an unstated maximum duration.
- **Early resume:** A user may restore access before the countdown ends. This cancels that pause's timer; the user does not have to wait for expiry.
- **Device names:** Users can set custom names. Store these on the local Android device; do not change names on the router. Names are not required to sync across phones.
- **Widget default:** The widget's timed action uses 30 minutes initially. Users can change that default in app settings. The in-app duration choice is not limited to this default.

### Home-screen widget states

The owner specified these states and actions:

| State | Display and available actions |
|---|---|
| No active pause | Two actions: pause all devices indefinitely, or pause all devices using the configured default duration. |
| All devices paused indefinitely | Offer manual resume. |
| All devices paused with a timer | Display the running countdown and offer manual resume, cancelling the timer. |
| A selected device paused indefinitely | Offer manual resume for that device. |
| A selected device paused with a timer | Display the running countdown and offer manual resume for that device, cancelling its timer. |

The same widget follows the single current pause. Its idle buttons pause everyone; pausing a TV in the app changes the widget to the TV's countdown/resume state; resuming returns it to the idle buttons. The owner confirmed this behavior. Separate widget target configurations have not been requested.

Custom names and own-phone identification are confirmed device-list features. Offline history, favorites/groups, and recurring schedules have not been requested as first-version requirements.

## Shared control across phones: required behavior, not yet verified

The router stores the pause binding and, for a timed pause, its schedule. This makes shared control feasible without transferring a phone-side countdown: another authorized phone on the local network can read router configuration and request removal of the app-owned pause. Automatic restoration must continue to use the router's schedule.

Read current status from the router on refresh, so Phone B can discover and resume Phone A's pause and Phone A sees that change on its next refresh. The owner confirmed that this is sufficient for version one. Immediate updates on other phones can be implemented later; they are not part of the initial implementation.

Before claiming this works, verify that a second installation can identify the shared app-owned binding and recover its target, pause mode, and deadline from router state. Another phone must not need Phone A's private local record of object IDs to resume safely. Preserve unrelated parental-control rules and verify router state before and after changes; local cached status alone is insufficient.

The feasibility test observed that another admin login invalidates an existing session. Handle expired sessions with bounded reauthentication and errors rather than repeated competing logins. Serialization within one app does not coordinate separate phones; conflicting or stale actions require explicit handling and two-phone testing. A single-active-pause product rule does not prove concurrent requests are safe.

Local custom names are not router data. Sharing pause control does not automatically share names or app preferences between installations.

## First-version acceptance checklist

These checks describe required outcomes, not completed tests:

- Enter and securely remember router credentials; reopen the app and use widget actions without re-entering them. Verify the editable router address, system-language default and English/Arabic override, Arabic RTL, locally saved device names, own-phone identification, and the configurable widget duration default.
- Pause either one device, including this phone, or all devices. Support indefinite and timed pauses entered in minutes, with only one active NetPause pause at a time. Offer replacement when changing targets and verify the old pause/timer cannot affect the replacement. Verify that local router control remains reachable while internet access is paused.
- Restore access automatically at the router deadline even if the initiating app is closed or its phone leaves the network. Manually resume early and confirm that the cancelled timer cannot affect a subsequent pause.
- Verify ordinary 10- and 20-minute use and a short pause spanning midnight. Not needing hours-based input does not eliminate date-boundary correctness.
- Verify every widget state in the table against confirmed router state, including a selected-device pause started inside the app. Distinguish pending, failed, and stale/unknown state rather than reporting success before readback.
- On two phones, refresh to discover the same active pause, resume from the other phone, then refresh the initiating phone to see the change. Exercise login invalidation and conflicting/stale requests without modifying unrelated parental controls.

## Established technical evidence

The [router feasibility report](router-feasibility.md) records a live test on 2026-10-19 against a Huawei HG8245X6 (user-supplied model HG8245X6-10), firmware V5R022C10S520, ISP customization TURKCELL2. The [sanitized test evidence](router-test-evidence.json) supports that report.

- Listing devices and pausing one Android phone's IPv4 internet access worked while Wi-Fi and local router access remained available.
- Router-side allowed-time schedules restored access automatically. Removing the test binding restored access manually. Cleanup matched the original router configuration.
- All-device control was inspected but was not activated. IPv6 blocking was not verified because the test network had no working IPv6 baseline.
- The tested router uses private web-management endpoints. Requests can return HTTP errors even after changing configuration, so readback is necessary before retrying or reporting success.
- Router schedules are the demonstrated restoration mechanism. A phone countdown alone cannot guarantee restoration after the phone disconnects or shuts down. Minute precision and date boundaries still need validation.
- Indefinite pause is a product requirement, but its implementation was not exercised by the short timed test. Validate it before treating it as supported behavior.

Consult the report for endpoint formats, authentication, schedule semantics, preservation of existing parental controls, and the complete remaining acceptance checks. Do not generalize these findings to other firmware or routers without testing.

## Existing repository rules

- Preserve unrelated working-tree changes.
- Test Arabic RTL for UI or translation changes.
- This is a public repository. Do not publish actual device MAC addresses or household public IP addresses.
- Keep actual router credentials and session tokens out of this public repository. The guide's earlier permission to expose credentials has been replaced with user-entered credentials and the report's existing handling guidance.

## Deferred scope and remaining implementation decisions

- Immediate cross-phone updates are explicitly deferred. Version one uses refresh.
- Simultaneous independent device pauses are outside version one. Remote access and support for other router models are outside the current scope. No board/display feature belongs here.
- The owner specified minutes-based input, not a maximum duration or a deadline-rounding policy. Set validation from verified router capabilities; do not assume arbitrarily long timers are supported. Minute-resolution scheduling and midnight/date expiration remain technical validation gaps.
- Repeated pause taps and editing an active timer have not been specified; do not add extension/restart behavior by assumption. The confirmed way to change targets is the explicit replacement flow.

Keep implementation choices proportional to this household tool and preserve existing router policy. Consult the feasibility report for the broader hardware validation gaps. Requirements describe the desired app, not proof that its router operations already work.

## How to maintain this file

Record user-confirmed decisions with their reasons and distinguish them from observed technical behavior. Resolve or replace the open questions as the interview progresses. Keep transient task progress and session transcripts out of this document. Update `../AGENTS.md` when the repository architecture, commands, or standing change rules change.
