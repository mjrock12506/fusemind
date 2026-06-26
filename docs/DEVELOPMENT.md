# FuseMind — Development Plan

Work is organised into **phases gated by completion, not dates**. A phase ends
when its Definition of Done is met. Go faster, the next phase simply starts
sooner. Each phase maps to a milestone tag in the repo.

> Build for **any BLE smartwatch** from the start. Wear OS is the first adapter
> (it covers Samsung, Pixel Watch, Fossil, TicWatch, Mobvoi in one codebase);
> other platforms plug in later via the same `WatchAdapter` contract.

---

## Phase 0 — Foundation  ✅ *(this scaffold)*

**Goal:** a professional repo anyone can clone and build against.

Deliverables
- Repo with plugin folder structure
- `WatchAdapter` interface (iOS) + watch-side contract (Wear OS)
- BLE protocol spec (`docs/ble-protocol.md`)
- CI pipeline (lint + build placeholders)
- README, CONTRIBUTING, LICENSE, ADR-001

**Definition of Done:** `git clone` on a fresh machine; CI is green; the
interface and protocol are documented and reviewed.

---

## Phase 1 — BLE Bridge

**Goal:** watch and iPhone connect over BLE and stay connected.

- Wear OS GATT server advertises the FuseMind service
- iOS CoreBluetooth central discovers and connects
- Connection persistence + auto-reconnect after going out of range
- Connection-status UI on both sides

**DoD:** pair in < 5 s on real hardware; survive screen-off and background;
auto-reconnect in < 10 s. Milestone tag: `phase-1-ble`.

---

## Phase 2 — Notifications

**Goal:** every iPhone notification appears on the watch in < 2 s.

- ANCS reader on iOS (the hardest single piece)
- Notification relay over the FuseMind GATT characteristic
- Notification list + detail UI on the watch
- Dismiss on watch → clears on iPhone

**DoD:** 50 notifications across 10 apps deliver accurately in < 2 s; dismissal
syncs both ways. Tag: `phase-2-notifications`.

---

## Phase 3 — Calls  🏁 *MVP*

**Goal:** answer and speak on a call from the watch.

- CallKit integration on iOS
- Accept/reject from watch
- BLE SCO audio routing (watch mic + speaker)
- In-call UI (timer, mute, end)

**DoD:** call setup < 3 s; audio latency < 200 ms; graceful "answer on phone"
when the watch has no speaker. **This is the MVP.** Tag: `phase-3-mvp`.

---

## Phase 4 — AI Brain

**Goal:** intelligence the bridge competitors don't have.

- Groq LLM integration in the agent
- Notification priority classifier (H/M/L)
- Smart-filter rules + user preferences
- Context awareness (meeting / workout / sleep)
- AI quick-reply suggestions

**DoD:** classifier ≥ 90% sensible on a hand-labelled set; noisy notifications
suppressed in focus mode. Tag: `phase-4-ai`.

---

## Phase 5 — Health Sync

**Goal:** watch + Whoop data flow into Apple Health with insights.

- Wear OS Health Services → HealthSnapshot
- Whoop REST API (recovery / strain / sleep)
- Write to Apple HealthKit
- Daily AI health insight

**DoD:** health syncs every 15 min with > 99% success; Whoop fused; insight
written to HealthKit. Tag: `phase-5-health`.

---

## Phase 6 — Media + Files  ✅ *v1.0 feature-complete*

**Goal:** control phone media and move files from the watch.

- Media control over BLE (play/pause/skip/volume)
- Wi-Fi file/image transfer (watch ↔ phone)
- Image thumbnail preview on watch

**DoD:** all seven core features working and QA-tested. **v1.0 feature
freeze.** Tag: `phase-6-v1`.

---

## Phase 7 — Voice AI

**Goal:** speak a command, the phone executes it.

- Watch mic capture + stream over BLE
- Groq Whisper STT
- LLM intent parsing + action router
- 20+ action intents

**DoD:** round trip < 800 ms; ≥ 90% intent accuracy on the supported set.
Tag: `phase-7-voice`.

---

## Phase 8 — Open-Source Launch  🚀

**Goal:** a public v1.0 the community can use and extend.

- Watch-face complication (AI summary)
- Settings UI on both apps
- Full setup + API + architecture docs
- "Add an adapter" contribution guide
- v1.0 GitHub release

**DoD:** docs complete; a stranger can install via AltStore/Scarlet and build
from source. Tag: `v1.0.0`.

---

## Phase 9 — Garmin Adapter

**Goal:** prove the plugin model with a second platform.

- Connect IQ app (Monkey C) implementing the watch-side contract
- Garmin Health API → HealthSnapshot
- Pass the shared adapter contract tests

**DoD:** a Garmin watch gets notifications + health with zero changes to the
iOS core. Tag: `phase-9-garmin`.

---

## Phase 10 — Community Adapters  🌍

**Goal:** an ecosystem, not a project.

- Amazfit (Zepp OS) and Fitbit OS adapters — community-led
- Maintainer reviews PRs against the contract test suite

**DoD:** ≥ 1 community-contributed adapter merged. Ongoing.

---

### Role rotation (solo)

You wear every hat, rotating by activity rather than by calendar:
**Architect** (interface/protocol decisions) → **Developer** (build) →
**QA** (test against DoD) → **Product Owner** (accept the phase, write the next
issues). Each role is real experience and a real interview answer.
