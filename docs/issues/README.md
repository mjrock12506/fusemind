# FuseMind — Phase Issues

Task specs for each development phase. Work through a phase ticket by ticket;
each phase ends when its Definition of Done is met (see `docs/DEVELOPMENT.md`).
IDs (e.g. `P1-001`) are stable references for commits and PRs.

| Phase | File | Goal | Status |
|---|---|---|---|
| 0 | — | Foundation: repo, interface, protocol, CI | ✅ done |
| 4/5/7 (agent) | — | AI agent: classifier, health, voice | ✅ done (`core/fusemind-agent/`) |
| **1** | [phase-1-ble.md](phase-1-ble.md) | BLE bridge: watch ↔ iPhone connect | ⏭️ next |
| 2 | [phase-2-notifications.md](phase-2-notifications.md) | Notifications on the watch | planned |
| 3 | [phase-3-calls.md](phase-3-calls.md) | Calls on the watch (🏁 MVP) | planned |
| 5 | (in agent + iOS) | Health sync to Apple Health | agent done; iOS integration pending |
| 6 | (to spec) | Media controls + file transfer (✅ v1.0) | planned |
| 7 | (in agent + iOS) | Voice AI | agent done; iOS/watch wiring pending |
| 8 | (to spec) | OSS launch (🚀) | planned |
| 9 | (to spec) | Garmin adapter | planned |
| 10 | (to spec) | Community adapters (🌍) | ongoing |

## Order of work
Phase 1 → 2 → 3 gets you a shippable MVP (notifications + calls). The AI agent
(Phases 4/5/7) is already built — its iOS-side integration happens within
Phases 2, 5, and 7. Later phase specs (6, 8, 9) will be written when their
prerequisites land.

## Using these with Claude Code
See `docs/PROMPTS.md` for ready-to-paste prompts that point Claude Code at the
current phase. Always: build incrementally, run tests, commit per ticket, and
keep the iOS core platform-agnostic.
