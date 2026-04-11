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
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import com.example.silentemergency.EmergencyHandler;
import com.example.silentemergency.utils.PrefManager;

public class EmergencyService extends Service {

    private SensorManager sensorManager;
    private ShakeDetector shakeDetector;
    private PrefManager prefManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    // Power only
    private int powerPressCount = 0;
    private long firstPressTime = 0;
    private Runnable resetPowerOnlyRunnable;

    // Power + shake
    private volatile boolean powerPressedForShake = false;
    private Runnable clearPowerShakeRunnable;
    private volatile boolean emergencyFired = false;

    private final BroadcastReceiver powerButtonReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            String mode = prefManager.getGestureMode();

            if (Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mode.equals("power_only")) {
                    handlePowerOnlyPress();
                } else if (mode.equals("power_shake")) {
                    powerPressedForShake = true;
                    emergencyFired = false;
                    long windowMs = prefManager.getPowerShakeWindow() * 1000L;

                    acquireWakeLock(windowMs + 1000);
                    vibrate(60);
                    showToast("⚡ Power pressed — shake now!");

                    if (clearPowerShakeRunnable != null)
                        mainHandler.removeCallbacks(clearPowerShakeRunnable);

                    clearPowerShakeRunnable = () -> {
                        if (powerPressedForShake) {
                            powerPressedForShake = false;
                            releaseWakeLock();
                        }
                    };
                    mainHandler.postDelayed(clearPowerShakeRunnable, windowMs);
                }
            }
        }
    };

    private void handlePowerOnlyPress() {
        long now = System.currentTimeMillis();
        long windowMs = prefManager.getPowerPressWindow() * 1000L;
        int required = prefManager.getPowerPressCount();

        if (powerPressCount == 0 || (now - firstPressTime) > windowMs) {
            powerPressCount = 1;
            firstPressTime = now;
        } else {
            powerPressCount++;
        }

        if (powerPressCount >= required) {
            powerPressCount = 0;
            firstPressTime = 0;
            if (resetPowerOnlyRunnable != null)
                mainHandler.removeCallbacks(resetPowerOnlyRunnable);
            triggerEmergency("Power button (" + required + "x)");
        } else {
            vibrate(50);
            if (resetPowerOnlyRunnable != null)
                mainHandler.removeCallbacks(resetPowerOnlyRunnable);
            resetPowerOnlyRunnable = () -> {
                powerPressCount = 0;
                firstPressTime = 0;
            };
            mainHandler.postDelayed(resetPowerOnlyRunnable, windowMs);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefManager = new PrefManager(this);
        startForeground(1, createNotification());
        setupShakeDetector();
        registerPowerButtonReceiver();
    }

    private void setupShakeDetector() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        float sensitivity = prefManager.getShakeSensitivity();
        int requiredShakes = prefManager.getShakeCount();
        int shakeWindowMs = prefManager.getShakeWindow() * 1000;

        shakeDetector = new ShakeDetector(sensitivity, requiredShakes, shakeWindowMs,
                () -> {
                    // Final trigger callback
                    String mode = prefManager.getGestureMode();
                    if (mode.equals("shake")) {
                        triggerEmergency("Shake trigger");
                    } else if (mode.equals("power_shake")) {
                        if (powerPressedForShake && !emergencyFired) {
                            emergencyFired = true;
                            powerPressedForShake = false;
                            if (clearPowerShakeRunnable != null)
                                mainHandler.removeCallbacks(clearPowerShakeRunnable);
                            releaseWakeLock();
                            triggerEmergency("Power + Shake trigger");
                        }
                    }
                },
                () -> vibrate(40) // Intermediate shake tick
        );

        sensorManager.registerListener(shakeDetector, accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void triggerEmergency(String reason) {
        vibrateHighIntensity();
        mainHandler.post(() ->
                Toast.makeText(getApplicationContext(),
                        "🚨 ALERT SENT: " + reason, Toast.LENGTH_LONG).show()
        );
        Intent intent = new Intent(this, EmergencyHandler.class);
        startService(intent);
    }

    private void vibrateHighIntensity() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                long[] pattern = {0, 400, 100, 400};
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                v.vibrate(new long[]{0, 400, 100, 400}, -1);
            }
        }
    }

    private void vibrate(long durationMs) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(durationMs);
            }
        }
    }

    private void showToast(String message) {
        mainHandler.post(() ->
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show()
        );
    }

    private void registerPowerButtonReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(powerButtonReceiver, filter);
    }

    private void acquireWakeLock(long timeout) {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (wakeLock == null)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SilentEmergency:Lock");
        if (!wakeLock.isHeld())
            wakeLock.acquire(timeout);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
    }

    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "emergency_channel", "Safe Mode Active",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, "emergency_channel")
                .setContentTitle("Silent Emergency Active")
                .setContentText("Monitoring for emergency trigger")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null)
            sensorManager.unregisterListener(shakeDetector);
        try { unregisterReceiver(powerButtonReceiver); } catch (Exception ignored) {}
        releaseWakeLock();
    }
}