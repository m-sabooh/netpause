# Netpause: Huawei HG8245X6 feasibility and control protocol

Verified against the actual router at `http://192.168.1.1/` on 19 October 2026. This is a research report, not an Android implementation.

## Decision

**Yes, a native Kotlin app is feasible on this router's current firmware.** Device discovery, a per-device internet pause while retaining Wi-Fi/local management access, automatic restoration, and manual restoration were verified. The all-device control exists in the firmware UI and request model, but its traffic behavior was not tested.

| Requested capability | Verification |
|---|---|
| List connected devices | Verified by authenticated device-list reads |
| Pause one device without disconnecting Wi-Fi | Verified on the authorized Android phone over IPv4 |
| Resume automatically | Verified using a short router-side allowed-time schedule |
| Resume manually | Verified by removing only the test phone's binding |
| Pause all devices | UI and request format inspected; not activated |
| Default 30-minute timer | Same schedule mechanism is applicable; a full 30-minute run and boundary cases remain untested |

The phone does not need internet access to control a router on its local Wi-Fi network.

The safest implementation uses **router-side parental-control schedules**. A phone-side countdown cannot guarantee restoration if the phone leaves home, loses power, or cannot run background work. This router has minute-resolution calendar schedules, not an observed `pauseForSeconds` API.

Actual test results and remaining limits are recorded below. Do not equate a successful settings request with proof that every traffic type is blocked.

## Hardware and access verified

| Item | Observed value |
|---|---|
| User-supplied model | HG8245X6-10 |
| Model reported by firmware | HG8245X6 |
| Software | V5R022C10S520 |
| Hardware version | 1E8E.A |
| Firmware release timestamp | 2025-09-28 13:05:48 |
| ISP customization | TURKCELL2 |
| Base URL | `http://192.168.1.1/` |
| Login | Supplied `admin` account worked |
| Relevant UI | Advanced > Security > MAC Filtering / Parental Control |
| Clock configuration | Istanbul, UTC+03:00; NTP enabled |
| Device inventory snapshot | 26 known entries, 8 online, including OdysseyA16 |

The password, session cookies, tokens, router serial number, and household device inventory are deliberately omitted from these artifacts. Ask for credentials during app setup and store them securely. Do not compile the supplied password into the APK.

## Live test

The user authorized a short interruption only for OdysseyA16. USB debugging provided an independent path for checking that phone. No app was built or installed on it.

The phone ran Android 16. Network probes were bound to its Wi-Fi interface or Wi-Fi IPv4 address, preventing cellular fallback from producing a false success. Mobile data was enabled and was left unchanged.

| Check | Before pause | During pause |
|---|---|---|
| Wi-Fi IPv4 ping to 1.1.1.1 | Success | 100% packet loss |
| Wi-Fi TCP/HTTP to 1.1.1.1:80 | HTTP 301 | Connection timeout |
| Local router HTTP | HTTP 200 | HTTP 200 |
| Wi-Fi interface | Connected | Still UP, same IPv4 address |
| Wi-Fi IPv6 internet | No default route; network unreachable | Not a valid IPv6 blocking test |

The test used a separate `NetpauseVerify` template with `StartDate=EndDate=20261019`, one allowed interval from `18:40` to `23:59`, and `RepeatDay=1` (Monday). Binding the phone outside that interval blocked its internet immediately. The existing template was not modified.

**Automatic restoration passed.** After the 18:40 allowed period began, Wi-Fi IPv4 ping and HTTP worked again without a resume command. A subsequent authenticated read at 18:41:49 confirmed that the same binding remained installed. The exact second at which forwarding resumed was not measured.

**Manual restoration passed.** The test period was moved to 18:44, briefly blocking the phone again. The router remained reachable. Deleting only the test MAC binding restored IPv4 ping and HTTP before the new allowed period.

**Cleanup passed.** The test binding, template, and its period were removed. Readback of the parental-control bindings, templates, and periods exactly matched the pre-test snapshot. The existing unbound template remained intact. No household-wide pause was performed.

Machine-readable evidence is in [router-test-evidence.json](router-test-evidence.json). These artifacts contain no actual device MAC, household public IP, session token, or password. The public probe address `1.1.1.1` and the all-device sentinel below are protocol/test constants, not household identifiers.

## Connection and authentication

These are private web-management endpoints used by the firmware UI. They are not a documented, stable vendor SDK. An ISP firmware update can change paths, fields, privileges, or parsing rules.

1. Read `/` and inspect the login page and customization.
2. `POST /asp/GetRandCount.asp` with an empty body to obtain a fresh login nonce.
3. `POST /login.cgi` using `application/x-www-form-urlencoded`:

   ```text
   UserName=<username>
   PassWord=<Base64 of password, standard alphabet, no line breaks>
   Language=english
   x.X_HW_Token=<nonce>
   ```

   The UI sets the initial cookie `Cookie=body:Language:english:id=-1`. Keep a matching local `Referer` header.

4. Retain the response's `CookieHttp` session cookie, including its complete value. Do not split its value on `=`: it contains `sid=...`. A proper cookie jar is preferable to hand parsing.
5. The observed login response is HTTP 200 with JavaScript navigation to `index.asp`, rather than a normal HTTP 302. Confirm authentication by reading `/index.asp` or the parental-control data page and checking the expected schema.
6. Send the session cookie on subsequent requests. A login page or JavaScript redirect to `/` can arrive with HTTP 200 after session invalidation. Detect that and reauthenticate with bounded retries.
7. Before changing configuration, fetch the relevant configuration page and extract the hidden input whose **name** is `onttoken`. Its element ID can be `hwonttoken`. Include the value as `x.X_HW_Token` in the POST body.

The inspected login page reports a three-failure lockout threshold. Stop on authentication failure rather than retrying indefinitely. Opening another admin session invalidated an earlier session during inspection, so serialize app operations and tolerate another browser using the panel.

The TURKCELL2 branch uses Base64 encoding, not the PBKDF2/SHA256 login branch also present in the shared source. Base64 is not encryption. HTTP exposes login credentials and session cookies to anyone able to observe that traffic. HTTPS capability was not verified.

## Read endpoints and parsing

| Purpose | GET path |
|---|---|
| Authorized menu | `/asp/getMenuArray.asp` |
| Model, firmware, current router time | `/html/ssmp/deviceinfo/deviceinfo.asp` |
| Device list | `/html/bbsp/common/GetLanUserDevInfo.asp` |
| Parental bindings, templates, periods, counters | `/html/bbsp/common/parentalctrlinfo.asp` |
| Parental overview and token | `/html/bbsp/parentalctrl/parentalctrlmac.asp` |
| Template configuration and token | `/html/bbsp/parentalctrl/parentalctrltemplate.asp` |
| Schedule editor and token | `/html/bbsp/parentalctrl/parentalctrltime.asp?TemplateId=<id>&FlagStatus=EditTemplate` |
| MAC filter configuration and token | `/html/bbsp/macfilter/macfilter.asp` |

Responses contain HTML and JavaScript constructor calls, not JSON. For example, the device data uses:

```text
USERDevice(Domain, IpAddr, MacAddr, Port, IpType, DevType,
           DevStatus, PortType, Time, HostName, IPv4Enabled,
           IPv6Enabled, DeviceType, UserDevAlias,
           UserSpecifiedDeviceType, LeaseTimeRemaining,
           TrafficSendRate, TrafficRecvRate)
```

Parse the relevant constructor records and decode JavaScript string escapes such as `\x3a` for `:`. Preserve strings and field positions; do not split naively on commas inside strings. Never evaluate downloaded JavaScript in the app. Device names are untrusted data. Fail visibly on an unexpected schema rather than silently showing an empty device list.

The list includes offline historical records. Use `DevStatus=Online`, case-insensitively, for currently connected devices. Display an alias or hostname with IP/MAC as a fallback. Select targets by MAC, not IP, because DHCP addresses change. Randomized MAC addresses can also change, so a remembered device is not an unbreakable identity.

The current router clock is available as `systemdsttime` in the device-information page, including its UTC offset. In this firmware, `/asp/GetLocalTime.asp`, although referenced by shared JavaScript, returned 404. Do not build around that broken endpoint or the time page's placeholder values.

## Parental-control data model

The base path below is a router data-model identifier, not an Android class:

```text
P = InternetGatewayDevice.X_HW_Security.ParentalCtrl

P.Templates.<id>
  Name, StartDate, EndDate
  DurationPolicy, DurationRight
  UrlFilterPolicy, UrlFilterRight

P.Templates.<id>.Duration.<id>
  StartTime, EndTime, RepeatDay

P.MAC.<id>
  MACAddress, Description, TemplateInst
```

Dates use `YYYYMMDD`; times use `HH:mm`; weekdays are comma-separated numbers with Monday=1 through Sunday=7. `TemplateInst` is the numeric template instance as a string, not its full object path.

The UI limits are eight templates, four periods per template, and eight managed-device entries. These are observed firmware UI limits, not independently measured backend maximums. One existing unbound template named `template` was present before testing.

### Critical firmware-specific behavior

`ParentCtrlTimeFeature='0'` hides the newer time-filter enable and blacklist/whitelist controls. The visible UI describes **allowed** internet periods. The live test sent `DurationRight=0` and `DurationPolicy=0`, matching the existing hidden controls, and the allowed-time schedule still blocked traffic outside its period.

Therefore, **do not assume `DurationRight=0` disables time control on this firmware**, and do not interpret `DurationPolicy=0` as an effective blocked-time mode merely because generic shared code calls it a blacklist. Use the semantics proven on this firmware. To resume manually, remove the app-owned MAC binding rather than relying on these hidden switches.

The separate global parental `Enable` toggle in shared page source belongs to another ISP customization branch. It was not needed for the test and should not be toggled speculatively.

## Requests exercised by the isolated test

All POST bodies below are form encoded and include a fresh `x.X_HW_Token`. `<id>` values must be obtained by reading back configuration. Do not hardcode the test's instance IDs.

**Create an unbound template:**

```text
POST /html/bbsp/parentalctrl/add.cgi
  ?x_SAVE_A=InternetGatewayDevice.X_HW_Security.ParentalCtrl.Templates
  &RequestFile=html/bbsp/parentalctrl/parentalctrltime.asp

x_SAVE_A.Name=NetpauseVerify
x_SAVE_A.StartDate=20261019
x_SAVE_A.EndDate=20261019
x.X_HW_Token=<fresh token>
```

Read `parentalctrlinfo.asp` and locate the exact new template. Build its complete schedule before binding devices, avoiding an incomplete active rule.

**Add an allowed period:**

```text
POST /html/bbsp/parentalctrl/add.cgi
  ?x=InternetGatewayDevice.X_HW_Security.ParentalCtrl.Templates.<id>.Duration
  &y=InternetGatewayDevice.X_HW_Security.ParentalCtrl.Templates.<id>
  &RequestFile=html/ipv6/not_find_file.asp

x.StartTime=18:40
x.EndTime=23:59
x.RepeatDay=1
y.DurationRight=0
y.DurationPolicy=0
x.X_HW_Token=<fresh token>
```

The test received HTTP **403** from this request even though the period was successfully created and took effect. A later period update and template deletion returned **404**, also despite successful configuration changes. The firmware UI uses the unusual `RequestFile=html/ipv6/not_find_file.asp` destination for period writes. This establishes that status codes alone are insufficient; it does not prove the exact internal cause of each error. Read back state after ambiguous responses before retrying, or duplicate rules may result. An HTTP error is not automatically success either.

The manual-resume test updated the period using `/html/bbsp/parentalctrl/set.cgi`, with `x` set to the full existing duration domain and `y` set to the template domain. Its body used the same period fields shown above. Readback confirmed the new allowed start before the blocked-state probe.

**Bind one device:**

```text
POST /html/bbsp/parentalctrl/add.cgi
  ?x=InternetGatewayDevice.X_HW_Security.ParentalCtrl.MAC
  &RequestFile=html/bbsp/parentalctrl/parentalctrlmac.asp

x.MACAddress=<selected device MAC>
x.Description=NetpauseVerify
x.TemplateInst=<id>
x.X_HW_Token=<fresh token>
```

Read back the binding, confirm its exact target MAC and template, and record its full domain for later removal. An app should distinguish configured policy from a live connectivity test.

**Delete an app-owned binding, following the UI's deletion format:**

```text
POST /html/bbsp/parentalctrl/del.cgi
  ?RequestFile=html/bbsp/parentalctrl/parentalctrlmac.asp

InternetGatewayDevice.X_HW_Security.ParentalCtrl.MAC.<binding-id>=
x.X_HW_Token=<fresh token>
```

The selected object's full domain is a body key with an empty value. Read back after deletion. Delete only objects owned by the current pause. Preserve unrelated parental controls.

**Remove the now-unbound test template:**

```text
POST /html/bbsp/parentalctrl/del.cgi
  ?RequestFile=/html/bbsp/parentalctrl/parentalctrlstatus.asp

InternetGatewayDevice.X_HW_Security.ParentalCtrl.Templates.<template-id>=
x.X_HW_Token=<fresh token>
```

This removed its child duration as well. Confirm that no devices remain bound to the template before deleting it. The test's final readback matched the original bindings, templates, and periods.

## Turning a 30-minute countdown into a router schedule

For a same-day pause from 18:00 to 18:30, create a template valid only for that date, with allowed periods before the pause and from 18:30 to 23:59. Bind it only once the complete schedule is verified. The before-pause period makes the schedule a proper complement, although the short live test needed only the future period.

The end of the blocked interval is the beginning of an allowed interval. The router can resume forwarding without any request from the phone. The app countdown is a display of the router's deadline, not the mechanism that restores access.

Important boundaries:

- UI time precision is one minute, so define rounding explicitly. Rounding the resume deadline upward avoids shortening a requested pause but can add less than a minute.
- A period cannot cross midnight: the UI requires start < end and hours 00-23. A midnight-spanning pause needs date/day-specific allowed periods, with tests for the two dates and expiration behavior.
- `23:59` is the UI's whole-day endpoint. Its exact final-minute semantics were not tested.
- Use a bounded validity date to avoid accidentally repeating the pause next week. Verify out-of-date behavior before promising a one-shot timer under all conditions.
- Validate the router clock against the phone. A calendar schedule cannot deliver the intended duration when the router's clock is wrong or jumps.
- A reconnect, new pause, manual resume, or stale widget action must not let an old operation undo a newer pause. Persist the owned object IDs and deadline and serialize operations.
- Removing an app-owned rule means restoring the previous policy, not overriding someone else's pre-existing restrictions.

## All-device pause

The actual parental-control page offers “Apply on all devices.” Its request uses `x.MACAddress=FF:FF:FF:FF:FF:FF` as a firmware-defined all-device marker. This is stronger than enumerating currently connected devices because the intended scope also covers later arrivals.

This mode was inspected but **not activated**, because only the phone test was authorized. Local control during a per-device internet block worked; household-wide behavior, coverage of Ethernet/guest networks, and newly arriving devices still require validation.

Switching between all-device and specified-device modes in the UI deletes existing MAC bindings. Do not blindly replay the UI's mode-switch handler. Save the existing policy and design conflict handling before offering an all-device button. Do not assume a global binding and individual bindings can coexist or that their precedence is defined.

## MAC filtering alternative

Advanced > Security > **MAC Filtering** explicitly describes preventing internet access. **Wi-Fi MAC Filtering** controls Wi-Fi association and is the wrong primitive for keeping a phone locally connected.

The internet MAC-filter page exposes these source-derived operations, not exercised in the live test:

```text
set.cgi?x=InternetGatewayDevice.X_HW_Security
  x.MacFilterRight=1       enable filtering
  x.MacFilterPolicy=0      blacklist mode

add.cgi?x=InternetGatewayDevice.X_HW_Security.MacFilter
  x.SourceMACAddress=<MAC>
  x.DeviceAlias=<name>
```

Include the appropriate `RequestFile` and token, and use the paths as resolved from the MAC-filter page. Policy 1 is whitelist mode and can block other users; avoid it for a per-device pause. The configured UI maximum is eight MAC-filter entries. MAC filtering alone has no observed expiry field, so it requires a later removal command and is a weaker choice for dependable timed restoration.

## Kotlin and home-screen widget implications

- A native HTTP client can replay this protocol. No root access, custom router firmware, cloud backend, or embedded browser was needed for the verified operations.
- Use a small widget with “Pause 30 min” and “Resume,” plus pending/error/last-confirmed state. Do not display success before router readback. Android supports interactive control widgets: [App widgets overview](https://developer.android.com/develop/ui/views/appwidgets/overview).
- Keep router requests on the Wi-Fi `Network`, even if Android prefers cellular because Wi-Fi has no internet. `Network.openConnection()` explicitly routes a connection over that network. Do not require internet validation for a local-router operation: [Network API](https://developer.android.com/reference/android/net/Network).
- Declare `INTERNET`; account for network-state permissions needed by the chosen connectivity APIs. When targeting SDK 37+, request `ACCESS_LOCAL_NETWORK` at runtime under current Android guidance. The Android 16 opt-in testing path differs: [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission).
- Android apps targeting API 28+ deny cleartext traffic by default. Configure the necessary cleartext exception for the router and constrain credential-bearing requests to the intended router. Do not send credentials to arbitrary redirects: [Network security configuration](https://developer.android.com/privacy-and-security/security-config).
- Store credentials with protection backed by Android Keystore, exclude them from logs/backups as appropriate, and keep session tokens out of widget text and diagnostics.
- Do not use a coroutine delay, widget refresh interval, or WorkManager as a guaranteed restoration timer. Exact alarms have permission and lifecycle constraints, and even an exact alarm cannot reach a router after the phone leaves its LAN. Use router schedules for restoration; Android work can update display state and clean up expired objects: [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms).

## What this does and does not enforce

A router pause blocks internet through that router. It does not stop cellular data, a neighbor's Wi-Fi, downloaded videos, or already buffered playback. MAC-based selection can be evaded by changing the device's MAC identity. Anyone with router admin credentials can undo the restrictions. Those are limits of this approach, not Kotlin problems.

Do not use DNS or YouTube URL filtering as a substitute for the requested complete internet pause. This investigation deliberately tested traffic to a literal public IP, independent of DNS.

## Remaining acceptance checks

Before treating this as a dependable family app, verify IPv6 with a working baseline, all-device scope, Ethernet and guest clients, existing TCP/QUIC streams, date expiration, midnight, router reboot/time synchronization, overlapping pauses, manual changes in the admin panel, and actual widget behavior under Android network switching. Test an ordinary 30-minute interval as well as the short interval used here.

The Android CLI documentation lookup failed locally while initializing its knowledge-base download. Official Android web documentation was used instead. No Android application code was created.
