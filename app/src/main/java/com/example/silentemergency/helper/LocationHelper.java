package com.example.silentemergency.helper;

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

    public void sendLocationAndAlert(String phoneNumber, boolean smsOnly, String customPrefix) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (location == null) {
            Toast.makeText(context, "Unable to get location. Please enable GPS.", Toast.LENGTH_LONG).show();
            return;
        }
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        String mapLink = "https://maps.google.com/?q=" + lat + "," + lon;
        String message = customPrefix + "I am in danger. Please help. My location: " + mapLink;

        SmsHelper.sendSMS(phoneNumber, message);
        if (!smsOnly) {
            CallHelper.makeCall(context, phoneNumber);
        }
    }
}