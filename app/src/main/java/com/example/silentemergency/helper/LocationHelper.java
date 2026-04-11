package com.example.silentemergency.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import com.example.silentemergency.utils.PrefManager;

public class LocationHelper {

    private final Context context;

    public LocationHelper(Context context) {
        this.context = context;
    }

    /**
     * Builds the full emergency message including route info and live location.
     * Called by EmergencyHandler to get the message text.
     */
    @SuppressLint("MissingPermission")
    public String generateEmergencyMessage() {
        PrefManager pref = new PrefManager(context);
        String start = pref.getStartingPoint();
        String dest  = pref.getDestination();

        // Build route prefix
        String routeInfo = "";
        if (!start.isEmpty() && !dest.isEmpty()) {
            routeInfo = "I was going from " + start + " to " + dest + ". ";
        } else if (!start.isEmpty()) {
            routeInfo = "I was at " + start + ". ";
        } else if (!dest.isEmpty()) {
            routeInfo = "I was heading to " + dest + ". ";
        }

        // Try to get location
        Location location = null;
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null)
                    location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location == null)
                    location = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (location != null) {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            String mapLink = "https://www.google.com/maps?q=" + lat + "," + lon;
            return routeInfo + "I am in danger. Please help. My location: " + mapLink;
        } else {
            return routeInfo + "I am in danger. Please help. (Location unavailable — GPS may be off)";
        }
    }

    /**
     * Legacy method kept for compatibility with any other callers.
     * Note: customPrefix is ignored here since generateEmergencyMessage
     * already builds the full message with route info.
     */
    public void sendLocationAndAlert(String phoneNumber, boolean smsOnly, String customPrefix) {
        String message = generateEmergencyMessage();
        SmsHelper.sendSMS(context, phoneNumber, message);
        if (!smsOnly) {
            CallHelper.makeCall(context, phoneNumber);
        }
    }

    public void sendLocationAndAlert(String phoneNumber, boolean smsOnly) {
        sendLocationAndAlert(phoneNumber, smsOnly, "");
    }
}