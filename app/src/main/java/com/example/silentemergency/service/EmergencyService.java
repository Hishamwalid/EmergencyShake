package com.example.silentemergency.service;

import android.annotation.SuppressLint;
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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.example.silentemergency.EmergencyHandler;
import com.example.silentemergency.utils.PrefManager;

public class EmergencyService extends Service {

    private static final String TAG = "EmergencyService";

    private SensorManager    sensorManager;
    private ShakeDetector    shakeDetector;
    private PrefManager      prefManager;
    private LocationManager  locationManager;
    private LocationListener locationListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    // ── Trigger cooldown ──────────────────────────────────────────────────────
    // Prevents multiple SMS batches firing from a single emergency event.
    // Once triggered, all further gestures are ignored for COOLDOWN_MS.
    private static final long COOLDOWN_MS      = 60_000L; // 60 seconds
    private volatile boolean  triggerOnCooldown = false;
    private Runnable          resetCooldownRunnable;

    // ── Power only ────────────────────────────────────────────────────────────
    private int      powerPressCount       = 0;
    private long     firstPressTime        = 0;
    private Runnable resetPowerOnlyRunnable;

    // ── Power + shake ─────────────────────────────────────────────────────────
    private volatile boolean powerPressedForShake = false;
    private Runnable         clearPowerShakeRunnable;
    private volatile boolean emergencyFired        = false;

    // ── Power button broadcast receiver ───────────────────────────────────────
    private final BroadcastReceiver powerButtonReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            String mode = prefManager.getGestureMode();
            if (Intent.ACTION_SCREEN_OFF.equals(action) ||
                    Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mode.equals("power_only")) {
                    handlePowerOnlyPress();
                } else if (mode.equals("power_shake")) {
                    powerPressedForShake = true;
                    emergencyFired       = false;
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
        long now      = System.currentTimeMillis();
        long windowMs = prefManager.getPowerPressWindow() * 1000L;
        int  required = prefManager.getPowerPressCount();

        if (powerPressCount == 0 || (now - firstPressTime) > windowMs) {
            powerPressCount = 1;
            firstPressTime  = now;
        } else {
            powerPressCount++;
        }

        if (powerPressCount >= required) {
            powerPressCount = 0;
            firstPressTime  = 0;
            if (resetPowerOnlyRunnable != null)
                mainHandler.removeCallbacks(resetPowerOnlyRunnable);
            triggerEmergency("Power button (" + required + "x)");
        } else {
            vibrate(50);
            if (resetPowerOnlyRunnable != null)
                mainHandler.removeCallbacks(resetPowerOnlyRunnable);
            resetPowerOnlyRunnable = () -> {
                powerPressCount = 0;
                firstPressTime  = 0;
            };
            mainHandler.postDelayed(resetPowerOnlyRunnable, windowMs);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onCreate() {
        super.onCreate();
        prefManager     = new PrefManager(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        startForeground(1, createNotification());
        setupShakeDetector();
        registerPowerButtonReceiver();
        startLiveLocationTracking();
    }

    // ── Live GPS tracking ─────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    private void startLiveLocationTracking() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                prefManager.setLiveLocation(
                        location.getLatitude(), location.getLongitude());
                Log.d(TAG, "Live location updated: "
                        + location.getLatitude() + ", " + location.getLongitude());
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };

        boolean registered = false;

        try {
            if (locationManager != null &&
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        30_000L,
                        10f,
                        locationListener,
                        Looper.getMainLooper());
                registered = true;
                Log.d(TAG, "Live GPS tracking started (GPS provider)");
            }
        } catch (Exception e) {
            Log.e(TAG, "GPS provider failed: " + e.getMessage());
        }

        try {
            if (locationManager != null &&
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        30_000L,
                        10f,
                        locationListener,
                        Looper.getMainLooper());
                registered = true;
                Log.d(TAG, "Live GPS tracking started (Network provider)");
            }
        } catch (Exception e) {
            Log.e(TAG, "Network provider failed: " + e.getMessage());
        }

        if (!registered) {
            Log.w(TAG, "No location provider available for live tracking");
        }
    }

    private void stopLiveLocationTracking() {
        try {
            if (locationManager != null && locationListener != null) {
                locationManager.removeUpdates(locationListener);
                Log.d(TAG, "Live GPS tracking stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping location tracking: " + e.getMessage());
        }
        prefManager.clearLiveLocation();
    }

    // ── Shake detector setup ──────────────────────────────────────────────────
    private void setupShakeDetector() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        float sensitivity  = prefManager.getShakeSensitivity();
        int requiredShakes = prefManager.getShakeCount();
        int shakeWindowMs  = prefManager.getShakeWindow() * 1000;

        shakeDetector = new ShakeDetector(
                sensitivity,
                requiredShakes,
                shakeWindowMs,
                () -> {
                    String mode = prefManager.getGestureMode();
                    if (mode.equals("shake")) {
                        triggerEmergency("Shake trigger");
                    } else if (mode.equals("power_shake")) {
                        if (powerPressedForShake && !emergencyFired) {
                            emergencyFired       = true;
                            powerPressedForShake = false;
                            if (clearPowerShakeRunnable != null)
                                mainHandler.removeCallbacks(clearPowerShakeRunnable);
                            releaseWakeLock();
                            triggerEmergency("Power + Shake trigger");
                        }
                    }
                },
                () -> vibrate(40)
        );

        sensorManager.registerListener(
                shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    // ── Emergency trigger ─────────────────────────────────────────────────────
    private void triggerEmergency(String reason) {
        // If we already fired within the last 60 seconds, ignore completely.
        // This prevents the shake detector resetting and firing again while
        // the user is still physically shaking during the same emergency event.
        if (triggerOnCooldown) {
            Log.d(TAG, "Trigger ignored — cooldown active (" + reason + ")");
            return;
        }

        // Arm the cooldown immediately before doing anything else
        triggerOnCooldown = true;
        if (resetCooldownRunnable != null)
            mainHandler.removeCallbacks(resetCooldownRunnable);
        resetCooldownRunnable = () -> {
            triggerOnCooldown = false;
            Log.d(TAG, "Trigger cooldown reset — ready for next emergency");
        };
        mainHandler.postDelayed(resetCooldownRunnable, COOLDOWN_MS);

        // Proceed with the actual emergency dispatch
        vibrateHighIntensity();
        mainHandler.post(() ->
                Toast.makeText(getApplicationContext(),
                        "🚨 ALERT SENT: " + reason, Toast.LENGTH_LONG).show()
        );
        startService(new Intent(this, EmergencyHandler.class));
    }

    // ── Vibration ─────────────────────────────────────────────────────────────
    private void vibrateHighIntensity() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 400, 100, 400}, -1));
            else
                v.vibrate(new long[]{0, 400, 100, 400}, -1);
        }
    }

    private void vibrate(long durationMs) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(
                        durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            else
                v.vibrate(durationMs);
        }
    }

    private void showToast(String message) {
        mainHandler.post(() ->
                Toast.makeText(getApplicationContext(),
                        message, Toast.LENGTH_SHORT).show()
        );
    }

    // ── Power button receiver ─────────────────────────────────────────────────
    private void registerPowerButtonReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(powerButtonReceiver, filter);
    }

    // ── Wake lock ─────────────────────────────────────────────────────────────
    private void acquireWakeLock(long timeout) {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (wakeLock == null)
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "SilentEmergency:Lock");
        if (!wakeLock.isHeld())
            wakeLock.acquire(timeout);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private Notification createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "emergency_channel",
                    "Safe Mode Active",
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
        stopLiveLocationTracking();
        if (sensorManager != null)
            sensorManager.unregisterListener(shakeDetector);
        try { unregisterReceiver(powerButtonReceiver); } catch (Exception ignored) {}
        releaseWakeLock();
        if (resetCooldownRunnable != null)
            mainHandler.removeCallbacks(resetCooldownRunnable);
        mainHandler.removeCallbacksAndMessages(null);
    }
}