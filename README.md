<img width="923" height="923" alt="fullsize app icone" src="https://github.com/user-attachments/assets/57f7ccbb-7795-4446-b3a3-e5bb789f9c57" />

# VeilCal — Silent Emergency Alert System

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Language-Java%2017-orange?style=for-the-badge&logo=java" />
  <img src="https://img.shields.io/badge/Min%20SDK-24%20(Android%207.0)-blue?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-blue?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Version-1.0.0-success?style=for-the-badge" />
  <img src="https://img.shields.io/badge/License-Academic-lightgrey?style=for-the-badge" />
</p>

---

> **A fully functional calculator that secretly protects you — because opening an obvious SOS app can itself be a threat.**

VeilCal is a personal-safety Android application that looks and behaves exactly like a standard calculator. Hidden beneath the interface is a complete emergency alert system. The moment you activate protection, the app begins silently tracking your GPS location in the background. When a trigger fires, your real-time coordinates are sent instantly in an emergency SMS to up to three contacts simultaneously, followed by sequential phone calls to each of them — all without drawing attention or opening a visible screen.

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
13. [Limitations & Known Issues](#-limitations--known-issues)
14. [Architecture Notes](#-architecture-notes)
15. [License](#-license)

---

## 📱 Overview

In dangerous situations — harassment, robbery, domestic violence, or any scenario where the victim cannot openly call for help — traditional emergency apps fail because their icons, interfaces, or interactions are visible to the threat. VeilCal solves this by hiding its entire purpose inside a calculator app.

The app name in the launcher is **VeilCal**. Its icon and UI are indistinguishable from any other calculator. The only way to reach emergency settings is to type a secret numeric password on the calculator keypad and press `=`. A wrong input is silently treated as a normal calculation. An attacker who picks up the phone sees nothing unusual.

---

## 📍 How the Location System Works

The location system is the most critical component of VeilCal. It answers one operational question: **when the emergency fires, does the SMS contain where you actually are right now?**

### Three-Layer Resolution Chain

```
Layer 1 ── Live continuous tracking              (primary — zero delay)
           EmergencyService tracks GPS every 30s while protection is active.
           On trigger: read stored fix instantly. No wait, no request.
           ↓ only if no fix stored within last 5 minutes

Layer 2 ── Fresh GPS request with 10s timeout    (fallback)
           requestLocationUpdates(0, 0) on GPS + Network providers.
           Fires when triggered within the first 30 seconds of activation.
           ↓ only if timeout or no provider enabled

Layer 3 ── Last known location                   (last resort)
           getLastKnownLocation across GPS → Network → Passive.
           May be stale. SMS still sends regardless.
           ↓ only if all providers return null

No location ── SMS sends with route context + "(Location unavailable)" note
```

### Layer 1 — Continuous Live Tracking

The moment you tap **ACTIVATE**, `EmergencyService` registers location listeners on both GPS and Network providers simultaneously via `requestLocationUpdates(provider, 0, 0, listener, mainLooper)`. Every location update writes full-precision coordinates to `PrefManager` as `String` values (not `float` — preserving all 15 decimal digits) along with a timestamp.

When the trigger fires, `LocationHelper` checks whether a live fix was stored within the last 5 minutes. If yes — which is always the case after 30+ seconds of activation — the stored fix is used **instantly with zero delay**.

```
ACTIVATE pressed  →  GPS + Network listeners register
     ↓ every 30 seconds or 10 metres
Location fix  →  stored in PrefManager as full-precision String
     ↓
Trigger fires  →  read stored fix  →  build SMS  →  send instantly
DEACTIVATE pressed  →  listeners unregister  →  stored fix cleared
```

### Layer 2 — Fresh Fix on Early Trigger

If the emergency fires within the first 30 seconds of activation, the system falls back to `requestLocationUpdates(0, 0)` with a 10-second timeout. An `AtomicBoolean` prevents both the callback and the timeout from firing. Outdoors with GPS enabled, a fix typically arrives in 1–3 seconds.

### Honest GPS Assessment

| Scenario | Accuracy | Delay |
|---|---|---|
| Outdoors, active 30+ seconds | Precise — within 5 metres | Zero |
| Outdoors, triggered within first 30s | Precise — within 5 metres | 1–3 seconds |
| Indoors, good network coverage | Approximate — 50–500 metres | Zero |
| Indoors, poor signal | Stale or unavailable | Up to 10 seconds |

GPS does not penetrate buildings reliably. This is a hardware limitation — no Android app can fully overcome it.

### Duplicate SMS Prevention

A **60-second cooldown** is enforced in `EmergencyService` after each trigger. If the shake detector resets and fires again while the user is still shaking — which is natural during a real emergency — all subsequent triggers within 60 seconds are silently ignored. Only one `EmergencyHandler` is ever started per emergency event.

---

## ✨ Core Features

### 🧮 Fully Functional Calculator
- Supports `+`, `−`, `×`, `÷`, `%`, `^` with correct BODMAS operator precedence
- Three-pass expression evaluator: exponentiation → multiplication/division → addition/subtraction
- Live preview label updates in real time as you type
- Calculation history — last 20 entries displayed in a scrollable panel, persisted across sessions
- C button clears the current expression only — history is preserved
- 12-digit input limit per operand
- Light and dark themes with one-tap toolbar toggle

### 🔐 Stealth Password Gate
- Type your numeric password on the calculator and press `=` to open Emergency Settings
- Wrong input is silently evaluated as a normal math expression — no error, no hint
- Display resets to `0` instantly on a correct match
- Password rules: digits only, minimum 4 digits, cannot start with zero
- Rules are displayed clearly on both the setup and reset screens

### 📳 Three Emergency Trigger Modes

| Mode | How to Activate | Best For |
|---|---|---|
| **Shake Only** | Shake device N times within a configurable window | Pocket or bag trigger |
| **Power + Shake** | Press power button once, then shake within the window | Screen-off trigger |
| **Rapid Power Presses** | Press power button N times rapidly | Silent trigger, no movement needed |

All modes are independently configurable:
- Shake force threshold — 5.0 to 25.0 G (default 15.0)
- Count required — 1 to 10 shakes or presses
- Time window — 1 to 10 seconds
- SMS-only mode — optional toggle to skip the call phase entirely

### 📨 Multi-Contact SMS + Sequential Calling
- Up to **3 emergency contacts**, stored locally with name and phone number
- On trigger: SMS sent **simultaneously** to all contacts — GPS coordinates formatted with `Locale.US` to guarantee the Google Maps link is always valid
- If background SMS is blocked: SMS app opens pre-filled; app waits 6 seconds then begins calling
- Calls placed **sequentially**: contact 1 → call ends → 2-second pause → contact 2 → contact 3
- **45-second safety timeout** per contact if call state events never fire
- **Dual-SIM support**: correct SIM selected via `SubscriptionManager` for both SMS and calls
- **60-second cooldown**: prevents duplicate SMS from continued shaking after trigger

### 📌 Route Context in Emergency SMS
- **Starting point**: tap a button — GPS reads your current position and reverse-geocodes it to a human-readable street address
- **Destination**: set via interactive map — if you searched by name, that name appears in the SMS; if you tapped the map, coordinates are used
- Emergency SMS reads naturally: *"I was going from Mirpur 10, Dhaka to Dhanmondi 27, Dhaka. I am in danger. Please help. My location: [Google Maps link]"*

### 🗺️ Interactive Map for Destination
- Powered by **OSMDroid** — no Google Maps API key required
- Type any place name for live autocomplete from the **Photon Komoot API** (400ms debounce)
- Tap anywhere on the map to drop a pin at any precise location
- Custom no-op filter adapter prevents suggestions from clearing on item selection

### 🔄 Always-On Background Protection
- `EmergencyService` runs as a foreground service with `START_STICKY` — restarts if killed
- GPS tracking runs continuously while protection is active
- `WAKE_LOCK` acquired during Power+Shake window to prevent CPU sleep
- Persistent low-priority notification while protection is active

### 👥 Emergency Contact Management
- Import from phonebook via `ContactsContract` or enter name and number manually
- Duplicate phone number detection — same number cannot be added twice
- Contact names containing colons handled correctly with `split(":", 2)`
- All data stored locally — never transmitted to any server

### 🔑 Password Recovery
- 7 predefined security questions plus a custom question option
- Answers stored and compared case-insensitively
- Two-phase UI: identity verification first; change panel revealed only after correct answer
- Can change password, security question, or both in one session

### 🌗 Live Theme Switching
- Toggle between light and dark mode via toolbar icon
- First launch inherits the system dark mode setting
- All colours defined through custom theme attributes — no hardcoded values

### ⏱️ Active Protection Timer
- Shows real-time elapsed counter: `Protection active for: HH:MM:SS`
- Updates every second; persists across Activity rotation

---

## 🔄 Complete Emergency Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                       APP FIRST LAUNCH                          │
│  Set numeric password (4+ digits, no leading zero)              │
│  Set security question + answer                                 │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     CALCULATOR MODE                             │
│  Type password → press = → Settings opens                       │
│  Wrong input → silent math evaluation, no trace                 │
└────────────────────────────┬────────────────────────────────────┘
                             │ correct password
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   EMERGENCY SETTINGS HUB                        │
│  Add up to 3 contacts  •  Set starting point                    │
│  Set destination (interactive map)  •  Configure trigger        │
│  Tap ACTIVATE                                                   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│               BACKGROUND MONITORING (always-on)                 │
│  EmergencyService (foreground, START_STICKY)                    │
│  ├── GPS + Network listeners → location stored every 30s        │
│  ├── ShakeDetector (accelerometer, 150ms debounce)              │
│  └── BroadcastReceiver (power button via screen on/off)         │
└────────────────────────────┬────────────────────────────────────┘
                             │ trigger gesture detected
                             │ 60-second cooldown armed instantly
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│               EMERGENCY DISPATCH (EmergencyHandler)             │
│                                                                 │
│  Step 1 — Resolve location                                      │
│    Live fix ≤5 min old?  → use instantly (zero delay) ✅        │
│    No live fix?  → requestLocationUpdates(0,0), 10s timeout     │
│    Timeout?  → getLastKnownLocation fallback chain              │
│    All null?  → send with "(Location unavailable)" note         │
│                                                                 │
│  Step 2 — Build SMS                                             │
│    "I was going from [Human-readable Start]                     │
│     to [Human-readable Destination].                            │
│     I am in danger. Please help.                                │
│     My location: maps.google.com?q=23.810300,90.412500"         │
│    (Locale.US format — scientific notation impossible)          │
│                                                                 │
│  Step 3 — Send SMS to ALL contacts simultaneously               │
│    Success → proceed to calls immediately                       │
│    Blocked → open SMS app (manual) → wait 6s → proceed          │
│                                                                 │
│  Step 4 — Sequential calls                                      │
│    Contact 1 → OFFHOOK → IDLE → 2s pause                        │
│    Contact 2 → OFFHOOK → IDLE → 2s pause                        │
│    Contact 3 → OFFHOOK → IDLE → stopSelf()                      │
│    (45s safety timeout per contact guards against stuck states) │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📊 Emergency SMS Scenarios

### SMS Access Granted

| Situation | SMS Silent? | Location in SMS | Calls Automatic? |
|---|---|---|---|
| Outdoors, active 30+ seconds | ✅ Yes | Pinpoint current (live fix) | ✅ Yes |
| Indoors, good network | ✅ Yes | Approximate 50–500m | ✅ Yes |
| Triggered within first 30s | ✅ Yes | Current (fresh request) | ✅ Yes |
| GPS completely off | ✅ Yes | Stale or unavailable | ✅ Yes |

### SMS Access Blocked by Manufacturer

| Situation | SMS Silent? | Location | Calls Automatic? |
|---|---|---|---|
| GPS available | ❌ Manual tap | Accurate (in SMS app) | ✅ After 6s |
| GPS unavailable | ❌ Manual tap | Unavailable note | ✅ After 6s |

> **The call sequence always runs automatically regardless of SMS outcome.** Even if SMS is blocked and GPS is off, contacts will still receive phone calls.

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
| **Geocoding** | Photon Komoot REST API (free, no key) |
| **Local Storage** | SharedPreferences via PrefManager |
| **Key Android APIs** | `SensorManager`, `LocationManager`, `SmsManager`, `TelephonyManager`, `TelephonyCallback`, `SubscriptionManager`, `PowerManager.WakeLock` |

---

## 📂 Project Structure

```
app/src/main/
├── AndroidManifest.xml
│
├── java/com/example/silentemergency/
│   ├── CalculatorActivity.java          # Launcher; arithmetic engine; password gate
│   ├── DestinationPickerActivity.java   # OSMDroid map; Photon search; tap-to-pin
│   ├── EmergencyHandler.java            # Foreground service: SMS + sequential calls
│   ├── FirstTimeSetupActivity.java      # One-time setup: password + security question
│   ├── PlaceSuggestion.java             # POJO: name, lat, lon for autocomplete
│   ├── ResetPasswordActivity.java       # Two-phase credential reset
│   ├── SettingsActivity.java            # Emergency hub: contacts, route, toggle, timer
│   ├── ShakeConfigActivity.java         # Trigger mode and sensitivity configuration
│   │
│   ├── helper/
│   │   ├── CallHelper.java              # ACTION_CALL with dual-SIM voice subscription
│   │   ├── LocationHelper.java          # Three-layer location chain; Locale.US URLs
│   │   └── SmsHelper.java              # Background SMS; dual-SIM; manual fallback
│   │
│   ├── service/
│   │   ├── EmergencyService.java        # Live GPS; gesture detection; 60s cooldown
│   │   └── ShakeDetector.java          # Windowed shake counter; 150ms debounce
│   │
│   └── utils/
│       └── PrefManager.java             # SharedPreferences wrapper; GPS as String;
│                                        # destination label; 30+ typed keys
│
└── res/
    ├── layout/          # All activity and item layouts
    ├── drawable/        # Rounded shapes, card backgrounds, theme-aware buttons
    ├── menu/            # Toolbar menu
    └── values/          # attrs, colors, strings, styles, themes
```

---

## 🚀 Setup & Build

### Prerequisites
- Android Studio **Ladybug** (2024.1) or newer
- JDK 17
- A **physical Android device** — SMS and calls do not work on emulators
- "Install from Unknown Sources" enabled on the test device

### Steps

```bash
# 1. Clone
git clone https://github.com/Hishamwalid/EmergencyShake.git
cd EmergencyShake

# 2. Open in Android Studio
# File → Open → select the folder → allow Gradle sync

# 3. Build APK
# Build → Build APK(s)
# Output: app/build/outputs/apk/debug/app-debug.apk

# 4. Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔧 First-Time Configuration

The setup screen runs automatically on first launch before the calculator appears.

**Step 1 — Password**
Enter a numeric-only password and confirm it. This is what you type on the calculator to open Emergency Settings.

Password rules shown on screen:
- Digits only (0–9)
- Minimum 4 digits
- Cannot start with zero

**Step 2 — Security Question**
Choose one of 7 predefined questions or write a custom one. Provide your answer — used to recover access if you forget your password. Answers are case-insensitive.

Tap **Complete Setup**. The calculator opens.

---

## 📖 Usage Guide

### Accessing Emergency Settings
Type your numeric password on the calculator and press `=`. Settings opens instantly. The display resets to `0`. To any observer it looks like a normal calculator press.

### Adding Emergency Contacts
Settings → **Add Emergency Contact** → Import from Contacts or enter manually. Up to 3 contacts. Duplicate phone numbers are rejected automatically.

### Setting Your Route
- **Starting point** — tap **Set Current Location as Starting Point**. The app requests a fresh GPS fix and reverse-geocodes it to a human-readable address. The button disables while fetching and shows a timestamp when complete.
- **Destination** — tap **Set Destination on Map**. Search by name or tap the map. If you search, the place name is stored and used in the emergency SMS. Confirm to save.

### Configuring the Trigger
Tap the gear icon in the Settings toolbar. Choose one trigger mode. Adjust its parameters. Optionally enable SMS-only mode to skip the call phase.

### Activating Protection
Tap the large circular **ACTIVATE** button. The timer starts. A notification appears. GPS tracking begins immediately in the background.

### Triggering an Emergency
Perform your configured gesture. The device double-vibrates to confirm. SMS and calls proceed silently. All further gestures are ignored for 60 seconds to prevent duplicate messages.

### Recovering a Forgotten Password
From the calculator screen, tap the settings icon in the toolbar. Answer your security question. Update your credentials.

---

## 🔑 Permissions

| Permission | Type | Purpose |
|---|---|---|
| `SEND_SMS` | Runtime (dangerous) | Silent background SMS to all contacts |
| `ACCESS_FINE_LOCATION` | Runtime (dangerous) | Precise GPS for live tracking and SMS |
| `ACCESS_COARSE_LOCATION` | Runtime (dangerous) | Network/cell tower location fallback |
| `ACCESS_BACKGROUND_LOCATION` | Runtime — separate prompt (Android 10+) | Location while screen is off |
| `CALL_PHONE` | Runtime (dangerous) | Direct emergency call |
| `READ_PHONE_STATE` | Runtime (dangerous) | Monitor call state for sequential calling |
| `READ_CONTACTS` | Runtime (dangerous) | Import contacts from phonebook |
| `POST_NOTIFICATIONS` | Runtime (Android 13+) | Foreground service notification |
| `FOREGROUND_SERVICE` | Normal | Run background services |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Normal | Non-standard FGS type (Android 14+) |
| `FOREGROUND_SERVICE_LOCATION` | Normal | Location from foreground service (Android 14+) |
| `VIBRATE` | Normal | Haptic feedback on shakes and trigger |
| `WAKE_LOCK` | Normal | CPU alive during Power+Shake window |
| `INTERNET` | Normal | Map tiles and Photon geocoding |

---

## ⚠️ Granting SMS Access on High Android Versions

On **Android 10 and above**, device manufacturers apply battery and background execution restrictions that prevent VeilCal from sending SMS silently — even after the standard `SEND_SMS` permission is granted.

If your emergency SMS opens the SMS app instead of sending silently, follow every step below.

---

### Step 1 — Open App Info

**Long-press the VeilCal icon** → tap **App Info** (or ℹ️ icon).

> Alternative: **Settings → Apps → See all apps → VeilCal**

---

### Step 2 — Remove Background Restrictions

App Info → **Battery** → select the unrestricted option:

| Manufacturer | Setting to Choose |
|---|---|
| **Samsung One UI** | Battery → **Unrestricted** |
| **Xiaomi / MIUI / HyperOS** | Other Permissions → Allow background; Battery Saver → No restrictions |
| **OPPO / ColorOS** | Battery → Background Power Usage → Allow |
| **Realme / Realme UI** | Battery → Background Activity → Allow |
| **Vivo / FuntouchOS** | Battery → Background Power Consumption → No restriction |
| **OnePlus / OxygenOS** | Battery → Background Activity → Allow |
| **Huawei / EMUI** | Battery → App Launch → Manage manually → Background Activity on |
| **Stock Android / Pixel** | Battery → Battery usage → Unrestricted |

> **The OS may ask for your device PIN or fingerprint here.** Enter your phone's screen-lock credential — not your VeilCal password.

---

### Step 3 — Grant SMS Permission

App Info → **Permissions** → **SMS** → **Allow**.

---

### Step 4 — Grant Background Location (Android 10+)

App Info → **Permissions** → **Location** → **Allow all the time**.

> Tap **"Change to allow all the time"** when the system warning appears. Required for GPS to update while the screen is off.

---

### Step 5 — Allow Notifications (Android 13+)

App Info → **Permissions** → **Notifications** → **Allow**.

---

### Step 6 — Samsung Extra Step

1. App Info → **Battery** → **Unrestricted** (not Optimized)
2. **Settings → Device Care → Battery → Background usage limits**
3. Ensure VeilCal is **not** in the Sleeping or Deep sleeping apps lists

---

### Step 7 — Verify

1. Open VeilCal → enter password → tap **ACTIVATE**
2. Close the app completely → lock the screen → wait 60 seconds
3. Perform your trigger gesture
4. Check your test contact — SMS should arrive silently with a Google Maps link

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

## ⚠️ Limitations & Known Issues

**No silent call audio control** — Calls are placed via `ACTION_CALL`, which opens the system dialer visibly and rings audibly. Android 9+ blocks background audio control for non-default-dialer apps. This is a platform restriction.

**GPS accuracy indoors** — Live tracking works precisely outdoors. Indoors, GPS is blocked; the network provider attempts a coarse fix (50–500m). Hardware limitation.

**Background location requires manual setup** — "Allow all the time" cannot be granted via a runtime dialog on Android 10+. Must be set in App Info.

**No reboot persistence** — There is no `RECEIVE_BOOT_COMPLETED` receiver. Protection must be manually re-activated after a device reboot.

**Plain-text credential storage** — Password and security answer are stored as plain strings in SharedPreferences. Safe on non-rooted devices but vulnerable to device backups and rooted access.

**Map and geocoding require internet** — Destination search and reverse geocoding require a data connection. Core SMS and calls work fully offline.

---

## 🏗️ Architecture Notes

**Three-layer GPS** — `EmergencyService` maintains continuous `requestLocationUpdates` on GPS and Network providers. Every fix is written to `PrefManager` as a full-precision `String`. `LocationHelper` reads PrefManager first (zero delay), then falls back to a 10-second fresh request, then to last-known. `String.format(Locale.US, "%.6f,%.6f")` guarantees valid Maps URLs.

**60-second cooldown** — `triggerOnCooldown` is set as the very first action in `triggerEmergency()`. All subsequent gesture callbacks return immediately. Cancelled in `onDestroy()`.

**Two-service separation** — `EmergencyService` is a lightweight, always-on listener (`START_STICKY`). `EmergencyHandler` is a heavyweight, on-demand dispatcher (`START_NOT_STICKY`, self-terminates). Keeps gesture detection alive during long call sequences.

**Destination label** — When a user searches for a destination by name, the display label is stored separately in `PrefManager` (`destination_label`). `LocationHelper` reads this label for the SMS route prefix, so the message reads "heading to Dhanmondi 27, Dhaka" instead of raw coordinates.

**Dual-SIM routing** — Both `SmsHelper` and `CallHelper` query `SubscriptionManager.getDefaultSmsSubscriptionId()` and `getDefaultVoiceSubscriptionId()`. Both AOSP and OEM subscription extras are passed in the call intent for compatibility across Samsung, Xiaomi, and other manufacturers.

**Split safety** — All contact data parsed with `split(":", 2)` — contact names containing colons never corrupt the stored phone number.

---

## 📄 License

Academic project — distributed for educational and personal safety use.

**Repository:** [https://github.com/Hishamwalid/EmergencyShake](https://github.com/Hishamwalid/EmergencyShake)
