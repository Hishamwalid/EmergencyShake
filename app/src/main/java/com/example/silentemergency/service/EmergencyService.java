package com.example.silentemergency.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class EmergencyService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
