<img width="1024" height="1024" alt="ChatGPT Image Apr 4, 2026, 10_38_02 PM" src="https://github.com/user-attachments/assets/32a5af59-9bab-418c-b80c-ca0f598f2e16" />

**VeilCal**
===========

### **Silent Emergency Alert System**

**VeilCal** is a personal safety Android application designed for security through discretion. On the surface, it functions as a fully operational, bug-free calculator. Beneath the interface lies a hidden emergency gateway designed to provide immediate assistance during critical situations without drawing attention.

* * * * *

📱 Overview
-----------

In many emergency scenarios, opening a traditional safety app can escalate a threat. **VeilCal** solves this by hiding its true purpose. When the device is shaken, the app silently triggers an emergency protocol: sending an SMS with real-time GPS coordinates and initiating a phone call to a pre-registered contact---all without requiring an active internet connection.

* * * * *

✨ Core Features
---------------

-   **Functional Calculator:** Supports standard math operations (`+`, `-`, `×`, `÷`, `%`, `^`) with a stable, logic-error-free interface.

-   **Stealth Access:** Type your master password followed by the `=` key to unlock the hidden settings dashboard.

-   **Smart Shake Detection:** Fully configurable sensitivity and shake count to prevent accidental triggers.

-   **Background Service:** Stays active and vigilant even when the app is closed or the screen is locked.

-   **Emergency Response:** Automated SMS (includes a Google Maps location link) and immediate phone call initiation.

-   **Customization:** Includes theme options and an official branded UI for a polished look.

-   **Privacy Focused:** Operates entirely offline. No data ever leaves your device except for the emergency alerts you authorize.

* * * * *

🛠️ Tech Stack
--------------

-   **Language:** Java 17

-   **IDE:** Android Studio

-   **Compatibility:** Min SDK 24 (Android 7.0) / Target SDK 34 (Android 14)

-   **Build System:** Gradle 8.7 / AGP 9.0.0

-   **Key APIs:** `SensorManager`, `LocationManager`, `SmsManager`, `TelephonyManager`

* * * * *

📂 Project Structure
--------------------

-   **Activities:** `CalculatorActivity`, `SettingsActivity`, `FirstTimeSetupActivity`

-   **Core Logic:** `EmergencyHandler`, `ShakeDetector`, `EmergencyService`

-   **Helpers:** `LocationHelper`, `SmsHelper`, `CallHelper`

-   **Data Management:** `PrefManager` (Utilizing SharedPreferences for local storage)

* * * * *

🚀 Setup & Build
----------------

1.  **Clone the Repository:**

    Bash

    ```
    git clone https://github.com/Hishamwalid/EmergencyShake.git

    ```

2.  **Open Project:** Import the folder into **Android Studio** and sync Gradle files.

3.  **Build:** Navigate to `Build > Build APK(s)` to generate the installer.

4.  **Install:** Deploy the `app-debug.apk` to a physical Android device. *Note: Ensure "Install from Unknown Sources" is enabled.*

* * * * *

📖 How to Use
-------------

1.  **First Launch:** Set your master password and choose a custom security question for recovery.

2.  **Access Hidden Hub:** On the calculator interface, input your password and press `=`.

3.  **Configuration:** * Add up to 3 emergency contacts.

    -   Adjust shake sensitivity and trigger count via the Gear icon.

    -   Tap **Activate Protection** to arm the system.

4.  **Triggering:** Simply shake the device according to your configured settings to send the SOS.

5.  **Recovery:** If you forget your password, use the security question reset option on the main screen.

* * * * *

🔐 Permissions & Limitations
----------------------------

### **Permissions Requested**

-   **SMS & Phone:** To send alerts and initiate emergency calls.

-   **Location (Fine & Coarse):** To include precise GPS coordinates in the SMS.

-   **Foreground Service:** To ensure the shake detection remains active in the background.

### **Limitations**

-   **Android 10+:** Due to Google's privacy restrictions, background calls will appear via the standard system dialer rather than being completely invisible.

-   **Hardware:** Requires a device equipped with an Accelerometer and GPS module.

-   **Carrier Charges:** Sending SMS alerts requires a mobile plan with active credit.

* * * * *

📄 License
----------

*Academic project -- Distributed for educational and personal safety use.*

**Repository:** <https://github.com/Hishamwalid/EmergencyShake>
