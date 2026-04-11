package com.example.silentemergency.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.widget.Toast;

public class LocationHelper {
    private Context context;

    public LocationHelper(Context context) {
        this.context = context;
    }

    // Legacy method for compatibility
    public void sendLocationAndAlert(String phoneNumber, boolean smsOnly) {
        sendLocationAndAlert(phoneNumber, smsOnly, "");
    }

    @SuppressLint("MissingPermission")
    public void sendLocationAndAlert(String phoneNumber, boolean smsOnly, String customPrefix) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        Location location = null;
        try {
            location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (location == null) {
                location = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String message;
        if (location != null) {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            String mapLink = "https://maps.google.com/?q=" + lat + "," + lon;
            message = customPrefix + "I am in danger. Please help. My location: " + mapLink;
        } else {
            // Send without location rather than failing silently
            message = customPrefix + "I am in danger. Please help. (Location unavailable — GPS may be off)";
            Toast.makeText(context, "Warning: Could not get location. Alert sent without coordinates.", Toast.LENGTH_LONG).show();
        }

        // ✅ Pass context so SmsHelper can fall back to SMS Intent if needed
        SmsHelper.sendSMS(context, phoneNumber, message);

        if (!smsOnly) {
            CallHelper.makeCall(context, phoneNumber);
        }
    }
}