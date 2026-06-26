# CLAUDE.md — FuseMind project guide for Claude Code

This file is read automatically by Claude Code. It is the single source of
truth for how to work in this repo. Read `docs/` for depth.

## What FuseMind is

An open-source universal bridge connecting **any BLE smartwatch** (Wear OS,
Garmin, Fitbit, Amazfit) to an **iPhone**, with an AI agent at its core.
Tagline: *Any watch. Every iPhone. One mind.* License: Apache-2.0.

The product gives non-Apple watches the features Apple reserves for Apple
Watch: live notifications, calls on the wrist, health sync to Apple Health,
media control, file transfer, and voice actions.

## Golden rules (do not violate)

1. **The adapter pattern is sacred.** The iOS app and AI agent must NEVER
   contain watch-platform-specific code. All platform differences live behind
   the `WatchAdapter` interface (`core/fusemind-ios/.../WatchAdapter.swift`) and
   the watch-side companion apps in `watch-adapters/`. Adding a watch brand =
   one new adapter, zero core changes. See `docs/adr/ADR-001-adapter-architecture.md`.
2. **The BLE protocol is the contract.** `docs/ble-protocol.md` defines the
   exact GATT service UUID, characteristic UUIDs, and JSON payloads. Both sides
   must match it byte-for-byte. Do not invent new UUIDs without updating that doc.
3. **Graceful degradation via `capabilities()`.** Always check what a watch can
   do before using a feature. No speaker → no call audio, show "answer on phone"
   instead. This is by design, never an error.
4. **LLM-first with a deterministic fallback.** The agent already follows this
   (`core/fusemind-agent/`): try Groq, fall back to a heuristic on any failure or
   missing key. Keep this pattern for any new intelligence.
5. **Never commit secrets.** Keys go in `.env` (git-ignored). Use `.env.example`
   as the template. No keys in code, commits, or logs.
6. **Test on real hardware.** BLE behaves differently on devices than emulators.
   A feature is not done until it works on a real Samsung Watch 6 + iPhone.
7. **The watch reads notifications, not the iPhone app.** iOS gives third-party
   apps NO access to the device-wide notification stream, and Apple's ANCS makes
   the iPhone the GATT *server* and the accessory the GATT *client*. So the
   **watch** is the ANCS consumer (it bonds to the iPhone and reads ANCS service
   `7905F431-B5CE-4E99-A40F-4B1E122D00D0` directly); the iOS core never reads
   notifications. Do not write an "ANCS reader on iOS" — it cannot exist. See
   `docs/adr/ADR-002-ancs-watch-consumer.md`.

## Repository map

```
core/
  fusemind-ios/      Swift · CoreBluetooth · CallKit · HealthKit   (the brain)
    Sources/FuseMindCore/WatchAdapter.swift   ← the universal interface
  fusemind-agent/    Python · FastAPI · Groq   (the AI — BUILT, Phases 4/5/7)
  fusemind-relay/    Node.js · WebSocket       (later phase)
watch-adapters/
  wear-os/           Kotlin — Samsung, Pixel Watch, Fossil, TicWatch
    app/.../FuseMindWatchService.kt            ← watch-side BLE service
  garmin/ fitbit/ amazfit/   (later phases)
docs/
  ble-protocol.md    the shared contract (UUIDs + payloads)
  DEVELOPMENT.md     the phase plan (no dates — completion-gated)
  issues/            per-phase task specs with acceptance criteria
  adr/               architecture decision records (see ADR-002: watch reads ANCS)
  spikes/            throwaway feasibility probes (e.g. ancs-spike-README.md)
spikes/
  ancs-consumer-android/   feasibility spike: can a BLE central read iPhone ANCS?
```

## Conventions

- **Languages by layer:** iOS = Swift/SwiftUI; Wear OS = Kotlin/Jetpack Compose;
  agent = Python (FastAPI); relay = Node.js. Garmin = Monkey C; Amazfit = JS.
- **Commits:** `type(scope): summary` — types `feat fix docs test refactor chore`.
  One logical change per commit.
- **Tickets:** IDs like `P1-001` map to `docs/issues/phase-N-*.md`.
- **Tests:** every feature ships with tests; the agent uses pytest. Don't reduce
  coverage.

## Commands

Agent (works offline; no key needed for fallback mode):
```bash
cd core/fusemind-agent
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m pytest -q          # expect: 12 passed
uvicorn main:app --reload    # http://localhost:8000
```
Real Groq calls: put `GROQ_API_KEY` in `.env`; the agent flips fallback→llm.

iOS (Phase 1+, needs a Mac + Xcode): open `core/fusemind-ios` in Xcode, plug in
the iPhone, Run.

Wear OS (Phase 1+, needs Android Studio): open `watch-adapters/wear-os`, install
to the watch via `adb`.

## Current status

- ✅ **Phase 0 — Foundation:** repo, interface, protocol, CI, docs.
- ✅ **Phases 4/5/7 — AI Agent:** classifier, health fusion, voice intent +
  Whisper transcription, 12 passing tests. Fully built in `core/fusemind-agent/`.
- ⏭️ **Next: Phase 1 — BLE Bridge.** Watch ↔ iPhone connect and stay connected.
  Then Phase 2 (notifications), Phase 3 (calls = MVP). See `docs/issues/`.

## How to work here

- Pick the current phase from `docs/issues/`. Work ticket by ticket.
- Build incrementally; run tests after each ticket; commit per ticket.
- Match the BLE protocol exactly; keep the iOS core platform-agnostic.
- When you make a non-trivial design choice, briefly explain why (and add an ADR
  in `docs/adr/` if it's architectural) — this project doubles as a learning
  exercise, so the reasoning matters as much as the code.
- A phase is done only when its Definition of Done in the issue file is met.
