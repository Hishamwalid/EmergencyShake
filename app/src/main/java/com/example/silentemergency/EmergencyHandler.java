package com.example.silentemergency;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;
import com.example.silentemergency.helper.LocationHelper;
import com.example.silentemergency.utils.PrefManager;

public class EmergencyHandler extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PrefManager pref = new PrefManager(this);
        boolean smsOnly = pref.isSmsOnly();
        String startingPoint = pref.getStartingPoint();
        String destination = pref.getDestination();
        boolean anyContact = false;
        int sentCount = 0;

        for (int i = 1; i <= 3; i++) {
            String contact = pref.getEmergencyNumber(i);
            if (contact != null && !contact.isEmpty()) {
                anyContact = true;
                String phoneNumber = contact.contains(":") ? contact.split(":")[1] : contact;

                // Build custom prefix
                StringBuilder customPrefix = new StringBuilder();
                if (!startingPoint.isEmpty() && !destination.isEmpty()) {
                    customPrefix.append("I was going from ").append(startingPoint).append(" to ").append(destination).append(". ");
                } else if (!startingPoint.isEmpty()) {
                    customPrefix.append("I was at ").append(startingPoint).append(". ");
                } else if (!destination.isEmpty()) {
                    customPrefix.append("I was heading to ").append(destination).append(". ");
                }

                new LocationHelper(this).sendLocationAndAlert(phoneNumber, smsOnly, customPrefix.toString());
                sentCount++;
            }
        }

        if (anyContact) {
            String action = smsOnly ? "SMS" : "SMS and call";
            Toast.makeText(this, "Emergency alert sent to " + sentCount + " contact(s) (" + action + ")", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "No emergency contacts set", Toast.LENGTH_SHORT).show();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}