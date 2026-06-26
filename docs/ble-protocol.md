# FuseMind BLE Protocol Specification

**Status:** Phase 0 — contract frozen for Phase 1 implementation
**Audience:** anyone writing a watch adapter (iOS-side or watch-side)

This is the single source of truth both sides agree on. The iPhone app and
every watch companion app (Wear OS, Garmin, Fitbit, Amazfit) implement these
exact UUIDs and payloads. Get this right and any watch works with any iPhone.

---

## Custom GATT Service

| Item | UUID |
|---|---|
| **FuseMind Service** | `F0000000-0000-1000-8000-00805F9B34FB` |

The watch advertises this service. The iPhone scans for it and connects as
GATT central; the watch acts as GATT peripheral/server.

## Characteristics

| Name | UUID | Direction | Properties | Payload |
|---|---|---|---|---|
| Notification Push | `F0000001-…` | phone → watch | Write, Notify | JSON `Notification` |
| Notification Dismiss | `F0000002-…` | watch → phone | Write | UTF-8 notification `id` |
| Call Control | `F0000003-…` | bidirectional | Write, Notify | JSON `CallEvent` |
| Media Command | `F0000004-…` | watch → phone | Write | UTF-8 enum (see below) |
| Health Data | `F0000005-…` | watch → phone | Read, Notify | JSON `HealthSnapshot` |
| Capabilities | `F0000006-…` | watch → phone | Read | JSON `WatchCapabilities` |

(Each `…` is `0000-1000-8000-00805F9B34FB`.)

---

## Payload schemas (JSON, UTF-8)

### Notification (phone → watch)
```json
{ "id": "ancs-4821", "appName": "WhatsApp", "title": "Sara",
  "body": "On my way", "aiPriority": "H", "timestamp": 1716700000 }
```
`aiPriority` is one of `"H"`, `"M"`, `"L"` (set by the AI agent).

### CallEvent (bidirectional)
```json
{ "action": "ring", "caller": "Dad", "number": "+44…" }
```
`action` ∈ `ring | accept | reject | end`.

### Media Command (watch → phone)
UTF-8 string, one of: `play | pause | next | previous | volumeUp | volumeDown`.

### HealthSnapshot (watch → phone)
```json
{ "heartRate": 62, "hrv": 45.0, "steps": 4200,
  "sourceAdapter": "wear_os", "recordedAt": 1716700000 }
```
Any field may be `null` if the watch lacks that sensor.

### WatchCapabilities (watch → phone, read once on connect)
```json
{ "hasMic": true, "hasSpeaker": true, "hasGPS": true,
  "hasHRM": true, "maxNotifLength": 120 }
```
The iPhone reads this **before** using any feature. Example: if `hasSpeaker`
is `false` (e.g. most Garmin watches), the phone never opens a call-audio
channel and shows "answer on phone" instead. This is graceful degradation,
not an error.

---

## Connection lifecycle

1. Watch boots → starts GATT server → advertises FuseMind Service.
2. iPhone scans → discovers service → connects (target: < 5 s).
3. iPhone reads `Capabilities` once.
4. Steady state: notifications/calls/media flow over BLE; health pushed on a
   timer.
5. Out of range → `degraded`; back in range → auto-reconnect (target: < 10 s).

## Notes for adapter authors

- **MTU:** request a larger MTU on connect; notification bodies can exceed the
  23-byte default. Chunk if the platform caps MTU.
- **Audio:** voice-call audio uses BLE SCO, negotiated separately from this
  GATT service. Only relevant when `hasMic && hasSpeaker`.
- **Files/images:** NOT carried over BLE (too slow). The phone and watch switch
  to Wi-Fi/LAN for transfers; this spec covers control + small payloads only.
