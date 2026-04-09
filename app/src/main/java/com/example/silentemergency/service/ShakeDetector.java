package com.example.silentemergency.service;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

public class ShakeDetector implements SensorEventListener {
    private float threshold;
    private int requiredShakeCount;
    private Runnable onTrigger;
    private long lastShakeTime = 0;
    private int shakeCount = 0;
    private static final int TIME_LAG_MS = 500;

    public ShakeDetector(float threshold, int requiredShakeCount, Runnable onTrigger) {
        this.threshold = threshold;
        this.requiredShakeCount = requiredShakeCount;
        this.onTrigger = onTrigger;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float acceleration = (float) Math.sqrt(x*x + y*y + z*z) - 9.8f;

        if (acceleration > threshold) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > TIME_LAG_MS) {
                shakeCount++;
                lastShakeTime = now;
                if (shakeCount >= requiredShakeCount) {
                    if (onTrigger != null) onTrigger.run();
                    shakeCount = 0;
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}