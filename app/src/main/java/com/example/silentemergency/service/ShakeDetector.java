package com.example.silentemergency.service;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Handler;
import android.os.Looper;

public class ShakeDetector implements SensorEventListener {
    private float threshold;
    private int requiredShakeCount;
    private int timeWindowMs;
    private Runnable onTrigger;
    private Runnable onShakeStep; // ✅ Added for intermediate feedback

    private long firstShakeTime = 0;
    private long lastShakeTime = 0;
    private int shakeCount = 0;

    private static final int TIME_LAG_MS = 150;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ShakeDetector(float threshold, int requiredShakeCount, int timeWindowMs, Runnable onTrigger, Runnable onShakeStep) {
        this.threshold = threshold;
        this.requiredShakeCount = requiredShakeCount;
        this.timeWindowMs = timeWindowMs;
        this.onTrigger = onTrigger;
        this.onShakeStep = onShakeStep;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - 9.8f;

        if (acceleration > threshold) {
            long now = System.currentTimeMillis();

            if (shakeCount == 0 || (now - firstShakeTime) > timeWindowMs) {
                shakeCount = 1;
                firstShakeTime = now;
                lastShakeTime = now;
                if (onShakeStep != null) mainHandler.post(onShakeStep); // Tick for first shake
            }
            else if (now - lastShakeTime > TIME_LAG_MS) {
                shakeCount++;
                lastShakeTime = now;

                if (shakeCount >= requiredShakeCount) {
                    shakeCount = 0;
                    firstShakeTime = 0;
                    if (onTrigger != null) mainHandler.post(onTrigger);
                } else {
                    // ✅ Tick for intermediate shakes
                    if (onShakeStep != null) mainHandler.post(onShakeStep);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}