package com.example.silentemergency.helper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

public class SmsHelper {

    /**
     * Sends SMS. Returns TRUE if sent automatically,
     * FALSE if it needs manual fallback.
     */
    public static boolean sendSMS(Context context, String number, String message) {
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager == null) return false;

            if (message.length() > 160) {
                java.util.ArrayList<String> parts = smsManager.divideMessage(message);
                smsManager.sendMultipartTextMessage(number, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(number, null, message, null, null);
            }
            return true; // Success

        } catch (Exception e) {
            Log.e("SmsHelper", "Auto-send failed: " + e.getMessage());
            return false; // Trigger fallback
        }
    }

    // This is now used by EmergencyHandler to handle the 3-contact fallback
    public static void openManualSms(Context context, String multiNumbers, String message) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + multiNumbers));
            intent.putExtra("sms_body", message);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}