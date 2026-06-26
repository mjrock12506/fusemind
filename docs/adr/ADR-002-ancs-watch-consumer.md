# ADR-002: The watch is the ANCS consumer (notifications), not the iPhone app

- **Status:** Accepted
- **Phase:** 2 (Notifications)
- **Author:** Mridhul (Architect) · drafted with Claude Code
- **Supersedes nothing; clarifies:** `docs/issues/phase-2-notifications.md` P2-001
  ("ANCS reader on iOS"), which is not implementable as written — see below.

## Context

Phase 2's goal is "every iPhone notification (WhatsApp, Mail, texts, calls)
appears on the watch." The Phase-2 issue spec drafted P2-001 as an **ANCS reader
living in the iOS app** (`core/fusemind-ios/`) that reads the iPhone's own
notifications, classifies them, and pushes them to the watch.

While starting Phase 2 we found that flow cannot work on iOS, for two
compounding reasons:

1. **No device-wide notification access for third-party apps.** iOS exposes no
   public API for an app to read the system-wide notification stream (other
   apps' notifications). A `UNNotificationServiceExtension` only sees *its own
   app's* remote push notifications. This is a deliberate Apple privacy
   boundary — it is why no general "notification-mirroring" app exists on the
   App Store.

2. **ANCS runs the opposite direction.** In Apple's **Apple Notification Center
   Service** specification the **iPhone is the GATT server** (the *Notification
   Provider*) and a **separate bonded accessory is the GATT client** (the
   *Notification Consumer*). ANCS is exposed *to an external accessory* over an
   encrypted/bonded BLE link — never to an app running on the same iPhone. So an
   "ANCS reader on iOS" is a contradiction: the consumer is always the
   accessory, not the phone app.

There is also a **BLE-role conflict** with Phase 1. ANCS requires the iPhone to
act as the GATT server and the watch as the GATT client. Phase 1 (P1-002) made
the iPhone the GATT *central* and the watch the GATT *peripheral/server* for the
custom FuseMind service. Those are opposite GATT roles; the ANCS direction is
the reverse of the FuseMind-service direction.

**Knock-on effect:** because the iPhone app can never see the notification text,
it also cannot run the agent `/classify` step on arbitrary notifications
(P2-002 as drafted). The documented "phone reads → phone classifies → phone
pushes" pipeline cannot exist on iOS.

This is, in fact, how every real third-party watch (Garmin, Amazfit, Fitbit,
Pebble historically) gets iPhone notifications: **the watch itself** bonds to
the iPhone as a BLE accessory and consumes ANCS directly. The phone app is not
in that loop. Notably, ADR-001 already anticipated this — "ANCS … does not care
which watch reads it" — so the *watch-as-consumer* model is the original intent;
the "reader on iOS" wording in the Phase-2 spec was the inconsistency.

## Decision

**Option A — the watch is the ANCS consumer.** The Wear OS companion (and every
future watch adapter) acts as the ANCS *Notification Consumer*: it bonds to the
iPhone, discovers the ANCS service
`7905F431-B5CE-4E99-A40F-4B1E122D00D0`, subscribes to the Notification Source,
and fetches attributes (app id, title, message) over the Data Source. On-device
filtering/priority happens watch-side (the watch can call the FuseMind agent, or
apply a local heuristic, to set H/M/L) rather than on the iPhone.

This is the only path to the **full product promise** — *all* notifications from
*all* apps, filtered on the wrist — and it matches how shipping non-Apple watches
already do it.

Because watch↔iPhone ANCS bonding by a non-Apple (Android/Wear OS) consumer is
uncharted for this project, the decision is gated by a **feasibility spike**
(`docs/spikes/ancs-spike-README.md`, `spikes/ancs-consumer-android/`) that proves
or disproves whether a BLE central can actually receive ANCS from a bonded
iPhone in our setup, *before* we invest in the production Wear OS implementation.

## Alternatives considered

1. **Option A — watch-as-ANCS-consumer (chosen).**
   - *Pros:* the only design that surfaces every iPhone notification; matches how
     real products work; keeps the iOS core thin.
   - *Cons:* re-founds the Phase-1 BLE role model for the notification path
     (iPhone becomes peripheral/GATT-server for ANCS); Wear OS → iPhone ANCS
     bonding may be limited or unsupported by the Wear OS Bluetooth stack —
     hence the spike; classification moves watch-side.

2. **Option B — controlled-source MVP.** Keep the clean Phase-1 architecture, but
   instead of real ANCS, have the iPhone relay notifications from a source it
   *is* allowed to see (its own pushes, or a test feed) to prove the
   classify → relay → render → dismiss pipeline.
   - *Pros:* low cost; builds directly on the working Phase-1 pipe; keeps the AI
     classify step on the iPhone.
   - *Cons:* does **not** deliver the product promise (cannot see other apps'
     notifications); a demo, not the real feature. *Rejected as the end state*
     (may still be used as an interim test harness).

3. **Option C — document-and-defer.** Record the iOS limitation, mark
   all-notification mirroring as not-feasible-on-iOS via the phone app, and ship
   only the watch→phone directions (media, health, dismiss).
   - *Pros:* lowest cost; fully honest.
   - *Cons:* abandons the headline feature. *Rejected.*

## Consequences

**Positive**
- A real path to "all notifications, filtered on-device" — the core promise.
- The iOS core stays platform-blind; it never reads notifications at all.
- Validated cheaply by a throwaway spike before any production code.

**Negative**
- The notification path uses the **reverse BLE role** from the FuseMind custom
  service (iPhone = ANCS GATT server / peripheral; watch = GATT client /
  central). The watch will run two logical roles. *Mitigation:* keep ANCS
  consumption isolated in the watch adapter; the `docs/ble-protocol.md` custom
  service is unaffected.
- Classification moves watch-side for notifications. *Mitigation:* the agent is
  reachable over the network from the watch; local H/M/L heuristic is the
  fallback (consistent with golden rule #4).
- **Risk the spike fails** (Wear OS may not bond to iPhone ANCS at all). If so,
  we fall back to Option B as an interim and revisit. The spike exists precisely
  to surface this before committing.

## ANCS reference (for implementers)

| Item | UUID |
|---|---|
| ANCS Service | `7905F431-B5CE-4E99-A40F-4B1E122D00D0` |
| Notification Source (Notify) | `9FBF120D-6301-42D9-8C58-25E699A21DBD` |
| Control Point (Write) | `69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9` |
| Data Source (Notify) | `22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB` |

ANCS characteristics require an **encrypted/bonded** link. Notification Source
pushes 8-byte tuples (EventID, EventFlags, CategoryID, CategoryCount,
NotificationUID[4]); attributes (app identifier, title, message) are fetched by
writing a *Get Notification Attributes* command to the Control Point and reading
the reply on the Data Source.

## Patterns referenced

- Spike/Tracer-bullet before committing to an uncertain integration.
- ANCS Notification Provider / Consumer roles (Apple).
- Graceful degradation + LLM-first-with-fallback (golden rules #3, #4).
