package com.example.silentemergency.service;

import android.app.*;
import android.content.*;
import android.hardware.*;
import android.os.*;
import androidx.core.app.NotificationCompat;

import com.example.silentemergency.EmergencyHandler;

public class EmergencyService extends Service {

    SensorManager sm;

    @Override
    public void onCreate() {
        super.onCreate();

        startForeground(1, createNotification());
        startSensor();
    }

    private Notification createNotification() {

        String id = "channel";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(id, "Emergency", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }

        return new NotificationCompat.Builder(this, id)
                .setContentTitle("Calculator Running")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .build();
    }

    private void startSensor() {

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        sm.registerListener(new ShakeDetector(() -> {
            startService(new Intent(this, EmergencyHandler.class));
        }), s, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public IBinder onBind(Intent i) { return null; }
}