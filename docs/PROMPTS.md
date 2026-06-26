# Claude Code Prompts

Paste these into Claude Code (run `claude` in the repo root) to drive each phase.
Claude Code reads `CLAUDE.md` automatically, so it already knows the
architecture, the golden rules, and where things live. Each prompt points it at
a phase's task spec.

> Tip: work one ticket at a time. After each ticket, review the diff, run the
> tests, and commit before moving on. Don't run unattended bypass mode on your
> Mac — use the default review flow (or `--permission-mode dontAsk` with a tight
> allowlist) so you stay in the loop and keep learning.

---

## Phase 1 — BLE Bridge (do this first)

```
Read CLAUDE.md and docs/issues/phase-1-ble.md and docs/ble-protocol.md.

Implement Phase 1 ticket by ticket, starting with P1-001 (the Wear OS GATT
server). Match the BLE protocol UUIDs and payloads exactly. Keep the iOS side
strictly conforming to the WatchAdapter interface — no Wear-OS-specific code in
the iOS core.

For each ticket: explain your approach briefly first (I'm learning), implement
it, then tell me how to test it on my Samsung Watch 6 + iPhone. Stop after each
ticket so I can review and commit. Do not start P1-002 until I say P1-001 works
on hardware.
```

## Phase 2 — Notifications

```
Phase 1 is complete and the BLE link is stable. Read
docs/issues/phase-2-notifications.md and docs/ble-protocol.md.

Implement Phase 2 ticket by ticket, starting with P2-001 (the ANCS reader).
Use the existing agent /classify endpoint for priority, with a local fallback if
the agent is unreachable. Explain each step, implement, then give me a test plan.
Stop after each ticket for review and commit.
```

## Phase 3 — Calls (MVP)

```
Phases 1 and 2 are complete. Read docs/issues/phase-3-calls.md and
docs/ble-protocol.md.

Implement Phase 3. Prototype P3-003 (BLE SCO audio) early since it's the
riskiest. Always check capabilities() before routing call audio — speaker-less
watches must fall back to "answer on phone". Explain, implement, give a test
plan, stop per ticket. When the phase is done, help me record a short MVP demo.
```

---

## Smaller, useful one-off prompts

**Wire the agent into the iOS app (after Phase 2):**
```
The Python agent in core/fusemind-agent exposes /classify, /health/insight,
/voice/transcribe, /voice/intent. Add a small Swift client in the iOS app that
calls these, with a timeout and a graceful fallback when the agent is
unreachable. Keep it platform-agnostic. Explain the design, then implement.
```

**Add the pytest step to CI (quick):**
```
Edit .github/workflows/ci.yml: in the `agent` job, after the import check, add a
step that runs `python -m pytest -q` in core/fusemind-agent. Show me the diff.
```

**Start a new watch adapter later (e.g. Garmin):**
```
Read docs/ble-protocol.md and CONTRIBUTING.md. Scaffold a new Garmin adapter in
watch-adapters/garmin that implements the watch-side contract in Monkey C,
speaking the same FuseMind GATT protocol. Report capabilities honestly (Garmin
watches usually have no speaker). Do not change the iOS core.
```

---

## Working agreement (why the "explain first, stop per ticket" style)

This project doubles as a learning exercise — the reasoning matters as much as
the code. Asking Claude Code to explain its approach and pause for review per
ticket keeps you in control, builds your understanding of BLE/ANCS/CallKit, and
keeps the architecture clean. Speed comes from clarity, not from skipping review.
