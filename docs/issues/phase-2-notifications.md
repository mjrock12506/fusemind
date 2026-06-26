# Phase 2 — Notifications

**Goal:** every iPhone notification appears on the watch within 2 seconds.
**Status:** after Phase 1 · **Milestone tag:** `phase-2-notifications`
**Reference:** `docs/ble-protocol.md` (Notification Push / Dismiss
characteristics), the agent's `/classify` endpoint.

ANCS is the hard part. Once the iPhone can read its own notifications, the rest
is relaying them over the BLE pipe built in Phase 1 and rendering on the watch.

---

## P2-001 — ANCS reader on iOS
**Where:** `core/fusemind-ios/`.
**Do:** Subscribe to Apple Notification Center Service. Discover the ANCS
service on the iPhone, subscribe to the Notification Source, and fetch
attributes (app id, title, message) for each notification UID. Map each into the
universal `WatchNotification` type.
**Acceptance:**
- [ ] New iPhone notifications are detected within ~1s.
- [ ] App name, title, and body are extracted correctly.
- [ ] Works for at least 10 different apps.

## P2-002 — Classify, then relay over BLE
**Where:** iOS adapter + agent call.
**Do:** For each notification, call the agent `/classify` endpoint to set
`aiPriority` (fall back to local default if the agent is unreachable), then write
the JSON to the Notification Push characteristic.
**Acceptance:**
- [ ] Notification reaches the watch in < 2s end to end.
- [ ] `aiPriority` is populated (H/M/L).
- [ ] If the agent is down, delivery still happens (priority defaults).

## P2-003 — Notification list + detail UI on the watch
**Where:** `watch-adapters/wear-os/` (Jetpack Compose).
**Do:** Implement `onNotificationReceived()` — render a scrollable list (newest
first), trigger a haptic, and a detail view on tap. Design for a ~1.4" screen
(2-line titles max). High-priority items get a stronger haptic.
**Acceptance:**
- [ ] Notifications render legibly with app + title + body.
- [ ] Haptic fires on arrival; stronger for high priority.
- [ ] Tapping shows full detail.

## P2-004 — Dismiss from watch syncs to iPhone
**Where:** both sides.
**Do:** Implement `dismissNotification(id)` — write the UID to the Notification
Dismiss characteristic; the iOS side performs the ANCS dismiss action.
**Acceptance:**
- [ ] Dismissing on the watch clears the notification on the iPhone.
- [ ] No ghost notifications left on either device.

---

## Definition of Done (Phase 2)
- [ ] 50 notifications across 10 apps deliver accurately in < 2s.
- [ ] Dismissal syncs both ways reliably.
- [ ] Battery impact on the watch < 20% extra per day.
- [ ] Tagged `phase-2-notifications`.
