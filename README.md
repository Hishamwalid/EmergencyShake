<img width="923" height="923" alt="fullsize app icone" src="https://github.com/user-attachments/assets/57f7ccbb-7795-4446-b3a3-e5bb789f9c57" />

# VeilCal — Silent Emergency Alert System

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Language-Java%2017-orange?style=for-the-badge&logo=java" />
  <img src="https://img.shields.io/badge/Min%20SDK-24%20(Android%207.0)-blue?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-blue?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Version-2.2.3-success?style=for-the-badge" />
  <img src="https://img.shields.io/badge/License-Academic-lightgrey?style=for-the-badge" />
</p>

---

> **A fully functional calculator that secretly protects you — because opening an obvious SOS app can itself be a threat.**

VeilCal is a personal-safety Android application that looks and behaves exactly like a standard calculator. Hidden beneath the interface is a complete emergency alert system. The moment you activate protection, the app begins silently tracking your GPS location in the background every 30 seconds. When a trigger fires, your real-time coordinates are sent instantly in an emergency SMS to up to three contacts simultaneously, followed by sequential phone calls to each of them — all without drawing attention or opening a visible screen.

---

## Table of Contents

1. [Overview](#-overview)
2. [How the Location System Works](#-how-the-location-system-works)
3. [Core Features](#-core-features)
4. [Complete Emergency Flow](#-complete-emergency-flow)
5. [Emergency SMS Scenarios](#-emergency-sms-scenarios)
6. [Tech Stack](#-tech-stack)
7. [Project Structure](#-project-structure)
8. [Setup & Build](#-setup--build)
9. [First-Time Configuration](#-first-time-configuration)
10. [Usage Guide](#-usage-guide)
11. [Permissions](#-permissions)
12. [Granting SMS Access on High Android Versions](#-granting-sms-access-on-high-android-versions)
13. [Limitations](#-limitations)
14. [Architecture Notes](#-architecture-notes)
15. [License](#-license)

---

## 📱 Overview

In dangerous situations — harassment, robbery, domestic violence, or any scenario where the victim cannot openly call for help — traditional emergency apps fail because their icons, interfaces, or interactions are visible to the threat. VeilCal solves this by hiding its entire purpose inside a calculator app.

The app name in the launcher is **VeilCal**. Its icon and UI are indistinguishable from any other calculator. The only way to reach emergency settings is to type a secret numeric password on the calculator keypad and press `=`. A wrong input is silently treated as a normal mathematical calculation — no error, no indication a password exists. An attacker who picks up the phone sees nothing unusual.

---

## 📍 How the Location System Works

This is the most important system to understand before deploying VeilCal. It answers the question: **when the emergency fires, does the SMS show where you actually are right now?**

### Three-Layer Resolution Chain

The system uses three layers in strict priority order:

```
Layer 1 ── Live continuous tracking                     (primary — zero delay)
           GPS + Network listeners run every 30s while protection is active.
           When trigger fires: stored coordinates are read instantly.
           ↓ only if no fix stored within the last 5 minutes

Layer 2 ── Fresh on-demand GPS request                  (first-30-seconds fallback)
           requestLocationUpdates(0, 0) on GPS + Network providers.
           10-second timeout with AtomicBoolean double-fire guard.
           ↓ only if timeout or no provider enabled

Layer 3 ── Last known location                          (final fallback)
           getLastKnownLocation: GPS → Network → Passive provider chain.
           ↓ only if all providers return null

No location ── SMS still sends with route context + "(Location unavailable)" note
```

### Layer 1 — Continuous Live Tracking

The moment you tap **ACTIVATE**, `EmergencyService` registers location listeners on both GPS and Network providers simultaneously via `requestLocationUpdates(provider, 0, 0, listener, mainLooper)`. Every location update immediately writes the coordinates to `PrefManager` as full-precision `String` values — preserving all 15 significant digits of a Java `double` — alongside the current timestamp.

When the trigger fires, `LocationHelper` checks whether a stored fix exists within the last 5 minutes. If yes — which is always the case after 30+ seconds of activation — the stored coordinates are used **instantly with zero delay**. The SMS is built and sent with your actual current position.

### Layer 2 — Fresh Fix on Early Trigger

If the emergency fires within the first 30 seconds of activation (before the first 30-second interval completes), the system requests a fresh fix. It registers `requestLocationUpdates(0, 0)` on available providers. An `AtomicBoolean` named `done` ensures either the location callback or the 10-second timeout fires — never both. Outdoors with GPS enabled, a fix typically arrives in 1–3 seconds.

### Layer 3 — Last Known Fallback

If neither layer produces a result, `getLastKnownLocation()` is tried across three providers in order. If all return null, the SMS still sends — the location portion is replaced with a note for the contact. The route context (starting point and destination) is still included.

### GPS Accuracy by Scenario

| Scenario | Expected Accuracy | Delay at Trigger |
|---|---|---|
| Outdoors, protection active 30+ seconds | Precise — within 5 metres | Zero |
| Outdoors, triggered within first 30 seconds | Precise — within 5 metres | 1–3 seconds |
| Indoors, good network coverage | Approximate — 50–500 metres | Zero |
| Indoors, GPS blocked, poor network | Stale or unavailable | Up to 10 seconds |

GPS does not penetrate buildings reliably. Indoors, the network provider (cell towers / WiFi) attempts a coarse fix. This is a hardware limitation that no Android app can overcome.

### Duplicate Alert Prevention

A **60-second cooldown** is enforced in `EmergencyService` after every trigger. Once the emergency fires, all subsequent gesture events are silently ignored for 60 seconds. This ensures exactly one `EmergencyHandler` runs per emergency event — one SMS batch, one call sequence.

---

## ✨ Core Features

### 🧮 Fully Functional Calculator
- Supports `+`, `−`, `×`, `÷`, `%`, `^` with correct BODMAS operator precedence
- Three-pass expression evaluator: exponentiation → multiplication/division → addition/subtraction
- Live preview label updates in real time as you type, showing the result before `=` is pressed
- Calculation history — last 20 entries displayed in a scrollable panel, persisted across sessions
- C button clears the current expression only; history is preserved as an intentional design
- 12-digit input limit per operand
- Light and dark themes with one-tap toolbar toggle; first launch inherits the system theme

### 🔐 Stealth Password Gate
- Type your numeric password on the calculator keypad and press `=` to open Emergency Settings
- Wrong input is silently evaluated as a normal math expression — no error, no hint that a password exists
- The display resets to `0` instantly after a successful match, leaving zero visible trace
- Password rules enforced and displayed clearly on the setup screen:
  - Digits only (0–9)
  - Minimum 4 digits
  - Cannot start with zero

### 📳 Three Emergency Trigger Modes

| Mode | How to Activate | Best For |
|---|---|---|
| **Shake Only** | Shake device N times within a configurable time window | Pocket or bag trigger |
| **Power + Shake** | Press power button once to arm, then shake within the window | Screen-off trigger |
| **Rapid Power Presses** | Press power button N times rapidly | Completely silent trigger, no movement needed |

All modes are independently configurable:
- Shake force threshold — 5.0 to 25.0 G (default 15.0)
- Count required — 1 to 10 shakes or presses
- Time window — 1 to 10 seconds
- **SMS-only mode** — optional toggle to skip phone calls entirely

### 📨 Multi-Contact SMS + Sequential Calling
- Up to **3 emergency contacts**, stored locally with name and phone number
- On trigger: SMS sent **simultaneously** to all registered contacts
- GPS coordinates formatted with `String.format(Locale.US, "%.6f,%.6f")` — guarantees a valid Google Maps link under all device locales
- If background SMS is blocked by the OS: SMS app opens pre-filled; app waits 6 seconds then begins calling
- Calls placed **sequentially**: contact 1 → wait for call to end → 2-second pause → contact 2 → contact 3
- **45-second safety timeout** per contact if telephony state events never fire (voicemail, no signal)
- **60-second trigger cooldown**: a single emergency event always produces exactly one SMS batch
- **Dual-SIM support**: uses `SubscriptionManager.getDefaultSmsSubscriptionId()` and `getDefaultVoiceSubscriptionId()` to route through the user's configured default SIM on dual-SIM devices

### 📌 Route Context in Emergency SMS
- **Starting point**: tap a button — GPS reads your current position and reverse-geocodes it to a human-readable street address via Android Geocoder with Photon API fallback; shows a timestamp of when it was last updated
- **Destination**: set via interactive map — when you search for a place by name, that name is stored and used directly in the SMS text; if you pin the map manually, coordinates are used as fallback
- The resulting SMS reads naturally: *"I was going from Mirpur 10, Dhaka to Dhanmondi 27, Dhaka. I am in danger. Please help. My location: https://www.google.com/maps?q=23.810300,90.412500"*

### 🗺️ Interactive Map for Destination
- Powered by **OSMDroid** (OpenStreetMap) — no Google Maps API key required
- Type any place name for live autocomplete suggestions via the **Photon Komoot API** (400ms debounce)
- Tap anywhere on the map to drop a precise pin at any location
- Custom no-op filter adapter prevents search suggestions from disappearing on selection
- Map centres on your current GPS position when opened; animates to live fix as it arrives

### 🔄 Always-On Background Protection
- `EmergencyService` runs as an Android Foreground Service with `START_STICKY` — automatically restarts if killed by the OS
- GPS tracking runs continuously for the entire duration protection is active
- A `WAKE_LOCK` is acquired during the Power+Shake window to prevent the CPU sleeping between the power press and the shake
- Displays a persistent low-priority notification while active: "Monitoring for emergency trigger"

### 👥 Emergency Contact Management
- Import from phonebook via `ContactsContract` API or enter name and number manually
- Duplicate phone number detection — adding the same number twice is blocked with a clear message
- Contact names containing colons are handled correctly with `split(":", 2)`
- All data stored locally in SharedPreferences — no data ever leaves the device except the emergency SMS

### 🔑 Password Recovery System
- Choose from 7 predefined security questions or write a custom question
- Answers stored and compared case-insensitively
- Two-phase UI: identity verification first; credential change panel revealed only after the correct answer
- Can update password, security question, or both in a single session
- Same password rules enforced and displayed in the reset screen

### 🌗 Live Theme Switching
- Toggle between light and dark mode via toolbar icon — takes effect instantly via `Activity.recreate()`
- First launch reads `Configuration.UI_MODE_NIGHT_MASK` to match the system theme automatically
- All colours defined through custom theme attributes (`?attr/calcButtonBackground` etc.) — no hardcoded values

### ⏱️ Active Protection Timer
- Settings screen shows a real-time elapsed counter: `Protection active for: HH:MM:SS`
- Updates every second via a Handler-based runnable; start timestamp persisted across Activity restarts

---

## 🔄 Complete Emergency Flow

```
┌──────────────────────────────────────────────────────────┐
│                        APP FIRST LAUNCH                            │
│  Set numeric password (4+ digits, no leading zero)                 │
│  Set security question + answer                                    │
└──────────────────────────────┬───────────────────────────┘
                                     │
                                     ▼
┌──────────────────────────────────────────────────────────┐
│                      CALCULATOR MODE                               │
│  Type password → press = → Emergency Settings opens               │
│  Wrong input → silent math evaluation, display unchanged           │
└──────────────────────────────┬───────────────────────────┘
                                     │ correct password
                                     ▼
┌──────────────────────────────────────────────────────────┐
│                    EMERGENCY SETTINGS HUB                          │
│  Add up to 3 emergency contacts                                    │
│  Set starting point (GPS + reverse geocode + timestamp)            │
│  Set destination (interactive map + place name stored)             │
│  Configure trigger mode + sensitivity                              │
│  Tap ACTIVATE                                                      │
└──────────────────────────────┬────────────── ────────────┘
                                     │
                                     ▼
┌──────────────────────────────────────────────────────────┐
│                BACKGROUND MONITORING (always-on)                   │
│  EmergencyService (foreground, START_STICKY)                       │
│  ├── GPS + Network listeners → location stored every 30s/10m      │
│  ├── ShakeDetector (accelerometer, 150ms debounce)                 │
│  └── BroadcastReceiver (power button via screen on/off events)     │
└──────────────────────────────┬───────────────────────────┘
                                     │ trigger gesture detected
                                     │ 60-second cooldown armed instantly
                                     ▼
┌──────────────────────────────────────────────────────────┐
│               EMERGENCY DISPATCH (EmergencyHandler)                │
│                                                                    │
│  Location resolution:                                              │
│    Live fix ≤5 min old? → use instantly (zero delay)               │
│    No live fix? → requestLocationUpdates(0,0), 10s timeout         │
│    Timeout? → getLastKnownLocation(GPS/Network/Passive)            │
│    All null? → send with route context + unavailable note          │
│                                                                    │
│  SMS body:                                                         │
│    "I was going from [Start] to [Dest Name].                       │
│     I am in danger. Please help.                                   │
│     My location: maps.google.com?q=23.810300,90.412500"            │
│    (Locale.US — valid Maps link on every device)                   │
│                                                                    │
│  Send SMS to ALL contacts simultaneously                           │
│    Success → start calling immediately                             │
│    Blocked → open SMS app pre-filled → wait 6s → start calling   │
│                                                                    │
│  Sequential calls:                                                 │
│    Contact 1 → OFFHOOK → IDLE → 2s pause                         │
│    Contact 2 → OFFHOOK → IDLE → 2s pause                         │
│    Contact 3 → OFFHOOK → IDLE → stopSelf()                       │
│    (45s safety timeout per contact)                                │
└──────────────────────────────────────────────────────────┘
```

---

## 📊 Emergency SMS Scenarios

### When SMS Access is Granted

| GPS Situation | SMS Silent? | Location in SMS | Calls Automatic? |
|---|---|---|---|
| Outdoors, active 30+ seconds | ✅ Yes | Pinpoint live fix | ✅ Yes |
| Indoors, good network | ✅ Yes | Approximate 50–500m | ✅ Yes |
| Triggered within first 30 seconds | ✅ Yes | Current (fresh request) | ✅ Yes |
| GPS completely off | ✅ Yes | Stale or unavailable | ✅ Yes |

### When SMS Access is Blocked by Manufacturer

| GPS Situation | SMS Silent? | Location | Calls Automatic? |
|---|---|---|---|
| GPS available | ❌ Manual tap required | Accurate (in pre-filled SMS) | ✅ Yes, after 6s |
| GPS unavailable | ❌ Manual tap required | Unavailable note | ✅ Yes, after 6s |

> **The call sequence is always automatic regardless of SMS outcome.** Even in the absolute worst case — blocked SMS and no GPS — every registered contact will still receive a phone call.

---

## 🛠️ Tech Stack

| Component | Detail |
|---|---|
| **Language** | Java 17 |
| **IDE** | Android Studio (Ladybug or newer) |
| **Min SDK** | API 24 — Android 7.0 Nougat |
| **Target SDK** | API 34 — Android 14 |
| **Build System** | Gradle 8.7 / AGP 9.0.0 |
| **Map Library** | OSMDroid (OpenStreetMap) |
| **Geocoding** | Photon Komoot REST API (free, no API key required) |
| **Local Storage** | SharedPreferences via PrefManager |
| **Key Android APIs** | `SensorManager`, `LocationManager`, `SmsManager`, `TelephonyManager`, `TelephonyCallback`, `SubscriptionManager`, `PowerManager.WakeLock`, `NotificationCompat` |

---

## 📂 Project Structure

```
app/src/main/
├── AndroidManifest.xml
│
├── java/com/example/silentemergency/
│   ├── CalculatorActivity.java          # Launcher; arithmetic engine; password gate
│   ├── DestinationPickerActivity.java   # OSMDroid map; Photon search; tap-to-pin; label
│   ├── EmergencyHandler.java            # Foreground service: location → SMS → calls
│   ├── FirstTimeSetupActivity.java      # One-time setup: password + security question
│   ├── PlaceSuggestion.java             # POJO: name, lat, lon for autocomplete results
│   ├── ResetPasswordActivity.java       # Two-phase credential reset
│   ├── SettingsActivity.java            # Emergency hub: contacts, route, toggle, timer
│   ├── ShakeConfigActivity.java         # Trigger mode and sensitivity configuration
│   │
│   ├── helper/
│   │   ├── CallHelper.java              # ACTION_CALL with dual-SIM voice routing
│   │   ├── LocationHelper.java          # Three-layer location; destination label; URLs
│   │   └── SmsHelper.java              # Background SMS; dual-SIM; multi-part; fallback
│   │
│   ├── service/
│   │   ├── EmergencyService.java        # Live GPS tracking; gesture detection; cooldown
│   │   └── ShakeDetector.java          # Windowed shake counter with 150ms debounce
│   │
│   └── utils/
│       └── PrefManager.java             # Typed SharedPreferences wrapper; 32+ keys;
│                                        # GPS as String; destination_label; live location
│
└── res/
    ├── layout/          # All activity and item XML layouts
    ├── drawable/        # Rounded shapes, card backgrounds, selector buttons, icons
    ├── menu/            # Toolbar menu (theme toggle + settings icon)
    └── values/          # attrs.xml, colors.xml, strings.xml, styles.xml, themes.xml
```

---

## 🚀 Setup & Build

### Prerequisites
- Android Studio **Ladybug** (2024.1) or newer
- JDK 17
- A **physical Android device** — SMS and calls do not function on emulators
- "Install from Unknown Sources" enabled on the target device

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/Hishamwalid/EmergencyShake.git
cd EmergencyShake

# 2. Open in Android Studio
# File → Open → select the EmergencyShake folder → allow Gradle sync

# 3. Build the APK
# Build → Build APK(s)
# Output: app/build/outputs/apk/debug/app-debug.apk

# 4. Install on physical device via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔧 First-Time Configuration

The setup screen runs automatically on first launch before the calculator is shown.

**Step 1 — Set a Password**

Enter a numeric-only password and confirm it. This is what you type on the calculator to access Emergency Settings. Keep it short enough to type quickly under stress — 4 to 6 digits is ideal.

Password requirements (shown on-screen):
- Digits only (0–9)
- Minimum 4 digits
- Cannot start with zero

**Step 2 — Security Question**

Choose one of 7 predefined questions or write a custom question. Provide your answer — this is used for password recovery. Answers are stored and checked case-insensitively.

Tap **Complete Setup**. The calculator opens. You are now ready to configure emergency contacts.

---

## 📖 Usage Guide

### Accessing Emergency Settings
Type your numeric password on the calculator keypad and press `=`. The settings screen opens instantly. The display resets to `0`. To any observer the interaction looks like a normal calculation.

### Adding Emergency Contacts
Settings → **Add Emergency Contact** → choose **Import from Contacts** or **Manual**. Up to 3 contacts. Duplicate phone numbers are rejected automatically.

### Setting Your Route

**Starting Point:**
Tap **Set Current Location as Starting Point**. The button disables and shows "Getting location…" while fetching a fresh GPS fix. Once resolved, the address is displayed with a timestamp (e.g., "updated at 14:32, 13 Apr"). This is what appears as the starting point in your emergency SMS.

**Destination:**
Tap **Set Destination on Map**. An interactive map opens. Either:
- Type a place name in the search bar and select from the dropdown — the human-readable name is stored and used in the SMS
- Tap anywhere on the map to drop a pin — coordinates are stored as the destination

Tap **Confirm Destination** to save and return to Settings.

### Configuring the Trigger
Tap the gear icon in the Settings toolbar. Choose exactly one trigger mode and configure its parameters. Enable SMS-only mode if you want to suppress phone calls.

### Activating Protection
Tap the large circular **ACTIVATE** button. The elapsed timer starts counting. A persistent notification appears in the status bar. GPS tracking begins immediately in the background.

### Triggering an Emergency
Perform your configured gesture. The device produces a strong double-vibration to confirm the trigger. Emergency SMS messages are sent and calls begin silently in the background. All further gestures are ignored for 60 seconds.

### Recovering a Forgotten Password
From the calculator screen, tap the settings icon in the toolbar (top right). Answer your security question, then update your credentials.

---

## 🔑 Permissions

| Permission | Type | Purpose |
|---|---|---|
| `SEND_SMS` | Runtime (dangerous) | Silent background SMS to all contacts simultaneously |
| `ACCESS_FINE_LOCATION` | Runtime (dangerous) | Precise GPS for live tracking and emergency SMS |
| `ACCESS_COARSE_LOCATION` | Runtime (dangerous) | Cell tower / WiFi location fallback |
| `ACCESS_BACKGROUND_LOCATION` | Runtime — separate prompt (Android 10+) | Location updates while screen is off |
| `CALL_PHONE` | Runtime (dangerous) | Direct emergency call via `ACTION_CALL` |
| `READ_PHONE_STATE` | Runtime (dangerous) | Call state monitoring for sequential calling |
| `READ_CONTACTS` | Runtime (dangerous) | Import emergency contacts from device phonebook |
| `POST_NOTIFICATIONS` | Runtime (Android 13+) | Foreground service notification |
| `FOREGROUND_SERVICE` | Normal (auto-granted) | Permission to call `startForeground()` |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Normal (Android 14+) | Non-standard FGS type declaration |
| `FOREGROUND_SERVICE_LOCATION` | Normal (Android 14+) | Location access from foreground service |
| `VIBRATE` | Normal (auto-granted) | Haptic feedback on shake ticks and trigger confirmation |
| `WAKE_LOCK` | Normal (auto-granted) | Keep CPU active during Power+Shake window |
| `INTERNET` | Normal (auto-granted) | Map tiles and Photon geocoding API |

---

## ⚠️ Granting SMS Access on High Android Versions

On **Android 10 and above**, device manufacturers apply battery and background execution restrictions that prevent VeilCal from sending SMS silently in the background — even after the standard `SEND_SMS` permission is granted. If your emergency SMS opens the SMS app instead of sending automatically, follow every step below.

---

### Step 1 — Open App Info

**Long-press the VeilCal icon** on your home screen or app drawer. Tap **App Info** (or the ℹ️ icon).

> Alternative: **Settings → Apps → See all apps → VeilCal**

---

### Step 2 — Remove Background Restrictions

Inside App Info → tap **Battery** → select the unrestricted option for your device:

| Manufacturer | Setting |
|---|---|
| **Samsung One UI** | Battery → **Unrestricted** |
| **Xiaomi / MIUI / HyperOS** | Other Permissions → Allow background activity; Battery Saver → No restrictions |
| **OPPO / ColorOS** | Battery → Background Power Usage → Allow |
| **Realme / Realme UI** | Battery → Background Activity → Allow |
| **Vivo / FuntouchOS** | Battery → Background Power Consumption → No restriction |
| **OnePlus / OxygenOS** | Battery → Background Activity → Allow |
| **Huawei / EMUI** | Battery → App Launch → Manage manually → Background Activity on |
| **Stock Android / Google Pixel** | Battery → Battery usage → Unrestricted |

> **The OS may ask for your device PIN, password, or fingerprint here.** Enter your phone's screen-lock credential — not your VeilCal password — to confirm the change.

---

### Step 3 — Grant SMS Permission

App Info → **Permissions** → **SMS** → **Allow**.

---

### Step 4 — Grant Background Location (Android 10+)

App Info → **Permissions** → **Location** → **Allow all the time**.

> A system warning will appear. Tap **"Change to allow all the time"**. This is required for GPS to update while the screen is off — the most realistic real-world emergency scenario.

---

### Step 5 — Allow Notifications (Android 13+)

App Info → **Permissions** → **Notifications** → **Allow**. Without this, Android 13+ terminates the background service after a short delay.

---

### Step 6 — Samsung Extra Step

Samsung applies an additional battery layer:
1. App Info → **Battery** → select **Unrestricted** (not Optimized)
2. **Settings → Device Care → Battery → Background usage limits**
3. Ensure VeilCal is **not** in the Sleeping or Deep sleeping apps lists. Swipe it out if it is.

---

### Step 7 — Verify the Configuration

1. Open VeilCal → enter password → tap **ACTIVATE**
2. Close the app completely (swipe from recents)
3. Lock the screen and wait 60 seconds
4. Perform your trigger gesture
5. Check your test contact's phone — SMS should arrive silently with a valid Google Maps link

---

### Quick Reference by Android Version

| Version | Required Steps |
|---|---|
| Android 7.0 – 9.0 | Step 3 only |
| Android 10 – 11 | Steps 2, 3, 4 |
| Android 12 – 12.1 | Steps 2, 3, 4 |
| Android 13 | Steps 2, 3, 4, 5 |
| Android 14+ | Steps 2, 3, 4, 5 + Step 6 if Samsung |

---

## ⚠️ Limitations

**No silent call audio control** — Emergency calls are placed via `ACTION_CALL`, which opens the standard system dialer and rings audibly. Android 9+ restricts background apps from controlling active call audio unless the app is the default dialer — a requirement that would require a visible system-level user action, destroying the calculator disguise.

**GPS accuracy indoors** — Live tracking works precisely outdoors. Indoors, GPS signal is blocked by building materials and the network provider falls back to cell tower/WiFi triangulation (50–500m accuracy). This is a hardware limitation.

**Background location requires manual setup** — The "Allow all the time" location permission cannot be granted via a standard runtime dialog on Android 10+. It must be set manually in App Info as described above.

**No reboot persistence** — There is no boot receiver. After a device reboot, the user must manually open VeilCal and tap ACTIVATE again for protection to resume.

**Plain-text credential storage** — The password and security answer are stored as plain strings in SharedPreferences. Safe on non-rooted devices, but device backups and rooted access could expose them.

**Map and geocoding require internet** — The interactive map, destination search, and reverse geocoding all require an active data connection. Core SMS and calls work fully offline.

---

## 🏗️ Architecture Notes

**Three-layer GPS system** — `EmergencyService` maintains continuous `requestLocationUpdates` on both GPS and Network providers for the entire protection period. Coordinates are stored as full-precision `String` values (not `float`) in `PrefManager`. `LocationHelper.generateEmergencyMessageAsync()` reads `PrefManager` first (zero delay), then falls back to a 10-second on-demand request with `AtomicBoolean` guard, then to `getLastKnownLocation()`. All GPS-to-URL formatting uses `String.format(Locale.US, "%.6f,%.6f")` — no scientific notation, valid on every device locale.

**60-second trigger cooldown** — `EmergencyService.triggerEmergency()` sets `triggerOnCooldown = true` as its absolute first action — before vibration, before Toast, before starting `EmergencyHandler`. All gesture callbacks return immediately for 60 seconds. The cooldown runnable is cancelled in `onDestroy()`.

**Two-service architecture** — `EmergencyService` (`START_STICKY`, always-on, lightweight) handles gesture detection and GPS tracking. `EmergencyHandler` (`START_NOT_STICKY`, on-demand, self-terminating) handles the heavy SMS + calling sequence. Separating them ensures a multi-minute call sequence never blocks new gesture events from being processed.

**Destination label system** — `DestinationPickerActivity` passes back two values when confirming: raw coordinates for the Google Maps URL, and the human-readable place name for the SMS text. Both are stored separately in `PrefManager` (`destination` and `destination_label`). `LocationHelper` uses the label in the route prefix and the coordinates in the Maps link.

**Sequential call state machine** — `EmergencyHandler` uses `TelephonyCallback` (Android 12+) or `PhoneStateListener` (Android 6–11) to track call state. `OFFHOOK` signals the call connected; subsequent `IDLE` triggers `moveToNextContact()`. A 45-second safety timeout per contact handles voicemail, no signal, and stuck states. A 2-second pause between calls prevents dialer overlap.

**Dual-SIM routing** — `SmsHelper` queries `SubscriptionManager.getDefaultSmsSubscriptionId()` and `CallHelper` queries `getDefaultVoiceSubscriptionId()`. Both pass the subscription ID to their respective APIs. `CallHelper` also passes two subscription extras (`com.android.phone.extra.slot` and `subscription`) to cover both AOSP and OEM dialer implementations.

**Contact safety** — All contact data stored as `"Name:PhoneNumber"`. Parsed everywhere with `split(":", 2)` so contact names containing colons never corrupt the stored phone number. Duplicate detection compares extracted phone numbers before adding.

---

## 📄 License

Academic project — distributed for educational and personal safety use.

**Repository:** [https://github.com/Hishamwalid/EmergencyShake](https://github.com/Hishamwalid/EmergencyShake)

