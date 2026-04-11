package com.example.silentemergency.helper;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public class CallHelper {

    public static void makeCall(Context context, String number) {
        // Try direct call first (requires CALL_PHONE permission)
        try {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + number));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);

        } catch (SecurityException e) {
            // CALL_PHONE denied — open dialer pre-filled instead
            e.printStackTrace();
            fallbackDialIntent(context, number);

        } catch (Exception e) {
            e.printStackTrace();
            fallbackDialIntent(context, number);
        }
    }

    // Opens dialer pre-filled — no permission needed, user just taps call
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