<div align="center">

# FuseMind

**Any watch. Every iPhone. One mind.**

An open-source universal bridge that connects *any* BLE smartwatch to an iPhone —
with an AI agent at its core.

`Apache-2.0` · Wear OS · Garmin · Fitbit · Amazfit → iPhone

</div>

---

## Why

Non-Apple smartwatches (Samsung, Garmin, Fitbit, Amazfit, Fossil) have no
official, full-featured way to work with iPhone. Apple keeps watch-grade
integration for Apple Watch. FuseMind closes that gap for an estimated **150M+
people** who own a non-Apple watch and an iPhone — and adds intelligence no
bridge has done before.

## What it does

- **Live notifications** on your wrist in under 2 seconds
- **Calls** — answer and speak through the watch
- **Health sync** — watch + Whoop data into Apple Health
- **Media controls** — play/pause/skip from the watch
- **File transfer** over Wi-Fi
- **Voice AI** — speak a command, your phone acts
- **AI brain** — smart filtering, health insights, context awareness

## How it works

Three transport channels, each tuned for its job:

| Channel | Carries | Notes |
|---|---|---|
| **BLE / ANCS** | notifications, calls, commands | always on, no internet, ~10 m |
| **Wi-Fi / LAN** | files, images, bulk health | fast, same network or hotspot |
| **WebSocket** | AI sync away from home | via iPhone 4G/5G (later phase) |

In the first phase the **iPhone is the brain** — no server, no cost.

## Universal by design

Every watch platform implements one interface — `WatchAdapter`. The iOS app and
AI agent never know which brand is connected. Adding a new watch = one adapter,
the core is never touched. Same idea as Android's Hardware Abstraction Layer.
See [`docs/adr/ADR-001-adapter-architecture.md`](docs/adr/ADR-001-adapter-architecture.md).

## Repository layout

```
core/
  fusemind-ios/      Swift · CoreBluetooth · CallKit · HealthKit  (the brain)
  fusemind-agent/    Python · FastAPI · LangChain · Groq          (the AI)
  fusemind-relay/    Node.js · WebSocket                          (later phase)
watch-adapters/
  wear-os/           Kotlin — Samsung, Pixel Watch, Fossil, TicWatch
  garmin/            Monkey C — later phase
  fitbit/            JS — later phase
  amazfit/           Zepp OS — later phase
docs/
  ble-protocol.md    the shared contract every adapter implements
  DEVELOPMENT.md     the phase-by-phase plan
  SETUP.md           local environment setup
  adr/               architecture decision records
plugins/             community-contributed adapters
```

## Getting started

See [`docs/SETUP.md`](docs/SETUP.md) for local setup, then
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) for the build plan.

## Contributing

New watch platforms are welcome — see [`CONTRIBUTING.md`](CONTRIBUTING.md).

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
