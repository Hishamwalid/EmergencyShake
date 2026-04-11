package com.example.silentemergency.helper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.widget.Toast;

public class SmsHelper {

    public static void sendSMS(Context context, String number, String message) {
        // Try direct SMS first
        try {
            SmsManager smsManager;
            // Android 12+ deprecates getDefault() — use context-aware version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager == null) {
                fallbackSmsIntent(context, number, message);
                return;
            }

            // Split long messages automatically
            if (message.length() > 160) {
                java.util.ArrayList<String> parts = smsManager.divideMessage(message);
                smsManager.sendMultipartTextMessage(number, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(number, null, message, null, null);
            }

        } catch (SecurityException e) {
            // OS blocked direct SMS — open SMS app pre-filled instead
            e.printStackTrace();
            fallbackSmsIntent(context, number, message);

        } catch (Exception e) {
            e.printStackTrace();
            fallbackSmsIntent(context, number, message);
        }
    }

    // Opens default SMS app with number and message pre-filled
    // User just taps Send — works even when SEND_SMS is blocked
    private static void fallbackSmsIntent(Context context, String number, String message) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + number));
            intent.putExtra("sms_body", message);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context,
                    "SMS permission blocked — opening SMS app to send manually",
                    Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(context,
                    "Could not send SMS to " + number + ". Please check permissions.",
                    Toast.LENGTH_LONG).show();
        }
    }
}