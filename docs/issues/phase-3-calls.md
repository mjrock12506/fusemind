# Phase 3 — Calls  🏁 MVP

**Goal:** answer and speak on a call directly from the watch.
**Status:** after Phase 2 · **Milestone tag:** `phase-3-mvp`
**Reference:** `docs/ble-protocol.md` (Call Control characteristic),
`WatchCapabilities` (mic/speaker checks).

This phase completes the MVP. The hardest piece is BLE SCO audio routing —
prototype it early. Watches without a speaker (checked via `capabilities()`)
fall back to "answer on phone".

---

## P3-001 — CallKit integration on iOS
**Where:** `core/fusemind-ios/`.
**Do:** Use CallKit to detect incoming calls and surface caller info. Write a
`CallEvent` (`action: ring`, caller, number) to the Call Control characteristic.
**Acceptance:**
- [ ] Incoming call is detected and caller info sent to the watch in < 1s.
- [ ] Caller name/number shown when available.

## P3-002 — Accept / reject from the watch
**Where:** `watch-adapters/wear-os/`.
**Do:** On a `ring` event (and only if `capabilities().hasSpeaker && hasMic`),
show Accept / Reject. Write the chosen `action` back over Call Control; the iOS
side drives CallKit accordingly. If no speaker, show "answer on phone" only.
**Acceptance:**
- [ ] Accept/Reject buttons appear on incoming call (capable watches).
- [ ] Tapping drives the call on the iPhone correctly.
- [ ] Speaker-less watches show the fallback, no broken audio.

## P3-003 — BLE SCO audio routing (the hard one)
**Where:** iOS audio session + watch audio.
**Do:** On accept, open a BLE SCO channel so the watch mic and speaker carry the
call. Route `AVAudioSession` appropriately. Target latency < 200ms.
**Acceptance:**
- [ ] Two-way audio works through the watch mic + speaker.
- [ ] Latency < 200ms; no persistent echo or one-sided audio.
- [ ] Ending on the watch ends the call on the iPhone.

## P3-004 — In-call UI on the watch
**Where:** `watch-adapters/wear-os/`.
**Do:** Active-call screen: running timer, mute toggle, end-call.
**Acceptance:**
- [ ] Timer counts; mute and end work and reflect on the iPhone.

---

## Definition of Done (Phase 3 = MVP)
- [ ] Call setup < 3s; audio latency < 200ms on real hardware.
- [ ] Graceful "answer on phone" for speaker-less watches.
- [ ] Notifications (Phase 2) + Calls both working = **MVP shippable.**
- [ ] Record a short demo; tag `phase-3-mvp`.
- [ ] Try the same build on a second Wear OS device (Pixel Watch / Fossil) to
  prove the adapter is truly platform-agnostic.
