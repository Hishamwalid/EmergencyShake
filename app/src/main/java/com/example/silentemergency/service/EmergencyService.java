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
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.example.silentemergency.EmergencyHandler;
import com.example.silentemergency.utils.PrefManager;

public class EmergencyService extends Service {
    private static final String CHANNEL_ID = "emergency_channel";
    private static final String TAG = "EmergencyService";
    private SensorManager sensorManager;
    private ShakeDetector shakeDetector;
    private PrefManager prefManager;

    @Override
    public void onCreate() {
        super.onCreate();
        prefManager = new PrefManager(this);
        createNotificationChannel();
        // Do NOT call startForeground here – call it in onStartCommand for Android 16 stability
        setupShakeDetector();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Emergency Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Monitors shake gestures for emergency alert");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("VeilCal Protection Active")
                .setContentText("Monitoring for emergency shake...")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setPriority(NotificationCompat.PRIORITY_LOW)
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
        // Critical: startForeground must be called here for Android 14+ stability
        try {
            Notification notification = createNotification();
            startForeground(1, notification);
        } catch (Exception e) {
            Log.e(TAG, "Error in onStartCommand: " + e.getMessage());
        }
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