# ADR-001: Universal WatchAdapter plugin architecture

- **Status:** Accepted
- **Phase:** 0 (Foundation)
- **Author:** Mridhul (Architect)

## Context

FuseMind was initially scoped for the Samsung Galaxy Watch 6 only (~15M
devices). That caps the audience and would force a rewrite to add any other
brand. But all BLE smartwatches share the same transport, and ANCS (the
iPhone-side notification service) is platform-agnostic — it does not care which
watch reads it. There are ~150M+ non-Apple watch users on iPhone across Wear OS,
Garmin, Fitbit, and Amazfit.

## Decision

Define a single `WatchAdapter` interface (iOS side) plus a mirrored watch-side
contract, both bound by one BLE protocol (`docs/ble-protocol.md`). Every watch
platform is a **plugin** that implements the contract. The iOS app and AI agent
are platform-blind — they talk only to the interface.

## Alternatives considered

1. **Samsung-only build** — rejected: limits audience; expansion means rewrites.
2. **Platform conditionals** (`if samsung … else garmin …`) — rejected: turns
   the core into spaghetti and couples it to every brand.
3. **Separate iOS apps per platform** — rejected: maintenance nightmare,
   fragmented user experience.

## Consequences

**Positive**
- Any BLE watch is supported by adding one adapter.
- The community can contribute platforms via pull requests.
- The iOS core never changes when a platform is added (Open–Closed Principle).
- A shared contract test suite validates every adapter the same way.

**Negative**
- More up-front design work in Phase 0 (defining the interface + protocol).
  *Mitigation:* small, one-time cost; pays back on the second platform onward.

## Patterns referenced

- Hardware Abstraction Layer (Android)
- Strategy Pattern (Gang of Four)
- Open–Closed Principle (SOLID)
