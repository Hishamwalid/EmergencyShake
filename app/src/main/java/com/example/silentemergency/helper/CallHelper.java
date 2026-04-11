package com.example.silentemergency.helper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public class CallHelper {

    // Overload for manual taps (keeps your original functionality)
    public static void makeCall(Context context, String number) {
        makeCall(context, number, false);
    }

    // New version for automated emergency loops
    public static boolean makeCall(Context context, String number, boolean isAutomated) {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + number));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true; // Call successfully initiated

        } catch (SecurityException e) {
            e.printStackTrace();
            // Only open the fallback dialer if the user manually tapped a button
            if (!isAutomated) fallbackDialIntent(context, number);
            return false;

        } catch (Exception e) {
            e.printStackTrace();
            if (!isAutomated) fallbackDialIntent(context, number);
            return false;
        }
    }

    private static void fallbackDialIntent(Context context, String number) {
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + number));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context,
                    "Call permission blocked — opening dialer to call manually",
                    Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(context,
                    "Could not call " + number + ". Please check permissions.",
                    Toast.LENGTH_LONG).show();
        }
    }
}