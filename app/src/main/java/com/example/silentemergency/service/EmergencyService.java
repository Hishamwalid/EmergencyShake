package com.example.silentemergency.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import com.example.silentemergency.EmergencyHandler;
import com.example.silentemergency.utils.PrefManager;

public class EmergencyService extends Service {
    private SensorManager sensorManager;
    private ShakeDetector shakeDetector;
    private PrefManager prefManager;

    @Override
    public void onCreate() {
        super.onCreate();
        prefManager = new PrefManager(this);
        startForeground(1, createNotification());
        setupShakeDetector();
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "emergency_channel", "Emergency Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, "emergency_channel")
                .setContentTitle("Emergency Protection Active")
                .setContentText("Monitoring shakes...")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .build();
    }

    private void setupShakeDetector() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        float sensitivity = prefManager.getShakeSensitivity();
        int requiredShakes = prefManager.getShakeCount();
        shakeDetector = new ShakeDetector(sensitivity, requiredShakes, () -> {
            Intent intent = new Intent(this, EmergencyHandler.class);
            startService(intent);
        });
        sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null && shakeDetector != null) {
            sensorManager.unregisterListener(shakeDetector);
        }
    }
}