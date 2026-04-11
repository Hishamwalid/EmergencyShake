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

    private static final String TAG = "EmergencyHandler";
    private static final String CHANNEL_ID = "emergency_alert_channel";
    private static final int NOTIF_ID = 99;

    // Wait after opening SMS app before starting calls
    private static final long SMS_FALLBACK_DELAY_MS = 6000;

    // Per-call safety timeout — move on if phone state never fires
    private static final long CALL_SAFETY_TIMEOUT_MS = 45000;

    private TelephonyManager telephonyManager;
    private final List<String> callQueue = new ArrayList<>();
    private int currentCallIndex = 0;
    private boolean isCallActive = false;

    private Object modernListener;
    private PhoneStateListener legacyListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable callSafetyTimeoutRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        // ✅ CRITICAL FIX: Must be foreground service so Android doesn't kill us
        // during the 6-second SMS wait or multi-contact call sequence.
        startForeground(NOTIF_ID, buildNotification("Emergency alert in progress..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PrefManager pref = new PrefManager(this);
        List<String> validContacts = new ArrayList<>();

        // 1. Collect valid phone numbers
        for (int i = 1; i <= 3; i++) {
            String contact = pref.getEmergencyNumber(i);
            if (contact != null && !contact.isEmpty()) {
                String phoneNumber = contact.contains(":")
                        ? contact.split(":")[1]
                        : contact;
                validContacts.add(phoneNumber.trim());
            }
        }

        if (validContacts.isEmpty()) {
            Toast.makeText(this, "⚠ No emergency contacts set", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        // 2. Generate emergency message once
        String messageBody = new LocationHelper(this).generateEmergencyMessage();

        // 3. Try background SMS for ALL contacts
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
            // Background SMS blocked — open SMS app with FIRST contact pre-filled
            // User taps Send manually. We wait 6 seconds then call everyone.
            updateNotification("SMS blocked — calls starting in 6 seconds...");
            Toast.makeText(this,
                    "SMS blocked. Tap Send in the SMS app. Calls start in 6 seconds.",
                    Toast.LENGTH_LONG).show();
            SmsHelper.openManualSms(this, validContacts.get(0), messageBody);
        } else {
            updateNotification("SMS sent — starting calls...");
            Toast.makeText(this,
                    "✅ Emergency SMS sent to " + validContacts.size() + " contact(s)",
                    Toast.LENGTH_LONG).show();
        }

        // 5. Start sequential calling (or wait 6s after SMS fallback)
        if (!pref.isSmsOnly()) {
            callQueue.addAll(validContacts);
            registerCallStateListener();

            if (!allSmsSent) {
                // ✅ postDelayed works because we are now a foreground service
                mainHandler.postDelayed(this::dialNextNumber, SMS_FALLBACK_DELAY_MS);
            } else {
                dialNextNumber();
            }
        } else {
            // SMS-only mode — we are done
            mainHandler.postDelayed(this::stopSelf, 3000);
        }

        return START_NOT_STICKY;
    }

    // ─── SEQUENTIAL CALLING ──────────────────────────────────────────────

    private void dialNextNumber() {
        if (currentCallIndex >= callQueue.size()) {
            Log.d(TAG, "All contacts called. Done.");
            unregisterCallStateListener();
            stopSelf();
            return;
        }

        String number = callQueue.get(currentCallIndex);
        isCallActive = false;

        Log.d(TAG, "Calling " + (currentCallIndex + 1) + "/" + callQueue.size() + ": " + number);
        updateNotification("Calling contact " + (currentCallIndex + 1)
                + " of " + callQueue.size() + "...");

        CallHelper.makeCall(this, number);

        // Safety timeout — if phone state never fires (no answer, voicemail, etc.)
        // move to next contact after 45 seconds
        cancelSafetyTimeout();
        callSafetyTimeoutRunnable = () -> {
            Log.w(TAG, "Safety timeout reached for: " + number + " — moving to next");
            moveToNextContact();
        };
        mainHandler.postDelayed(callSafetyTimeoutRunnable, CALL_SAFETY_TIMEOUT_MS);
    }

    private void moveToNextContact() {
        cancelSafetyTimeout();
        isCallActive = false;
        currentCallIndex++;
        // Brief pause so phone resets between calls
        mainHandler.postDelayed(this::dialNextNumber, 2000);
    }

    private void cancelSafetyTimeout() {
        if (callSafetyTimeoutRunnable != null) {
            mainHandler.removeCallbacks(callSafetyTimeoutRunnable);
            callSafetyTimeoutRunnable = null;
        }
    }

    /**
     * Call flow:
     *   Dialing / Answered → OFFHOOK (stays until call ends) → IDLE → next
     *   Rejected / Busy    → OFFHOOK briefly → IDLE quickly → next
     *   No answer          → OFFHOOK → IDLE after carrier timeout → next
     *                        (45s safety timeout also catches this)
     */
    private void handleCallStateChanged(int state) {
        Log.d(TAG, "Call state: " + state);
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            isCallActive = true;
        } else if (state == TelephonyManager.CALL_STATE_IDLE && isCallActive) {
            Log.d(TAG, "Call ended — moving to next contact");
            moveToNextContact();
        }
    }

    // ─── PHONE STATE LISTENER ────────────────────────────────────────────

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
            telephonyManager.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE);
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
                    telephonyManager.listen(legacyListener, PhoneStateListener.LISTEN_NONE);
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

    // ─── NOTIFICATION HELPERS ────────────────────────────────────────────

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
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification(text));
        }
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

    // ─── LIFECYCLE ───────────────────────────────────────────────────────

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelSafetyTimeout();
        unregisterCallStateListener();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}