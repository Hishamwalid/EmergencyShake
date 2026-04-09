package com.example.silentemergency.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefManager {
    private static final String PREF_NAME = "SilentEmergencyPrefs";
    private static final String KEY_IS_FIRST_TIME = "isFirstTime";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_SECURITY_QUESTION = "securityQuestion";
    private static final String KEY_SECURITY_ANSWER = "securityAnswer";
    private static final String KEY_SHAKE_SENSITIVITY = "shakeSensitivity";
    private static final String KEY_SHAKE_COUNT = "shakeCount";
    private static final String KEY_DARK_MODE = "darkMode";
    // Legacy single contact (keep for compatibility, but use slot methods)
    private static final String KEY_EMERGENCY_NUMBER = "emergencyNumber";
    // SMS-only preference (no phone call)
    private static final String KEY_SMS_ONLY = "sms_only";

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    public PrefManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    // Direct access to SharedPreferences (for history persistence)
    public SharedPreferences getPrefs() {
        return prefs;
    }

    // ---------- First time ----------
    public boolean isFirstTime() {
        return prefs.getBoolean(KEY_IS_FIRST_TIME, true);
    }

    public void setFirstTime(boolean isFirst) {
        editor.putBoolean(KEY_IS_FIRST_TIME, isFirst);
        editor.apply();
    }

    // ---------- Password ----------
    public void setPassword(String password) {
        editor.putString(KEY_PASSWORD, password);
        editor.apply();
    }

    public String getPassword() {
        return prefs.getString(KEY_PASSWORD, "");
    }

    // ---------- Security question & answer ----------
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
        return prefs.getString(KEY_SECURITY_ANSWER, "").equals(answer.toLowerCase());
    }

    // ---------- Shake configuration ----------
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

    // ---------- Dark mode ----------
    public void setDarkMode(boolean isDark) {
        editor.putBoolean(KEY_DARK_MODE, isDark);
        editor.apply();
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    // ---------- Emergency contacts (up to 3 slots) ----------
    public void setEmergencyNumber(int slot, String number) {
        editor.putString("emergency_number_" + slot, number);
        editor.apply();
    }

    public String getEmergencyNumber(int slot) {
        return prefs.getString("emergency_number_" + slot, "");
    }

    // Legacy single contact (used by old code – keep for compatibility)
    public void setEmergencyNumber(String number) {
        editor.putString(KEY_EMERGENCY_NUMBER, number);
        editor.apply();
    }

    public String getEmergencyNumber() {
        return prefs.getString(KEY_EMERGENCY_NUMBER, "");
    }

    // ---------- SMS only (no phone call) ----------
    public void setSmsOnly(boolean smsOnly) {
        editor.putBoolean(KEY_SMS_ONLY, smsOnly);
        editor.apply();
    }

    public boolean isSmsOnly() {
        return prefs.getBoolean(KEY_SMS_ONLY, false);
    }
}