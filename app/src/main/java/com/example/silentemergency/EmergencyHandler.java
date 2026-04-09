package com.example.silentemergency;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;
import com.example.silentemergency.utils.PrefManager;

public class EmergencyHandler extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PrefManager pref = new PrefManager(this);
        boolean anyContact = false;
        for (int i = 1; i <= 3; i++) {
            String contact = pref.getEmergencyNumber(i);
            if (contact != null && !contact.isEmpty()) {
                anyContact = true;
                break;
            }
        }
        if (anyContact) {
            Toast.makeText(this, "EMERGENCY! SMS & call would be sent to all contacts.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "No emergency contacts set", Toast.LENGTH_SHORT).show();
        }
        return START_NOT_STICKY;
    }
    @Override
    public IBinder onBind(Intent intent) { return null; }
}