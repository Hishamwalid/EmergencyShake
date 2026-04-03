Silent Emergency Alert System – Ecalculator
📱 Project Overview
Ecalculator is a personal safety Android application disguised as a fully functional calculator.
The user activates emergency protection by entering a secret password through the calculator interface. Once activated, the app runs silently in the background, monitors device motion via the accelerometer, and upon detecting a user‑configurable number of shakes, automatically sends an emergency SMS with the user’s GPS location and initiates a phone call to a pre‑registered emergency contact.

The application operates fully offline, stores all settings locally, and requires no internet connection – ensuring privacy and reliability.

✨ Key Features
Feature	Description
Calculator Disguise	Performs all basic arithmetic (+, −, ×, ÷, %, ^, backspace, clear).
Password Protection	User sets a password during first launch. Entering the password and pressing = opens the hidden Settings panel.
First‑Time Setup	Collects password, security question, and answer.
Emergency Contact	Store a phone number (local only).
Shake Detection	Uses accelerometer – adjustable sensitivity (5–30) and required shake count (1–10).
Background Service	Runs in foreground with a persistent notification while protection is active.
Emergency Alert	Sends SMS with Google Maps location link + initiates a phone call to the emergency contact.
Calculation History	Scrollable list of past calculations (right‑aligned, gray text) – cleared by C button.
Dark / Light Theme	Toggle via three‑dot menu.
Password Reset	Via security question (inside Settings).
Offline & Private	No internet required – all data stored in SharedPreferences.
🛠️ Technologies Used
Language: Java (JDK 17)

IDE: Android Studio (Ladybug / Panda 2)

Minimum SDK: API 24 (Android 7.0)

Target SDK: API 34 (Android 14)

Build Tool: Gradle 8.7, AGP 9.0.0

Key Android APIs: SensorManager, LocationManager, SmsManager, TelephonyManager, Service, SharedPreferences

📁 Project Structure
text
app/src/main/java/com/example/silentemergency/
├── CalculatorActivity.java          # Main calculator UI, password check, history
├── SettingsActivity.java            # Emergency contact, shake settings, dark mode, activation
├── FirstTimeSetupActivity.java      # Runs once: set password & security question
├── EmergencyHandler.java            # Short‑lived service: gets location, sends SMS, calls
├── helper/
│   ├── LocationHelper.java          # GPS retrieval and alert dispatch
│   ├── SmsHelper.java               # Sends SMS
│   └── CallHelper.java              # Initiates phone call
├── service/
│   ├── EmergencyService.java        # Foreground service, shake detector
│   └── ShakeDetector.java           # SensorEventListener, triggers emergency
└── utils/
    └── PrefManager.java             # SharedPreferences wrapper

res/
├── drawable/
│   ├── btn_rounded.xml              # Rounded button background (monochrome)
│   ├── btn_modern_primary.xml       # For first‑time setup button
│   └── bg_soft_rounded.xml          # For input fields in first‑time setup
├── layout/
│   ├── activity_calculator.xml      # Calculator layout with history area
│   ├── activity_settings.xml        # Settings layout (contact, seekbars, switch)
│   ├── activity_first_time_setup.xml
│   └── dialog_change_password.xml
├── menu/
│   └── menu_calculator.xml          # Three‑dot menu (Theme, Settings)
├── values/
│   ├── attrs.xml                    # Custom attributes for themes
│   ├── strings.xml                  # App name
│   ├── styles.xml                   # Button styles
│   └── themes.xml                   # Light / dark themes (monochrome)
└── AndroidManifest.xml
🚀 Setup & Installation
Prerequisites
Android Studio (latest stable)

JDK 17

A real Android phone (emulator may not have accelerometer or GPS reliably)

Steps
Clone the repository

bash
git clone https://github.com/Hishamwalid/EmergencyShake.git
Open the project in Android Studio.

Sync Gradle (File → Sync Project with Gradle Files).

Build the APK:

Build → Build Bundle(s) / APK(s) → Build APK(s)

The APK will be at app/build/outputs/apk/debug/app-debug.apk

Install on phone:

Enable “Install from unknown sources” in phone settings.

Transfer the APK and install.

Grant permissions when prompted (SMS, Location, Phone).

🧪 How to Use
First Launch
Set a password (e.g., 1234) and confirm it.

Choose a security question and provide an answer.

Tap Complete Setup → calculator appears.

Normal Calculator Usage
Perform calculations: 5 + 3 = → 8.

Use ^ for power: 2 ^ 3 = → 8.

C clears current expression and history.

⌫ deletes last character.

% converts the last number to percent.

Access Hidden Settings
Type your password (e.g., 1234) and press =.

The Settings activity opens.

Settings
Emergency Contact: Enter a phone number (with country code) and save.

Shake Sensitivity: 5 (very sensitive) – 30 (very hard shake).

Shake Count: Number of shakes required (1–10).

Dark Mode: Toggle between light and dark theme.

Activate Protection: Starts background service – a notification appears.

Deactivate Protection: Stops the service.

Emergency Trigger
With protection activated, shake the phone (or tap “Simulate Shake” if added).

A Toast appears (Day 2) or real SMS + call (Day 3).

The SMS contains a Google Maps link with your current location.

Password Reset
Go to Settings → Reset Password (button below Dark Mode switch).

Answer the security question correctly.

Enter a new password (twice) → password changed.

👥 Team Division (4‑Day Progressive Demo)
Day	Focus	Member 1 (Sensor & Service)	Member 2 (Comm)	Member 3 (UI & Security)
1	Calculator + Password	Stub files	Stub files	All activities, layouts, PrefManager
2	Shake detection + Toast	EmergencyService, ShakeDetector	EmergencyHandler (Toast)	SettingsActivity (full), layout
3	Real SMS & call	–	LocationHelper, SmsHelper, CallHelper, real EmergencyHandler	–
4	Full demo	Test & demo	Test & demo	Test & demo
🔐 Permissions Required
The app requests the following permissions at runtime:

SEND_SMS – to send emergency SMS.

ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION – to get GPS location.

CALL_PHONE – to initiate emergency call.

FOREGROUND_SERVICE – to run background monitoring.

VIBRATE – optional, for haptic feedback.

📸 Screenshots
Calculator (Dark)	Settings	History
(Add your screenshots here)	(Add your screenshots here)	(Add your screenshots here)
🧪 Testing
Unit tests: Calculator functions (+, −, ×, ÷, %, ^) manually verified.

Integration tests: Password → Settings → Activate → Shake → SMS/Call flow tested on Xiaomi Mi A1 (Android 10).

GPS fallback: Works with GPS or network provider.

Dark mode: Theme switches correctly; history and display backgrounds unify.

⚠️ Limitations & Future Improvements
Limitations
Silent call not possible on Android 10+ due to system restrictions. The call rings normally.

GPS dependency: Requires location to be enabled; may be slow indoors.

SMS balance required on the SIM card.

Shake sensitivity may need tuning per device.

Future Improvements
Multiple emergency contacts.

Cloud backup of settings (optional).

Fingerprint authentication.

Real‑time location sharing via background updates.

Add a ) button for full parenthesis support.

📄 License
This project is developed for academic purposes as a final year diploma/degree project.
Free to use and modify for educational use.

🙏 Acknowledgements
Android Developer Documentation

Stack Overflow community

Project supervisor / instructor

📞 Contact
GitHub Repository: https://github.com/Hishamwalid/EmergencyShake
Team Members: Hisham, Apurbo, Afif (Member 1, 2, 3)

This README corresponds to the final version of the Silent Emergency Alert System (Ecalculator) – monochrome design, permanent history, shake‑activated emergency alerts, and fully offline operation.
