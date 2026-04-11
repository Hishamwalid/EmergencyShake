package com.example.silentemergency.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import com.example.silentemergency.EmergencyHandler;
import com.example.silentemergency.utils.PrefManager;

public class EmergencyService extends Service {
    private SensorManager sensorManager;
    private ShakeDetector shakeDetector;
    private PrefManager prefManager;

    // For power button detection
    private long lastPowerPressTime = 0;
    private int powerPressCount = 0;
    private final Handler powerHandler = new Handler(Looper.getMainLooper());
    private Runnable resetPowerPressRunnable;

    // BroadcastReceiver to detect power button press (via screen off)
    private final BroadcastReceiver powerButtonReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                long now = System.currentTimeMillis();
                String mode = prefManager.getGestureMode();

                if (mode.equals("power_only")) {
                    // Count presses within 2 seconds
                    if (now - lastPowerPressTime < 2000) {
                        powerPressCount++;
                    } else {
                        powerPressCount = 1;
                    }
                    lastPowerPressTime = now;
                    int required = prefManager.getPowerPressCount();
                    if (powerPressCount >= required) {
                        // Trigger emergency
                        triggerEmergency();
                        powerPressCount = 0;
                        if (resetPowerPressRunnable != null) powerHandler.removeCallbacks(resetPowerPressRunnable);
                    } else {
                        // Reset count after 2 seconds of inactivity
                        if (resetPowerPressRunnable != null) powerHandler.removeCallbacks(resetPowerPressRunnable);
                        resetPowerPressRunnable = () -> powerPressCount = 0;
                        powerHandler.postDelayed(resetPowerPressRunnable, 2000);
                    }
                } else if (mode.equals("power_shake")) {
                    // Record the time of power press for shake+power mode
                    lastPowerPressTime = now;
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefManager = new PrefManager(this);
        startForeground(1, createNotification());
        setupShakeDetector();
        registerPowerButtonReceiver();
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
                .setContentText("Monitoring...")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .build();
    }

    private void setupShakeDetector() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        float sensitivity = prefManager.getShakeSensitivity();
        int requiredShakes = prefManager.getShakeCount();
        shakeDetector = new ShakeDetector(sensitivity, requiredShakes, () -> {
            String mode = prefManager.getGestureMode();
            if (mode.equals("shake")) {
                triggerEmergency();
            } else if (mode.equals("power_shake")) {
                // Check if a power button press happened within the last 2 seconds
                long now = System.currentTimeMillis();
                if (now - lastPowerPressTime < 2000) {
                    triggerEmergency();
                }
            }
        });
        sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void registerPowerButtonReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(powerButtonReceiver, filter);
    }

    private void triggerEmergency() {
        Intent intent = new Intent(this, EmergencyHandler.class);
        startService(intent);
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
        try {
            unregisterReceiver(powerButtonReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }
    }
}