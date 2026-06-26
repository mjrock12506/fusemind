# NOTES — Phase 1 BLE scaffolding: what to test on hardware tomorrow

Phase 1 tickets **P1-001** (Wear OS GATT server) and **P1-002** (iOS
`BLEWatchAdapter`) are written but **NOT hardware-tested**. BLE behaves
differently on real devices than emulators (CLAUDE.md golden rule #6), so
nothing below is "done" until it works on a **real Samsung Watch 6 + iPhone**.

What landed:
- `watch-adapters/wear-os/.../FuseMindWatchService.kt` — GATT server, six
  characteristics, advertising, WakeLock. Plus `AndroidManifest.xml` permissions.
- `core/fusemind-ios/.../BLEWatchAdapter.swift` — CoreBluetooth central, scan →
  connect → discover → read Capabilities. Plus `Package.swift` and an
  `App/Info.plist` with the background-mode + Bluetooth usage keys.

---

## 0. Compile first (before any device)
- [ ] **iOS:** `cd core/fusemind-ios && swift build` — the sandbox here blocked
      it, so this has **not** been run. Confirm it compiles. (CoreBluetooth is
      available on the macOS target.)
- [ ] **Wear OS:** the Kotlin file has **no Gradle project yet** — `build.gradle`,
      `settings.gradle`, an `Activity`/foreground `Service` to instantiate
      `FuseMindWatchService(applicationContext)` and call `startGattServer()`,
      and the runtime-permission request flow. Stand that up in Android Studio,
      then build/install via `adb`. The CI `wear-os` job also still needs a real
      build step.

## 1. P1-001 — Watch advertises the service
- [ ] On app start the watch advertises `F0000000-0000-1000-8000-00805F9B34FB`.
- [ ] **nRF Connect** (or any generic BLE scanner) sees the watch and lists the
      FuseMind service with **all six** characteristics and the expected
      properties (Push: Write+Notify; Dismiss: Write; Call: Write+Notify;
      Media: Write; Health: Read+Notify; Capabilities: Read).
- [ ] Reading **Capabilities** in nRF Connect returns the JSON
      `{"hasMic":true,...,"maxNotifLength":120}`.
- [ ] Advertising **continues after the watch screen turns off** (WakeLock).
      Let the screen sleep, then re-scan — still visible.
- [ ] Grant the runtime BLE permissions (`BLUETOOTH_ADVERTISE`,
      `BLUETOOTH_CONNECT`) on first run; confirm denial is handled, not crashed.

## 2. P1-002 — iPhone discovers + connects
- [ ] iPhone discovers the watch within **10s** of both apps running.
- [ ] Connection + handshake (Capabilities read) completes in **< 5s**.
- [ ] `capabilities()` returns the watch's real values after connect (verify the
      mic/speaker/GPS/HRM flags and `maxNotifLength` decode correctly).
- [ ] Grant the iOS Bluetooth permission prompt; confirm the usage string shows.
- [ ] **Background survival:** background the app / lock the phone, confirm the
      link stays up (relies on `bluetooth-central` + the restore identifier).
- [ ] Confirm iOS state restoration actually relaunches into the background
      (kill from multitasking is expected to end it; a background *suspend*
      should not).

## 3. Cross-checks while paired
- [ ] **Capabilities round-trip** matches byte-for-byte between Kotlin
      `WatchCapabilities.toJson()` and Swift `WatchCapabilities` decode.
- [ ] **MTU:** watch logs `onMtuChanged` to a value > 23 after connect. (iOS
      negotiates MTU automatically; we didn't call an explicit API — confirm the
      negotiated size is large enough for Phase-2 notification bodies, else we
      must chunk.)
- [ ] CCCD subscribe works: when the phone subscribes to Health/Notify chars,
      the watch logs the `subscribe` and adds the central to `subscribers`.

## 4. Known gaps / decisions to confirm (not bugs — design questions)
- [ ] **Dismiss + Media properties.** The protocol doc lists these as
      `watch → phone` but property **Write** only. A GATT *server* (the watch)
      can't "write" to a central — it must **Notify**. I kept the doc's declared
      properties verbatim (golden rule #2) and left a `notifyCharacteristic`
      path + TODO. **Decide:** add `Notify` (and a CCCD) to Dismiss/Media and
      update `docs/ble-protocol.md`, or model these differently. Phase 1 doesn't
      exercise them, so this isn't blocking — but resolve before Phase 2/3.
- [ ] **P1-003 (persistence/auto-reconnect)** is only partially stubbed: iOS
      retains the peripheral and re-issues `connect()` on disconnect (→ `degraded`);
      the watch keeps advertising. Not yet tested for the out-of-range → back-in-range
      < 10s target, the 30-min idle hold, or a Bluetooth restart on either device.
- [ ] **P1-004 (status UI)** not built on either side. The iOS adapter exposes an
      `onStateChange` hook ready for a SwiftUI view; the watch has no Compose
      indicator yet.
- [ ] **Watch selection:** iOS connects to the *first* FuseMind advertiser it
      sees. Fine for a one-watch bench test; needs identity/pairing logic before
      real use.

## 5. Definition of Done reminder (from docs/issues/phase-1-ble.md)
Pair < 5s on a real Samsung Watch 6 Classic + iPhone · survives background,
screen-off, 30-min idle · auto-reconnect < 10s after out-and-back range · no
platform-specific code outside the adapter · tag `phase-1-ble`.
