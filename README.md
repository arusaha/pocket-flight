# Pocket Flight: Automatic Airplane Mode for Android

**Pocket it. Disconnect. Unlock. Reconnect.**

Pocket Flight is an Android airplane mode automation app that turns airplane mode on when your phone is locked and detects that it is in your pocket, then turns it off when you unlock. Choose Wi-Fi networks and Bluetooth devices that keep your phone connected, so your home network, headphones or watch can prevent the pocket trigger while connected.

Fewer trips to Quick Settings. A simple routine built around putting your phone away and picking it up again. Runs on your phone with **no ads, no account and no analytics**. Works without root using **Shizuku**, which requires setup first.

**[Download the Android APK](https://github.com/arusaha/pocket-flight/releases/latest)** · [Setup guide](#install) · [FAQ](#frequently-asked-questions) · [Report an issue](https://github.com/arusaha/pocket-flight/issues)

## Walk. Run. Give your phone a signal break.

**More time in motion. Less cellular radio activity in your pocket.**

Heading out for a morning walk, a run or a weekend hike? Lock your phone, slip it into your pocket and let Pocket Flight switch on airplane mode once pocket detection confirms it. Unlock when you need your phone, and Pocket Flight turns airplane mode back off so it can reconnect. One less setting to remember before you head out.

If you prefer to reduce your phone's radio-frequency electromagnetic field (**EMF**) emissions while carrying it close to your body, Pocket Flight makes the airplane-mode habit automatic. Airplane mode disables cellular connectivity, stopping normal cellular transmissions while it is on. Your selected Wi-Fi and Bluetooth exceptions still apply, so a connected device you have chosen can keep the phone online.

Wi-Fi and Bluetooth can remain active according to [Android's remembered airplane-mode settings](https://support.google.com/pixelphone/answer/12639358?hl=en). Pocket Flight does not measure EMF exposure or promise health benefits, and airplane mode does not eliminate all EMF. Cellular calls and mobile data are unavailable while it is on.

## What Pocket Flight does

- **Automates airplane mode:** lock your phone, put it in your pocket and let sustained pocket detection trigger the switch. Unlock to turn off airplane mode enabled by the app.
- **Respects your connections:** a connection to any selected Wi-Fi network or Bluetooth device prevents airplane mode from turning on. A saved network or paired device alone does not count.
- **Adapts to your pocket:** proximity detection plus optional **Pocket assist**, which combines darkness and tilt when proximity alone misses your pocket. Adjust the confirmation delay from 2–15 seconds.
- **Keeps you in control:** pause and restore connectivity from the persistent notification. Airplane mode you enabled manually stays under your control.
- **Keeps settings on your phone:** no Internet permission, cloud account or GPS coordinate collection. Connection choices and diagnostics stay in app-private storage.
- **Helps troubleshoot your phone:** a live sensor test and saved pocket report show what the detector sees.

## Before you install

Requires **Android 11 or newer**, a working [Shizuku setup](https://shizuku.rikka.app/guide/setup/), suitable sensors and permission to run in the background. Download the APK and sideload it directly; no Play Store installation is needed. On an unrooted phone, start Shizuku again after each reboot.

Version 1.1 has been **reported working on a Galaxy Z Fold8 running Android 17**, including folded pocket use after the detection update. Other phones and firmware need their own testing. Pocket sensing is a heuristic; optional Pocket assist can also trigger on a tilted phone in darkness.

While airplane mode is on, cellular calls and mobile data are interrupted. Wi-Fi and Bluetooth follow Android's airplane mode preferences. Sensor monitoring can use extra battery; this app does not promise battery savings.

## Install

1. Download `PocketFlight-1.2.apk` from [Releases](https://github.com/arusaha/pocket-flight/releases/latest) to your phone and open it from Files. Allow that app to install unknown apps when Android asks. If Samsung Auto Blocker prevents installation, review its sideloading setting in Security and privacy.
2. [Install Shizuku](https://shizuku.rikka.app/download/) and follow its [wireless debugging setup](https://shizuku.rikka.app/guide/setup/). Start it, open Pocket Flight, then tap the **settings gear → Shizuku → Connect / grant Shizuku access**. Approve the request in Shizuku. No rooting is needed.
3. In **Settings → Permissions & battery**, tap **Allow location, Bluetooth & notifications**. For Wi-Fi exceptions, select precise location, then use **App settings** → Permissions → Location → **Allow all the time**. Keep Android's Location switch enabled. Bluetooth selection requires Nearby devices access. Notifications provide the persistent pause control.
4. In **Settings → Connection exceptions**, select your exact Wi-Fi network name(s) and paired Bluetooth device(s). You can add the currently connected Wi-Fi network or type its name. Either kind of matching connection prevents airplane mode. The app remembers the choices locally.
5. On Samsung, set Battery to **Unrestricted** for Pocket Flight and Shizuku. Unrestricted apps may not appear in the **Never sleeping apps** picker; if Pocket Flight is already unrestricted, leave it that way. Check that neither app is in **Sleeping apps** or **Deep sleeping apps**. In Secure lock settings, enable **Lock instantly with Side button**. Labels can vary with One UI.
6. In **Settings → Diagnostics**, run **Test proximity sensor** on both the cover and inner screens, then **Read-only connection check**. Return to the main screen, tap **Start monitoring**, and follow the physical test checklist below.

After a phone restart, **start Shizuku again**. Pocket Flight attempts to resume monitoring automatically; opening it will also resume it. Android/Samsung can still stop background apps.

## Version 1.2: a simpler main screen

The main screen shows your current status, a flight animation, a **Start monitoring / Stop monitoring** button and a settings gear. While monitoring, the dot circles continuously and the normal subtext reads **“Waiting for your phone to be locked and pocketed”**. Starting lifts the plane; stopping settles it back. Setup problems and restoration warnings remain visible when they need attention.

The gear opens settings for Shizuku, permissions and battery, connection exceptions, pocket detection, diagnostics and recent activity. The back arrow and Android Back return through settings to the main screen. Animation runs only on the visible main screen and respects Android's system animation setting. The orbit indicates that monitoring is enabled; it does not mean airplane mode is already on.

To update from 1.0 or 1.1, first use **Pause & restore connectivity** in the old app, then install the 1.2 APK over it. The package and signing key are unchanged, so your saved settings are retained. Do not uninstall first. Open the app after updating. **Stop monitoring** in 1.2 performs the same pause-and-restore action; the notification still provides **Pause & restore**.

## Pocket detection on foldable phones: what's new in 1.1

Version 1.0 could miss a folded phone in a pocket even when hand coverage worked. Version 1.1 adds an optional second detection path and diagnostics, and was reported working on a Galaxy Z Fold8. Proximity coverage is not a reliable proxy for every fabric, distance or fold/display state; test pocket behavior on your own phone.

1. Under **Settings → Pocket detection**, enable **Pocket assist** (darkness + tilt). It is off by default, including after an upgrade if you have not enabled it before.
2. Leave its light limit at **10 lux** and the confirmation delay at **5 seconds** for the first test.
3. Start monitoring. With no selected connection active, lock the folded phone and place it in your pocket for **15 seconds**, then unlock.
4. Open **Settings → Diagnostics → Last pocket report**. It records proximity distance, light level, tilt, lock/screen state, selected sensors and blocking conditions. Use **Copy report** when reporting a failed test in an issue. Wi-Fi names are omitted from this diagnostic.
5. If the report shows light above the chosen limit while actually pocketed, the limit can be adjusted from 1–50 lux. Higher limits can produce more false triggers. A report stuck on “waiting” identifies missing sensor readings instead of assuming darkness.

Pocket assist accepts low light **and** a phone tilted at least about 35° away from flat, even when proximity says clear. It requires fresh post-screen-off readings and recent orientation data. A flat phone in darkness does not satisfy this fallback, but a tilted phone in a dark room can. Proximity remains an independent trigger, so covering it on a flat surface can still activate airplane mode. This is a heuristic, not guaranteed pocket recognition.

The confirmation timer now tolerates clear gaps up to 800 ms without discarding all progress, but those gaps do not count toward the delay and activation never happens while the detector is clear. Sensors are reselected and re-registered when the screen turns off or the device configuration changes, rather than reusing readings from the previous display session.

## Behavior

- Requires screen off, device actually locked, and sustained pocket evidence for the chosen delay (5 seconds by default, adjustable from 2–15). Evidence is proximity coverage or, when explicitly enabled, the Pocket assist fallback described above.
- Checks Wi-Fi and Bluetooth immediately before enabling airplane mode. Wi-Fi matching is exact and case-sensitive; Bluetooth matching uses the selected paired device's address and checks the current connection, including connections that existed before monitoring started.
- A connected selected network **or** device prevents entry. Pairing/saving alone does not prevent it. If a configured exception cannot be checked, entry is blocked.
- Wi-Fi without internet still counts, including when mobile data or a VPN is the default network.
- Once enabled by Pocket Flight, airplane mode stays on until unlock, even after you remove the phone from your pocket. Waking the lock screen alone does not restore it.
- **Stop monitoring** in the app, or **Pause & restore** in the notification, stops automation and turns off airplane mode enabled by this app.
- Manually enabled airplane mode is left unchanged. Manually switching airplane mode off while locked suppresses re-entry for that lock session.
- Active Android call/communication audio modes block entry. This is a best-effort extra check, not a guarantee against every OEM or app-specific call implementation.
- Changes are serialized and checked again after Bluetooth lookup. A durable ownership marker helps recover after a process restart. An unlock during an in-flight enable is followed by a restore.
- No account, analytics, GPS coordinate collection, advertising or Internet permission. Network names, device addresses, the last 30 activity messages and one bounded pocket diagnostic stay in app-private storage; Android backup is disabled.

## Frequently asked questions

### Can I automate airplane mode on Android without root?

Yes, with Pocket Flight and Shizuku on a supported phone. Android does not let ordinary apps toggle airplane mode directly, so Pocket Flight uses Shizuku's elevated access to run the system command. Wireless debugging setup and a Shizuku permission grant are required; restart Shizuku after reboot. See the [installation steps](#install).

### Can I keep airplane mode off when connected to home Wi-Fi or a Bluetooth device?

Yes. Select your Wi-Fi network names and paired Bluetooth devices in the app. If any selected connection is active, Pocket Flight will not enable airplane mode. You can use this for home Wi-Fi, headphones, a smartwatch or another connected Bluetooth device. If the app cannot verify a configured exception, it blocks entry into airplane mode.

### Can I reduce phone EMF while walking or running with it in my pocket?

Pocket Flight can automate airplane mode during those pocketed stretches, reducing radio-frequency emissions from normal cellular transmissions while airplane mode is active. The app does not measure the reduction or control emissions from other devices. Wi-Fi and Bluetooth may stay on according to your phone's settings; check their switches if you want them off too. Pocket detection and connection exceptions determine whether airplane mode activates.

### Can I use Bluetooth headphones or a running watch?

If you select your headphones or watch as a connection exception, their active connection prevents Pocket Flight from enabling airplane mode. To allow airplane mode while using them, leave that device out of your exception list and use Android's option to keep Bluetooth on in airplane mode. Bluetooth will continue transmitting. Download any music or podcasts before heading out if you plan to listen without mobile data.

### Does Pocket Flight work with a Samsung Galaxy Fold?

Version 1.1 has one successful user report on a Galaxy Z Fold8 running Android 17. Optional Pocket assist was added after proximity detection worked under a hand but missed the folded phone in a pocket. Results depend on sensors, clothing, firmware and background settings; follow the [phone test checklist](#physical-verification-on-your-phone) for your device.

### Does automatic airplane mode save battery?

Battery savings have not been measured. Switching radios off can reduce their activity, but monitoring sensors and keeping the CPU awake when needed consumes power. Pocket assist can add to that cost. Treat Pocket Flight as a connectivity automation tool and compare battery use on your own phone.

### Why does it need location permission for Wi-Fi exceptions?

Android restricts access to Wi-Fi network names using location permissions and the system Location switch. Pocket Flight needs that access to match your selected networks while locked. It does not collect GPS coordinates or send your network names to a server.

### Does removing the phone from my pocket turn airplane mode off?

Unlocking does. Once Pocket Flight enables airplane mode, taking the phone out or waking the lock screen alone does not turn it off. You can also use **Stop monitoring** or the notification's **Pause & restore**. If Shizuku stops while airplane mode is on, turn airplane mode off manually in Quick Settings.

## Real limitations

**A proximity sensor detects coverage, not a pocket.** A hand, bag, case or face-down phone can also satisfy it. Sensor behavior can vary between displays and firmware. Version 1.1 has one successful user report on a Galaxy Z Fold8 / Android 17, 36 passing unit tests and zero Android lint errors; this does not establish compatibility with all phones or cover every checklist scenario.

The service prefers wake-up sensors. If proximity, or a sensor required by Pocket assist, cannot wake the CPU, Pocket Flight holds a partial CPU wake lock while monitoring with the screen off and airplane mode off. That improves reliability but uses additional battery, especially with Pocket assist enabled. The pocket report identifies sensor types. Some virtual sensors may still stop producing useful readings with the screen off; missing readings prevent the relevant trigger.

Android does not allow normal apps to toggle airplane mode. Pocket Flight calls the system connectivity command through a Shizuku UserService. OEM or Android updates can restrict this capability or Bluetooth's privileged connection query. Bluetooth query failures block entry rather than guessing.

If Shizuku is killed, loses permission or cannot execute a command while airplane mode is on, **unlock alone cannot restore connectivity**. Turn airplane mode off in Quick Settings and restart Shizuku. Force-stopping Pocket Flight also prevents automatic restoration until it is opened again. Pause and restore before uninstalling.

Android controls which radios airplane mode disables and can remember that Wi-Fi or Bluetooth should remain enabled. Pocket Flight changes airplane mode; it does not override those system radio preferences. Cellular calls and mobile data are interrupted while airplane mode is on.

## Physical verification on your phone

| Test | Expected result |
| --- | --- |
| Folded and unfolded, cover/uncover sensor in its live test | Clear and Covered change reliably |
| No selected connection; lock and pocket for more than the delay | Airplane mode turns on; unlock turns it off |
| Lock on a table with sensor clear | Airplane mode stays off |
| Cover briefly, then uncover before delay | Airplane mode stays off |
| Cover while unlocked, or while lock screen is lit | Airplane mode stays off |
| Unlock during the confirmation delay | Airplane mode stays off |
| Connected to selected Wi-Fi before starting monitor | Airplane mode stays off when pocketed |
| Connected to selected Bluetooth before starting monitor | Airplane mode stays off when pocketed |
| Selected Wi-Fi unavailable, selected Bluetooth connected | Airplane mode stays off |
| Paired Bluetooth disconnected; no selected Wi-Fi connected | Normal pocket trigger works |
| Revoke location / disable Location with selected Wi-Fi connected | Entry is blocked with an explanatory status |
| Pause while app-owned airplane mode is on | Airplane mode turns off |
| Manually enable airplane mode, then unlock | Your manual setting is preserved |
| Stop/restart Shizuku; restart phone | Monitor reports missing access and resumes after Shizuku is restarted |
| Leave locked for several minutes, then pocket | Sensor still detects pocket in normal screen-off operation |
| Enable Pocket assist; lock and pocket the folded phone for 15 seconds | Dark + tilted readings can trigger even with proximity clear |
| Pocket assist enabled, proximity clear, phone flat in a dark room | Fallback does not trigger |
| Return after a failed pocket test | Last pocket report contains readings and blocking conditions |

Use Quick Settings to inspect the airplane icon without unlocking. Wait a few seconds after unlock for the system command and radio reconnection. Test restoration before relying on unattended operation.

## Build and tests

Requires JDK 17, Android SDK platform 35 and build-tools 35.0.0. The app has `minSdk 30` (Android 11), targets API 35, and uses APIs available on newer Android versions through compatibility behavior. It contains no native binaries. Targeting API 35 does not by itself certify Samsung Android 17 compatibility.

Set `JAVA_HOME` and `ANDROID_HOME`, or create a local `local.properties` with `sdk.dir=/path/to/android-sdk`. Then:

```powershell
.\gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

On macOS/Linux use `sh ./gradlew` instead. The Gradle wrapper downloads Gradle 8.11.1 if needed. First build downloads dependencies from Google/Maven Central.

The published personal-use APK is non-debuggable and signed with the original development key. That key is not included in this repository. Building from source uses your own Android development keystore (`~/.android/debug.keystore`), so a self-built APK cannot update the published APK unless signed with the same original key. Retain your signing key for future updates. For broader distribution, configure a dedicated release signing key.

- APK: `app/build/outputs/apk/release/app-release.apk`
- Unit test report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Android lint report: `app/build/reports/lint-results-debug.html`
- Policy: `FlightPolicy.java`; sensor interpretation and timing: `PocketDetector.java`; sensor lifecycle and commands: `PocketService.java`
- Privileged, fixed-command interface: `FlightBridgeService.java`

Tests exercise entry guards, restoration precedence, manual ownership, exception uncertainty, Wi-Fi name handling, darkness/tilt conditions, stale or invalid readings, flicker timing, timer resets and diagnostic retention. Hardware sensing, Shizuku's shell permissions, Samsung background behavior and actual radio changes require the phone checklist above.

Version 1.2 also has Robolectric checks for the native UI, settings navigation, saved preferences, restoration controls and animation lifecycle, plus pure tests that keep restoration and setup errors visible on the home screen. Native-rendered UI previews are written under `app/build/ui-preview/` during the UI tests. These checks do not replace a test on your physical phone.

## Platform references

- [Android global settings restrictions](https://developer.android.com/reference/android/provider/Settings.Global)
- [Shizuku API and UserService guide](https://github.com/RikkaApps/Shizuku-API)
- [Wi-Fi name permission/redaction behavior](https://developer.android.com/reference/android/net/wifi/WifiInfo)
- [Android special-use foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
- [Android 17 changes affecting all apps](https://developer.android.com/about/versions/17/behavior-changes-all)

Shizuku API/provider dependencies are distributed under their upstream MIT license.
