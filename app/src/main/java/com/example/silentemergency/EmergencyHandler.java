package com.example.silentemergency;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import com.example.silentemergency.helper.CallHelper;
import com.example.silentemergency.helper.LocationHelper;
import com.example.silentemergency.helper.SmsHelper;
import com.example.silentemergency.utils.PrefManager;
import java.util.ArrayList;
import java.util.List;

public class EmergencyHandler extends Service {

    private TelephonyManager telephonyManager;
    private List<String> callQueue = new ArrayList<>();
    private int currentCallIndex = 0;
    private boolean isCallActive = false;
    private Object modernListener;
    private PhoneStateListener legacyListener;

    @Override
    public void onCreate() {
        super.onCreate();
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PrefManager pref = new PrefManager(this);
        List<String> validContacts = new ArrayList<>();
        List<String> failedSmsContacts = new ArrayList<>();

        // 1. Collect Contacts
        for (int i = 1; i <= 3; i++) {
            String contact = pref.getEmergencyNumber(i);
            if (contact != null && !contact.isEmpty()) {
                String phoneNumber = contact.contains(":") ? contact.split(":")[1] : contact;
                validContacts.add(phoneNumber.trim());
            }
        }

        if (validContacts.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // 2. Generate the Message
        LocationHelper lh = new LocationHelper(this);
        String messageBody = lh.generateEmergencyMessage();

        // 3. PHASE 1: Try Silent Background SMS
        for (String number : validContacts) {
            boolean success = SmsHelper.sendSMS(this, number, messageBody);
            if (!success) {
                failedSmsContacts.add(number);
            }
        }

        // 4. PHASE 2: Handle Manual Fallback or Start Calls
        if (!failedSmsContacts.isEmpty()) {
            triggerMultiSmsFallback(failedSmsContacts, messageBody);

            if (!pref.isSmsOnly()) {
                callQueue.addAll(validContacts);
                registerCallStateListener();
                // 8 second delay to allow you to hit "Send" in the SMS app
                new Handler(Looper.getMainLooper()).postDelayed(this::dialNextNumber, 8000);
            } else {
                stopSelf();
            }
        } else {
            // Background SMS succeeded
            if (!pref.isSmsOnly()) {
                callQueue.addAll(validContacts);
                registerCallStateListener();
                dialNextNumber();
            } else {
                stopSelf();
            }
        }

        return START_NOT_STICKY;
    }

    /**
     * Logic specifically designed to prevent Google Messages from creating a group.
     * Uses ACTION_SEND with text/plain which forces individual recipient handling.
     */
    private void triggerMultiSmsFallback(List<String> numbers, String message) {
        try {
            String joinedNumbers = TextUtils.join(",", numbers);

            // Use ACTION_SEND instead of ACTION_SENDTO to avoid the group-chat URI logic
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, message);

            // "address" is the key Google Messages uses to identify individual recipients
            intent.putExtra("address", joinedNumbers);

            // Backup for older Android versions
            intent.putExtra("com.android.extra.RECIPIENTS", numbers.toArray(new String[0]));

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Verify if there's an SMS app to handle this
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                // Last ditch effort if ACTION_SEND fails
                Intent lastDitch = new Intent(Intent.ACTION_VIEW);
                lastDitch.setData(Uri.parse("smsto:" + joinedNumbers));
                lastDitch.putExtra("sms_body", message);
                lastDitch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(lastDitch);
            }

            Toast.makeText(this, "Automatic SMS failed. Tap SEND to alert contacts.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- SEQUENTIAL CALL LOGIC ---

    private void dialNextNumber() {
        if (currentCallIndex < callQueue.size()) {
            String number = callQueue.get(currentCallIndex);
            isCallActive = false;
            CallHelper.makeCall(this, number);
        } else {
            unregisterCallStateListener();
            stopSelf();
        }
    }

    private void handleCallStateChanged(int state) {
        if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            isCallActive = true;
        } else if (state == TelephonyManager.CALL_STATE_IDLE && isCallActive) {
            isCallActive = false;
            currentCallIndex++;
            // Pause for 2.5 seconds between calls
            new Handler(Looper.getMainLooper()).postDelayed(this::dialNextNumber, 2500);
        }
    }

    private void registerCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernListener = new MyTelephonyCallback();
            telephonyManager.registerTelephonyCallback(getMainExecutor(), (TelephonyCallback) modernListener);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (modernListener != null) telephonyManager.unregisterTelephonyCallback((TelephonyCallback) modernListener);
        } else {
            if (legacyListener != null) telephonyManager.listen(legacyListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private class MyTelephonyCallback extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override public void onCallStateChanged(int state) { handleCallStateChanged(state); }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}