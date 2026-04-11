package com.example.silentemergency.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import com.example.silentemergency.utils.PrefManager;

public class LocationHelper {
    private Context context;

    public LocationHelper(Context context) {
        this.context = context;
    }

    /**
     * Generates the custom message: "I was going from [X] to [Y]. I am in danger..."
     * This is what the EmergencyHandler calls to get the text.
     */
    @SuppressLint("MissingPermission")
    public String generateEmergencyMessage() {
        PrefManager pref = new PrefManager(context);
        String start = pref.getStartingPoint();
        String dest = pref.getDestination();

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location location = null;
        try {
            location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Build the prefix
        String routeInfo = "";
        if (!start.isEmpty() && !dest.isEmpty()) {
            routeInfo = "I was going from " + start + " to " + dest + ". ";
        }

        if (location != null) {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            // Using the standard maps link format
            String mapLink = "https://www.google.com/maps?q=" + lat + "," + lon;
            return routeInfo + "I am in danger. Please help. My location: " + mapLink;
        } else {
            return routeInfo + "I am in danger. Please help. (Location unavailable)";
        }
    }

    // Legacy support for other parts of the app
    public void sendLocationAndAlert(String phoneNumber, boolean smsOnly, String customPrefix) {
        String message = customPrefix + generateEmergencyMessage();
        SmsHelper.sendSMS(context, phoneNumber, message);

        if (!smsOnly) {
            CallHelper.makeCall(context, phoneNumber);
        }
    }
}