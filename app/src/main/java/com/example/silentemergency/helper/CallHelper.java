package com.example.silentemergency.helper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.widget.Toast;

public class CallHelper {

    private static final String TAG = "CallHelper";

    /**
     * Places a call using ACTION_CALL.
     * Respects the user's default voice SIM on dual-SIM phones.
     * Falls back to dialer if CALL_PHONE is denied.
     */
    public static void makeCall(Context context, String number) {
        String cleanNumber = number.trim();

        try {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + cleanNumber));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // ✅ On dual-SIM phones, pass the default voice subscription
            // so the call goes through the SIM the user has set as default
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                int subId = SubscriptionManager.getDefaultVoiceSubscriptionId();
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    intent.putExtra("com.android.phone.extra.slot", subId);
                    // Some OEMs (Samsung, Xiaomi) use this extra
                    intent.putExtra("subscription", subId);
                }
            }

            context.startActivity(intent);
            Log.d(TAG, "Call placed to: " + cleanNumber);

        } catch (SecurityException e) {
            Log.e(TAG, "CALL_PHONE denied — opening dialer: " + e.getMessage());
            openDialer(context, cleanNumber);
        } catch (Exception e) {
            Log.e(TAG, "Call failed — opening dialer: " + e.getMessage());
            openDialer(context, cleanNumber);
        }
    }

    private static void openDialer(Context context, String number) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + number));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context,
                    "Call permission blocked — opening dialer",
                    Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}