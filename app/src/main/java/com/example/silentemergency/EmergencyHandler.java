package com.example.silentemergency;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.example.silentemergency.helper.CallHelper;
import com.example.silentemergency.helper.LocationHelper;
import com.example.silentemergency.helper.SmsHelper;
import com.example.silentemergency.utils.PrefManager;

import java.util.ArrayList;
import java.util.List;

public class EmergencyHandler extends Service {

    private static final String TAG                   = "EmergencyHandler";
    private static final String CHANNEL_ID            = "emergency_alert_channel";
    private static final int    NOTIF_ID              = 99;
    private static final long   SMS_FALLBACK_DELAY_MS = 6000L;
    private static final long   CALL_SAFETY_TIMEOUT_MS= 45000L;

    private TelephonyManager   telephonyManager;
    private final List<String> callQueue              = new ArrayList<>();
    private int                currentCallIndex       = 0;
    private boolean            isCallActive           = false;
    private Object             modernListener;
    private PhoneStateListener legacyListener;
    private final Handler      mainHandler            = new Handler(Looper.getMainLooper());
    private Runnable           callSafetyTimeoutRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        telephonyManager = (TelephonyManager)
                getSystemService(Context.TELEPHONY_SERVICE);
        // Must call startForeground immediately — Android 14+ throws
        // ForegroundServiceStartNotAllowedException if delayed beyond 5 seconds.
        startForeground(NOTIF_ID, buildNotification("Getting your location..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PrefManager pref = new PrefManager(this);

        // 1. Collect valid contacts
        List<String> validContacts = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String contact = pref.getEmergencyNumber(i);
            if (contact != null && !contact.isEmpty()) {
                // FIX: split(":", 2) limits to 2 parts so contact names that
                // contain a colon (e.g. "Dr. Smith: John") are handled correctly.
                String phoneNumber = contact.contains(":")
                        ? contact.split(":", 2)[1]
                        : contact;
                validContacts.add(phoneNumber.trim());
            }
        }

        if (validContacts.isEmpty()) {
            Toast.makeText(this,
                    "⚠ No emergency contacts set", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        updateNotification("Getting your current location...");

        // 2. Resolve location then dispatch — everything runs inside the callback
        // so SMS is only sent after the best available location is determined.
        new LocationHelper(this).generateEmergencyMessageAsync(messageBody -> {

            Log.d(TAG, "Message ready — dispatching to "
                    + validContacts.size() + " contact(s)");
            updateNotification("Sending emergency alerts...");

            // 3. Attempt background SMS to ALL contacts simultaneously
            boolean allSmsSent = true;
            for (String number : validContacts) {
                boolean sent = SmsHelper.sendSMS(this, number, messageBody);
                if (!sent) {
                    allSmsSent = false;
                    Log.w(TAG, "Background SMS failed for: " + number);
                }
            }

            // 4. Handle SMS result
            if (!allSmsSent) {
                updateNotification("SMS blocked — calls starting in 6 seconds...");
                Toast.makeText(this,
                        "SMS blocked. Tap Send in the SMS app. "
                                + "Calls start in 6 seconds.",
                        Toast.LENGTH_LONG).show();
                SmsHelper.openManualSms(this, validContacts.get(0), messageBody);
            } else {
                updateNotification("SMS sent — starting calls...");
                Toast.makeText(this,
                        "✅ Emergency SMS sent to "
                                + validContacts.size() + " contact(s)",
                        Toast.LENGTH_LONG).show();
            }

            // 5. Start sequential calls (or wait 6s after SMS fallback)
            if (!pref.isSmsOnly()) {
                callQueue.addAll(validContacts);
                registerCallStateListener();
                if (!allSmsSent) {
                    mainHandler.postDelayed(this::dialNextNumber, SMS_FALLBACK_DELAY_MS);
                } else {
                    dialNextNumber();
                }
            } else {
                mainHandler.postDelayed(this::stopSelf, 3000);
            }
        });

        return START_NOT_STICKY;
    }

    // ── Sequential calling ────────────────────────────────────────────────────
    private void dialNextNumber() {
        if (currentCallIndex >= callQueue.size()) {
            Log.d(TAG, "All contacts attempted. Done.");
            unregisterCallStateListener();
            stopSelf();
            return;
        }

        String number = callQueue.get(currentCallIndex);
        isCallActive  = false;

        Log.d(TAG, "Calling " + (currentCallIndex + 1)
                + "/" + callQueue.size() + ": " + number);
        updateNotification("Calling contact "
                + (currentCallIndex + 1) + " of " + callQueue.size() + "...");

        CallHelper.makeCall(this, number);

        // Safety timeout — advances to next contact if call state never fires
        cancelSafetyTimeout();
        callSafetyTimeoutRunnable = () -> {
            Log.w(TAG, "Safety timeout for " + number + " — moving to next");
            moveToNextContact();
        };
        mainHandler.postDelayed(callSafetyTimeoutRunnable, CALL_SAFETY_TIMEOUT_MS);
    }

    private void moveToNextContact() {
        cancelSafetyTimeout();
        isCallActive = false;
        currentCallIndex++;
        // Brief pause so the dialer resets cleanly between calls
        mainHandler.postDelayed(this::dialNextNumber, 2000);
    }

    private void cancelSafetyTimeout() {
        if (callSafetyTimeoutRunnable != null) {
            mainHandler.removeCallbacks(callSafetyTimeoutRunnable);
            callSafetyTimeoutRunnable = null;
        }
    }

    private void handleCallStateChanged(int state) {
        Log.d(TAG, "Call state changed: " + state);
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            isCallActive = true;
        } else if (state == TelephonyManager.CALL_STATE_IDLE && isCallActive) {
            Log.d(TAG, "Call ended — moving to next contact");
            moveToNextContact();
        }
    }

    // ── Telephony listener (version-safe) ─────────────────────────────────────
    private void registerCallStateListener() {
        if (telephonyManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernListener = new MyTelephonyCallback();
            telephonyManager.registerTelephonyCallback(
                    getMainExecutor(), (TelephonyCallback) modernListener);
        } else {
            legacyListener = new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String phoneNumber) {
                    handleCallStateChanged(state);
                }
            };
            telephonyManager.listen(
                    legacyListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    private void unregisterCallStateListener() {
        if (telephonyManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (modernListener != null)
                    telephonyManager.unregisterTelephonyCallback(
                            (TelephonyCallback) modernListener);
            } else {
                if (legacyListener != null)
                    telephonyManager.listen(
                            legacyListener, PhoneStateListener.LISTEN_NONE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private class MyTelephonyCallback extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            handleCallStateChanged(state);
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────
    private Notification buildNotification(String text) {
        createNotificationChannel();
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🚨 Emergency Alert Active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Emergency Alert",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Shows status while sending emergency alerts");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelSafetyTimeout();
        unregisterCallStateListener();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}