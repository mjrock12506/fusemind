# Phase 1 — BLE Bridge

**Goal:** the watch and iPhone establish a stable BLE connection and stay
connected, auto-reconnecting when they go out of range.
**Status:** ⏭️ next · **Depends on:** Phase 0 (✅) · **Milestone tag:** `phase-1-ble`
**Reference:** `docs/ble-protocol.md` (UUIDs + payloads), `WatchAdapter.swift`,
`FuseMindWatchService.kt`.

This is the most important phase — every later feature rides on this link.
Build the watch GATT server and the iOS central, then make the connection
durable. No notifications or health yet; just a rock-solid pipe.

---

## P1-001 — Wear OS GATT server advertises the FuseMind service
**Where:** `watch-adapters/wear-os/app/.../FuseMindWatchService.kt`
**Do:** Implement `startGattServer()` — open a `BluetoothGattServer`, register
`FuseMindGatt.SERVICE` with all six characteristics from the protocol doc, and
begin BLE advertising the service UUID. Hold a `WakeLock` so advertising
survives screen timeout.
**Acceptance:**
- [ ] Watch advertises the service UUID on app start.
- [ ] Service is visible in a generic BLE scanner app (e.g. nRF Connect).
- [ ] Advertising continues after the watch screen turns off.
- [ ] All six characteristics are registered with correct properties.

## P1-002 — iOS CoreBluetooth central discovers and connects
**Where:** `core/fusemind-ios/` (new `BLEWatchAdapter.swift` implementing
`WatchAdapter`).
**Do:** Implement `connect()` — a `CBCentralManager` that scans for the FuseMind
service UUID, connects to the watch, discovers services/characteristics, and
reads the `Capabilities` characteristic once. Wire `state` through the
`ConnectionState` enum. Enable CoreBluetooth **background mode** so the link
survives the app being backgrounded.
**Acceptance:**
- [ ] iPhone discovers the watch within 10s of both apps running.
- [ ] Connection completes (handshake) in < 5s.
- [ ] `capabilities()` is populated from the watch after connect.
- [ ] Connection survives the iOS app going to background / lock screen.

## P1-003 — Connection persistence + auto-reconnect
**Where:** both sides.
**Do:** Detect disconnects on both ends. On the iOS side, retain the peripheral
and re-initiate connection when it comes back in range. On the watch side,
restart advertising if the link drops. Surface `degraded` state while
disconnected.
**Acceptance:**
- [ ] Walking out of range → state becomes `degraded`, not `disconnected`.
- [ ] Returning in range → auto-reconnects in < 10s, no user action.
- [ ] Restarting Bluetooth on either device recovers the link.
- [ ] A 30-minute idle connection stays up (no silent drop).

## P1-004 — Connection-status UI on both devices
**Where:** iOS SwiftUI view + Wear OS Compose view.
**Do:** A simple indicator on each device showing Connected / Connecting /
Degraded / Disconnected, driven by the connection state.
**Acceptance:**
- [ ] Both devices show the current state live.
- [ ] State updates within ~1s of an actual change.

---

## Definition of Done (Phase 1)
- [ ] Pair in < 5s on a real Samsung Watch 6 Classic + iPhone.
- [ ] Connection survives background, screen-off, and a 30-min idle.
- [ ] Auto-reconnect in < 10s after going out of and back into range.
- [ ] No platform-specific code leaked into anything outside the adapter.
- [ ] Tagged `phase-1-ble`; this issue's boxes all checked.

## Notes
- Request a larger MTU on connect — notification bodies (Phase 2) exceed the
  23-byte default.
- Emulator BLE is unreliable; test on real hardware from the start.
- Keep `BLEWatchAdapter` conforming strictly to `WatchAdapter` so future
  platforms slot in unchanged.
