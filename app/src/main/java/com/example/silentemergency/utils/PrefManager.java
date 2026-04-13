package com.example.silentemergency.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefManager {

    private static final String PREF_NAME              = "SilentEmergencyPrefs";
    private static final String KEY_IS_FIRST_TIME      = "isFirstTime";
    private static final String KEY_PASSWORD           = "password";
    private static final String KEY_SECURITY_QUESTION  = "securityQuestion";
    private static final String KEY_SECURITY_ANSWER    = "securityAnswer";
    private static final String KEY_SHAKE_SENSITIVITY  = "shakeSensitivity";
    private static final String KEY_SHAKE_COUNT        = "shakeCount";
    private static final String KEY_SHAKE_WINDOW       = "shakeWindow";
    private static final String KEY_DARK_MODE          = "darkMode";
    private static final String KEY_EMERGENCY_NUMBER   = "emergencyNumber";
    private static final String KEY_SMS_ONLY           = "sms_only";
    private static final String KEY_STARTING_POINT     = "starting_point";
    private static final String KEY_DESTINATION        = "destination";
    private static final String KEY_GESTURE_MODE       = "gesture_mode";
    private static final String KEY_POWER_SHAKE_WINDOW = "power_shake_window";
    private static final String KEY_POWER_PRESS_COUNT  = "power_press_count";
    private static final String KEY_POWER_PRESS_WINDOW = "power_press_window";

    // Live GPS — stored as String to preserve full double precision.
    // Storing as float loses the last 1-2 decimal places (~1 metre accuracy).
    private static final String KEY_LIVE_LAT           = "live_lat_str";
    private static final String KEY_LIVE_LON           = "live_lon_str";
    private static final String KEY_LIVE_TIME          = "live_location_time";

    private final SharedPreferences       prefs;
    private final SharedPreferences.Editor editor;

    public PrefManager(Context context) {
        prefs  = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public SharedPreferences getPrefs() { return prefs; }

    // ── First-time flag ───────────────────────────────────────────────────────
    public boolean isFirstTime() {
        return prefs.getBoolean(KEY_IS_FIRST_TIME, true);
    }
    public void setFirstTime(boolean isFirst) {
        editor.putBoolean(KEY_IS_FIRST_TIME, isFirst);
        editor.apply();
    }

    // ── Password ──────────────────────────────────────────────────────────────
    public void setPassword(String password) {
        editor.putString(KEY_PASSWORD, password);
        editor.apply();
    }
    public String getPassword() {
        return prefs.getString(KEY_PASSWORD, "");
    }

    // ── Security question ─────────────────────────────────────────────────────
    public void setSecurityQuestion(String question) {
        editor.putString(KEY_SECURITY_QUESTION, question);
        editor.apply();
    }
    public String getSecurityQuestion() {
        return prefs.getString(KEY_SECURITY_QUESTION, "");
    }
    public void setSecurityAnswer(String answer) {
        editor.putString(KEY_SECURITY_ANSWER, answer.toLowerCase());
        editor.apply();
    }
    public boolean checkSecurityAnswer(String answer) {
        return prefs.getString(KEY_SECURITY_ANSWER, "")
                .equals(answer.toLowerCase());
    }

    // ── Shake settings ────────────────────────────────────────────────────────
    public float getShakeSensitivity() {
        return prefs.getFloat(KEY_SHAKE_SENSITIVITY, 15.0f);
    }
    public void setShakeSensitivity(float value) {
        editor.putFloat(KEY_SHAKE_SENSITIVITY, value);
        editor.apply();
    }
    public int getShakeCount() {
        return prefs.getInt(KEY_SHAKE_COUNT, 3);
    }
    public void setShakeCount(int count) {
        editor.putInt(KEY_SHAKE_COUNT, count);
        editor.apply();
    }
    public int getShakeWindow() {
        return prefs.getInt(KEY_SHAKE_WINDOW, 5);
    }
    public void setShakeWindow(int seconds) {
        editor.putInt(KEY_SHAKE_WINDOW, seconds);
        editor.apply();
    }

    // ── Theme ─────────────────────────────────────────────────────────────────
    public void setDarkMode(boolean isDark) {
        editor.putBoolean(KEY_DARK_MODE, isDark);
        editor.apply();
    }
    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    // ── Emergency contacts (slot-based) ───────────────────────────────────────
    public void setEmergencyNumber(int slot, String number) {
        editor.putString("emergency_number_" + slot, number);
        editor.apply();
    }
    public String getEmergencyNumber(int slot) {
        return prefs.getString("emergency_number_" + slot, "");
    }
    // Legacy single-contact methods — kept for compatibility
    public void setEmergencyNumber(String number) {
        editor.putString(KEY_EMERGENCY_NUMBER, number);
        editor.apply();
    }
    public String getEmergencyNumber() {
        return prefs.getString(KEY_EMERGENCY_NUMBER, "");
    }

    // ── SMS-only mode ─────────────────────────────────────────────────────────
    public void setSmsOnly(boolean smsOnly) {
        editor.putBoolean(KEY_SMS_ONLY, smsOnly);
        editor.apply();
    }
    public boolean isSmsOnly() {
        return prefs.getBoolean(KEY_SMS_ONLY, false);
    }

    // ── Route ─────────────────────────────────────────────────────────────────
    public void setStartingPoint(String address) {
        editor.putString(KEY_STARTING_POINT, address);
        editor.apply();
    }
    public String getStartingPoint() {
        return prefs.getString(KEY_STARTING_POINT, "");
    }
    public void setDestination(String address) {
        editor.putString(KEY_DESTINATION, address);
        editor.apply();
    }
    public String getDestination() {
        return prefs.getString(KEY_DESTINATION, "");
    }

    // ── Gesture mode ──────────────────────────────────────────────────────────
    public void setGestureMode(String mode) {
        editor.putString(KEY_GESTURE_MODE, mode);
        editor.apply();
    }
    public String getGestureMode() {
        return prefs.getString(KEY_GESTURE_MODE, "shake");
    }

    // ── Power press settings ──────────────────────────────────────────────────
    public int getPowerShakeWindow() {
        return prefs.getInt(KEY_POWER_SHAKE_WINDOW, 5);
    }
    public void setPowerShakeWindow(int seconds) {
        editor.putInt(KEY_POWER_SHAKE_WINDOW, seconds);
        editor.apply();
    }
    public void setPowerPressCount(int count) {
        editor.putInt(KEY_POWER_PRESS_COUNT, count);
        editor.apply();
    }
    public int getPowerPressCount() {
        return prefs.getInt(KEY_POWER_PRESS_COUNT, 3);
    }
    public void setPowerPressWindow(int seconds) {
        editor.putInt(KEY_POWER_PRESS_WINDOW, seconds);
        editor.apply();
    }
    public int getPowerPressWindow() {
        return prefs.getInt(KEY_POWER_PRESS_WINDOW, 2);
    }

    // ── Live GPS location ─────────────────────────────────────────────────────
    // Stored as String to preserve full double precision.
    // Float would silently truncate the last decimal places.
    public void setLiveLocation(double lat, double lon) {
        editor.putString(KEY_LIVE_LAT, String.valueOf(lat));
        editor.putString(KEY_LIVE_LON, String.valueOf(lon));
        editor.putLong(KEY_LIVE_TIME, System.currentTimeMillis());
        editor.apply();
    }

    public double getLiveLat() {
        try {
            return Double.parseDouble(prefs.getString(KEY_LIVE_LAT, "0.0"));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public double getLiveLon() {
        try {
            return Double.parseDouble(prefs.getString(KEY_LIVE_LON, "0.0"));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public long getLiveLocationTime() {
        return prefs.getLong(KEY_LIVE_TIME, 0L);
    }

    // Returns true if a live fix was stored within the last 5 minutes
    public boolean hasRecentLiveLocation() {
        long age = System.currentTimeMillis() - getLiveLocationTime();
        return getLiveLocationTime() > 0 && age < 5 * 60 * 1000L;
    }

    public void clearLiveLocation() {
        editor.remove(KEY_LIVE_LAT);
        editor.remove(KEY_LIVE_LON);
        editor.remove(KEY_LIVE_TIME);
        editor.apply();
    }
}