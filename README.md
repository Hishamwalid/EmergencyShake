# Silent Emergency Alert System – Ecalculator

**A disguised calculator that silently alerts an emergency contact when shaken.**

---

##  Overview
**Ecalculator** is a personal safety Android application designed for security through discretion. It looks and works like a standard calculator, but serves as a hidden emergency gateway. 

When the phone is shaken, the app automatically sends an SMS with your GPS location and initiates a phone call to a pre-registered contact—completely offline.

---

##  Core Features
* **Functional Calculator:** Standard math operations (+, −, ×, ÷, %, ^).
* **Stealth Access:** Type your password then `=` to open hidden settings.
* **Smart Shake Detection:** Configurable sensitivity and shake count.
* **Background Service:** Stays active even when the app is closed.
* **Emergency Response:** Automated SMS (Google Maps link) and phone call.
* **Privacy Focused:** No internet required; all data stays on your device.

---

##  Tech Stack
* **Language:** Java 17
* **IDE:** Android Studio
* **Compatibility:** Min SDK 24 (Android 7.0) / Target SDK 34 (Android 14)
* **Build System:** Gradle 8.7 / AGP 9.0.0
* **Key APIs:** SensorManager, LocationManager, SmsManager, TelephonyManager

---

##  Project Structure
* **Activities:** CalculatorActivity, SettingsActivity, FirstTimeSetupActivity
* **Core Logic:** EmergencyHandler, ShakeDetector, EmergencyService
* **Helpers:** LocationHelper, SmsHelper, CallHelper
* **Data:** PrefManager (SharedPreferences)

---

##  Setup & Build
1. **Clone:** `git clone https://github.com/Hishamwalid/EmergencyShake.git`
2. **Open:** Open the project in Android Studio and sync Gradle.
3. **Build:** Go to `Build > Build APK(s)`.
4. **Install:** Deploy `app-debug.apk` to a real device (enable "Unknown Sources").

---

##  How to Use
1. **First Launch:** Set your master password and security question.
2. **Enter Settings:** On the calculator, type **[Your Password]** then tap **`=`**.
3. **Configure:** * Input an emergency phone number.
   * Adjust shake sensitivity and count.
   * Tap **Activate Protection**.
4. **Trigger:** Shake the device to send the alert.
5. **Recovery:** Use the security question to reset your password if forgotten.

---

##  Permissions
The following are requested at runtime:
* SMS & Phone Call access
* Fine & Coarse Location
* Foreground Service & Vibration

##  Limitations
* **Android 10+:** Silent background calls are restricted by Google; the dialer will appear as a standard call.
* **Hardware:** Requires an Accelerometer and GPS.
* **Balance:** Requires mobile credit/plan to send SMS alerts.

---

## 📄 License
Academic project – free for educational use.

**Repository:** https://github.com/Hishamwalid/EmergencyShake
