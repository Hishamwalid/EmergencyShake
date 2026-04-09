package com.example.silentemergency;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.silentemergency.service.EmergencyService;
import com.example.silentemergency.utils.PrefManager;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private PrefManager prefManager;
    private LinearLayout contactsContainer;
    private Button btnToggleProtection;
    private TextView tvTimer;
    private List<String> contacts = new ArrayList<>();

    private boolean isActive = false;
    private long startTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private int pendingEditIndex = -1;

    private final ActivityResultLauncher<Intent> contactPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    handleContactResult(contactUri);
                }
            }
    );

    private static final String KEY_IS_PROTECTION_ACTIVE = "is_protection_active";
    private static final String KEY_PROTECTION_START_TIME = "protection_start_time";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefManager = new PrefManager(this);
        if (prefManager.isDarkMode()) setTheme(R.style.AppTheme_Dark);
        else setTheme(R.style.AppTheme_Light);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Emergency Settings");

        findViewById(R.id.btnShakeSettings).setOnClickListener(v ->
                startActivity(new Intent(this, ShakeConfigActivity.class)));

        contactsContainer = findViewById(R.id.contactsContainer);
        btnToggleProtection = findViewById(R.id.btnToggleProtection);
        tvTimer = findViewById(R.id.tvTimer);
        findViewById(R.id.btnAddContact).setOnClickListener(v -> openContactPickerForAdd());

        loadContacts();

        isActive = prefManager.getPrefs().getBoolean(KEY_IS_PROTECTION_ACTIVE, false);
        if (isActive) {
            startTime = prefManager.getPrefs().getLong(KEY_PROTECTION_START_TIME, System.currentTimeMillis());
            startTimer();
            btnToggleProtection.setText("Deactivate Protection");
            btnToggleProtection.setBackgroundResource(R.drawable.btn_monochrome_deactivate);
        } else {
            tvTimer.setText("Protection inactive");
            btnToggleProtection.setText("Activate Protection");
            btnToggleProtection.setBackgroundResource(R.drawable.btn_monochrome_primary);
        }

        btnToggleProtection.setOnClickListener(v -> {
            if (isActive) stopProtection();
            else startProtection();
        });
    }

    private void startProtection() {
        try {
            Intent intent = new Intent(this, EmergencyService.class);
            ContextCompat.startForegroundService(this, intent);
            isActive = true;
            startTime = System.currentTimeMillis();
            prefManager.getPrefs().edit()
                    .putBoolean(KEY_IS_PROTECTION_ACTIVE, true)
                    .putLong(KEY_PROTECTION_START_TIME, startTime)
                    .apply();
            startTimer();
            btnToggleProtection.setText("Deactivate Protection");
            btnToggleProtection.setBackgroundResource(R.drawable.btn_monochrome_deactivate);
            Toast.makeText(this, "Protection activated", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start protection", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopProtection() {
        try {
            // Stop the service directly – Android 16 is sensitive, but this is fine
            Intent intent = new Intent(this, EmergencyService.class);
            stopService(intent);
            isActive = false;
            prefManager.getPrefs().edit().putBoolean(KEY_IS_PROTECTION_ACTIVE, false).apply();
            if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
            tvTimer.setText("Protection inactive");
            btnToggleProtection.setText("Activate Protection");
            btnToggleProtection.setBackgroundResource(R.drawable.btn_monochrome_primary);
            Toast.makeText(this, "Protection deactivated", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error stopping protection", Toast.LENGTH_SHORT).show();
        }
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                long seconds = (elapsed / 1000) % 60;
                long minutes = (elapsed / (1000 * 60)) % 60;
                long hours = (elapsed / (1000 * 60 * 60));
                tvTimer.setText(String.format("Protection active for: %02d:%02d:%02d", hours, minutes, seconds));
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    // --- Contact picker helpers (unchanged, but included for completeness) ---
    private void openContactPickerForAdd() {
        if (contacts.size() >= 3) {
            Toast.makeText(this, "Maximum 3 contacts allowed", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!checkContactPermission()) return;
        pendingEditIndex = -1;
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void openContactPickerForEdit(int index) {
        if (!checkContactPermission()) return;
        pendingEditIndex = index;
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private boolean checkContactPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 100);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingEditIndex == -1) openContactPickerForAdd();
            else openContactPickerForEdit(pendingEditIndex);
        } else if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
        } else if (requestCode == 101) {
            Toast.makeText(this, "Notification permission denied. Service may not start.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleContactResult(Uri contactUri) {
        String phoneNumber = getPhoneNumberFromUri(contactUri);
        if (phoneNumber != null && !phoneNumber.isEmpty()) {
            if (pendingEditIndex == -1) {
                contacts.add(phoneNumber);
            } else {
                contacts.set(pendingEditIndex, phoneNumber);
                pendingEditIndex = -1;
            }
            saveContacts();
            refreshContactList();
        } else {
            Toast.makeText(this, "No phone number found", Toast.LENGTH_SHORT).show();
        }
    }

    private String getPhoneNumberFromUri(Uri uri) {
        String phoneNumber = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                try (Cursor phoneCursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{contactId},
                        null)) {
                    if (phoneCursor != null && phoneCursor.moveToFirst()) {
                        phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.replaceAll("[\\s\\-()]", "");
        }
        return phoneNumber;
    }

    private void loadContacts() {
        contacts.clear();
        for (int i = 1; i <= 3; i++) {
            String contact = prefManager.getEmergencyNumber(i);
            if (contact != null && !contact.isEmpty()) {
                contacts.add(contact);
            }
        }
        refreshContactList();
    }

    private void refreshContactList() {
        contactsContainer.removeAllViews();
        for (int i = 0; i < contacts.size(); i++) {
            String contact = contacts.get(i);
            View itemView = getLayoutInflater().inflate(R.layout.item_contact, null);
            TextView tvContact = itemView.findViewById(R.id.tvContact);
            Button btnEdit = itemView.findViewById(R.id.btnEdit);
            Button btnRemove = itemView.findViewById(R.id.btnRemove);
            tvContact.setText(contact);
            final int index = i;
            btnEdit.setOnClickListener(v -> openContactPickerForEdit(index));
            btnRemove.setOnClickListener(v -> {
                contacts.remove(index);
                saveContacts();
                refreshContactList();
            });
            contactsContainer.addView(itemView);
        }
    }

    private void saveContacts() {
        for (int i = 1; i <= 3; i++) {
            prefManager.setEmergencyNumber(i, "");
        }
        for (int i = 0; i < contacts.size(); i++) {
            prefManager.setEmergencyNumber(i + 1, contacts.get(i));
        }
    }
}