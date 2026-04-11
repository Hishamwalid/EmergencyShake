package com.example.silentemergency.helper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

public class SmsHelper {

    private static final String TAG = "SmsHelper";

    /**
     * Sends SMS silently in the background using the user's default SMS SIM.
     * Returns TRUE if sent successfully, FALSE if blocked (triggers manual fallback).
     */
    public static boolean sendSMS(Context context, String number, String message) {
        try {
            SmsManager smsManager = getDefaultSimSmsManager(context);
            if (smsManager == null) {
                Log.w(TAG, "SmsManager is null");
                return false;
            }

            if (message.length() > 160) {
                java.util.ArrayList<String> parts = smsManager.divideMessage(message);
                smsManager.sendMultipartTextMessage(number, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(number, null, message, null, null);
            }

            Log.d(TAG, "SMS sent successfully to: " + number);
            return true;

        } catch (SecurityException e) {
            Log.e(TAG, "SMS permission denied: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "SMS failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets SmsManager for the user's chosen default SMS SIM.
     * Fixes the issue where getDefault() ignores the SIM the user picked in settings.
     */
    private static SmsManager getDefaultSimSmsManager(Context context) {
        // Android 12+ — use context-aware getSystemService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Get the subscription ID of the user's default SMS SIM
            int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                // ✅ Use the specific SIM the user set as default for SMS
                SmsManager manager = SmsManager.getSmsManagerForSubscriptionId(subId);
                if (manager != null) return manager;
            }
            // Fallback to system service
            return context.getSystemService(SmsManager.class);
        }

        // Android 6–11 — use subscription ID directly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            int subId = SubscriptionManager.getDefaultSmsSubscriptionId();
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                // ✅ Use the specific SIM the user set as default for SMS
                return SmsManager.getSmsManagerForSubscriptionId(subId);
            }
        }

        // Final fallback for older devices
        return SmsManager.getDefault();
    }

    /**
     * Opens the default SMS app pre-filled with the number and message.
     * Used when background SMS is blocked — user taps Send manually.
     * Only opens for the FIRST contact.
     */
    public static void openManualSms(Context context, String number, String message) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + number));
            intent.putExtra("sms_body", message);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "Opened SMS app for manual send to: " + number);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open SMS app: " + e.getMessage());
            e.printStackTrace();
        }
    }
}