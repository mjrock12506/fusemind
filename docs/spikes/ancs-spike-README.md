# ANCS feasibility spike — how to run it

**Question this answers:** *Can a non-Apple BLE central (our watch) actually
receive Apple Notification Center Service (ANCS) notifications from a bonded
iPhone?* This is the gate for Option A in
[`ADR-002`](../adr/ADR-002-ancs-watch-consumer.md). If it works, FuseMind can
deliver *all* iPhone notifications to the watch. If it doesn't, we fall back to
Option B.

This is a **throwaway probe**. It lives in `spikes/ancs-consumer-android/` and
touches none of the production code (not the iOS app, the Wear OS app, or the
agent).

---

## What you need

- An **iPhone** (the ANCS provider).
- An **Android device** to act as the consumer. **Test on a normal Android
  phone first** — it's the fastest way to get a yes/no. Then repeat on the
  **Samsung Watch 6** (Wear OS is Android; the APK installs via `adb`), because
  the watch's Bluetooth stack may behave differently — that difference is part
  of what we're probing.
- **Android Studio** + `adb`.

## Key idea (why the steps look the way they do)

ANCS makes the **iPhone the GATT server** and the **Android device the GATT
client/consumer**, over an **encrypted, bonded** link. So you must *pair the two
devices at the OS level first*; only then can the app read ANCS. When you pair,
iOS may show a **"Show Notifications"** prompt for the accessory — you must
**allow** it, or iOS will refuse to expose ANCS.

---

## Run it

1. **Build & install the spike**
   - Open `spikes/ancs-consumer-android/` in Android Studio, let it Gradle-sync.
   - Select your Android device (phone or the Watch 6) and **Run**, or:
     ```
     cd spikes/ancs-consumer-android
     ./gradlew installDebug      # after Android Studio generates the wrapper
     ```

2. **Pair the Android device with the iPhone (system Bluetooth settings)**
   - On the **iPhone**: Settings → Bluetooth → tap the Android device → **Pair**.
   - On the **Android** device: accept the pairing request.
   - 👀 On the iPhone, if you see **"Allow … to Show Notifications"**, tap
     **Allow**. (Sometimes it's under Settings → Bluetooth → ⓘ next to the
     device → **Share System Notifications** — turn it **on**.)

3. **Open the "ANCS Spike" app** on the Android device.
   - Grant the Bluetooth permission prompt.
   - Tap **Refresh bonded devices**, then tap your **iPhone** in the list.

4. **Confirm the connection holds**
   - After `✓ Subscribed` you should see the log go **quiet** — one stable
     connection, no repeating `Disconnected` / reconnect lines. (The spike no
     longer auto-reconnects; it holds a single session until you tap **Refresh**.
     If you *do* see one `Session ended` line, the link dropped once — tap
     **Refresh**, then the iPhone, to re-arm. It should not loop.)

5. **Generate notifications — the actual proof**
   - With the connection **holding steady**, **lock the iPhone** (ANCS mainly
     pushes when the phone is locked).
   - **Send yourself a text** (or WhatsApp / Mail, or trigger a calendar alert).
   - In the log you should see a `NOTIFICATION: Added …` line followed by the
     decoded attributes:
     ```
     NOTIFICATION: Added Social uid=12345 flags=0x00 count=0
        app: com.apple.MobileSMS
        title: <sender>
        message: <the text you sent>
     ```
     Seeing `app:` / `title:` / `message:` lines = **ANCS works end to end** ✅.

6. **Read the logs** — on-screen in the app, and/or:
   ```
   adb logcat -s ANCSpike
   ```

---

## What success vs. failure looks like

Read the log lines top to bottom. The outcome is whichever row you reach:

| Log you see | Meaning | Verdict |
|---|---|---|
| `✓ ANCS service FOUND` **and** `NOTIFICATION: Added Social uid=…` (and ideally `app:`/`title:`/`message:` lines) | The central discovered ANCS and is receiving live notifications + attributes. | ✅ **FEASIBLE — Option A is on.** |
| `✓ ANCS service FOUND` but **no** `NOTIFICATION:` lines ever | Subscribed, but iOS isn't pushing. Usually: notification sharing not allowed, or iPhone unlocked. Re-check step 2's "Show Notifications", lock the phone, resend. | ⚠ Inconclusive — retry |
| `✗ ANCS service NOT present on this device.` | iOS connected but did **not** expose ANCS to this consumer (no notification-sharing grant, or wrong device). | ❌ Likely blocked on this device |
| `Connected` then immediately `Disconnected (status=…)` right after a subscribe | iOS dropped the link — typically an unbonded/unauthorised read of an encrypted ANCS characteristic. Re-pair, ensure BONDED, allow notifications. | ⚠ Bonding/auth issue |
| `⚠ Device is NOT bonded` | You skipped pairing. Pair in Bluetooth settings first. | ⚠ Fix and retry |
| Permission denied | Grant Bluetooth permission to the app. | ⚠ Fix and retry |

The log also prints **every GATT service UUID** it finds on the iPhone — handy
evidence even when ANCS is absent.

---

## If it gets stuck

- **Re-pair cleanly:** "Forget this device" on *both* the iPhone and the Android
  device, toggle Bluetooth off/on, then pair again and re-allow notifications.
- **Phone first, watch second:** if the Android *phone* works but the *Watch 6*
  doesn't, that's a meaningful result — note it; it tells us Wear OS bonding to
  iPhone ANCS is the real obstacle, not ANCS itself.
- The wrapper's `gradlew` binary isn't committed; open the project in Android
  Studio once to generate it (or run `gradle wrapper`).

## What we do with the result

- **Feasible (✅):** proceed to design the production Wear OS ANCS consumer under
  Option A (a real ticket set), reusing the parsing in `Ancs.kt`.
- **Not feasible (❌):** record it on ADR-002 and fall back to **Option B**
  (controlled-source MVP) as an interim while we investigate alternatives
  (e.g. an MFi/accessory path).

Either way: **report the exact log lines you reached** and we'll decide the next
step from evidence, not guesswork.
