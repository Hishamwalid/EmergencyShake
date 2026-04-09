package com.example.silentemergency;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.silentemergency.service.EmergencyService;
import com.example.silentemergency.utils.PrefManager;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private PrefManager prefManager;
    private LinearLayout contactsContainer;
    private Switch swDarkMode;
    private Button btnToggleProtection;
    private TextView tvTimer;
    private List<String> contacts = new ArrayList<>();

    private boolean isActive = false;
    private long startTime = 0;
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;

    private static final int PICK_CONTACT_REQUEST = 1;
    private int pendingEditIndex = -1; // used when editing a contact

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefManager = new PrefManager(this);
        if (prefManager.isDarkMode()) setTheme(R.style.AppTheme_Dark);
        else setTheme(R.style.AppTheme_Light);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Emergency Settings");

        findViewById(R.id.btnShakeSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, ShakeConfigActivity.class));
        });

        contactsContainer = findViewById(R.id.contactsContainer);
        swDarkMode = findViewById(R.id.swDarkMode);
        btnToggleProtection = findViewById(R.id.btnToggleProtection);
        tvTimer = findViewById(R.id.tvTimer);
        findViewById(R.id.btnAddContact).setOnClickListener(v -> openContactPickerForAdd());

        loadContacts();

        swDarkMode.setChecked(prefManager.isDarkMode());
        swDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefManager.setDarkMode(isChecked);
            recreate();
        });

        // Check if service is already running
        isActive = isServiceRunning();
        if (isActive) {
            startTime = prefManager.getPrefs().getLong("protection_start_time", System.currentTimeMillis());
            startTimer();
            btnToggleProtection.setText("Deactivate Protection");
            btnToggleProtection.setBackgroundResource(R.drawable.btn_monochrome_deactivate);
        } else {
            tvTimer.setText("Protection inactive");
        }

        btnToggleProtection.setOnClickListener(v -> {
            if (isActive) {
                stopProtection();
            } else {
                startProtection();
            }
        });
    }

    private boolean isServiceRunning() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (EmergencyService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void startProtection() {
        Intent intent = new Intent(this, EmergencyService.class);
        startService(intent);
        isActive = true;
        startTime = System.currentTimeMillis();
        prefManager.getPrefs().edit().putLong("protection_start_time", startTime).apply();
        startTimer();
        btnToggleProtection.setText("Deactivate Protection");
        btnToggleProtection.setBackgroundResource(R.drawable.btn_monochrome_deactivate);
        Toast.makeText(this, "Protection activated", Toast.LENGTH_SHORT).show();
    }

    private void stopProtection() {
        Intent intent = new Intent(this, EmergencyService.class);
        stopService(intent);
        isActive = false;
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        tvTimer.setText("Protection inactive");
        btnToggleProtection.setText("Activate Protection");
        btnToggleProtection.setBackgroundResource(R.drawable.btn_monochrome_primary);
        Toast.makeText(this, "Protection deactivated", Toast.LENGTH_SHORT).show();
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - startTime;
                long seconds = (elapsed / 1000) % 60;
                long minutes = (elapsed / (1000 * 60)) % 60;
                long hours = (elapsed / (1000 * 60 * 60));
                String time = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                tvTimer.setText("Protection active for: " + time);
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    // --- Contact management with contact picker ---
    private void openContactPickerForAdd() {
        if (contacts.size() >= 3) {
            Toast.makeText(this, "Maximum 3 contacts allowed", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingEditIndex = -1;
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT_REQUEST);
    }

    private void openContactPickerForEdit(int index) {
        pendingEditIndex = index;
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri contactUri = data.getData();
            String phoneNumber = getPhoneNumberFromUri(contactUri);
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                if (pendingEditIndex == -1) {
                    // Add new contact
                    contacts.add(phoneNumber);
                } else {
                    // Edit existing contact
                    contacts.set(pendingEditIndex, phoneNumber);
                    pendingEditIndex = -1;
                }
                saveContacts();
                refreshContactList();
            } else {
                Toast.makeText(this, "No phone number found for this contact", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getPhoneNumberFromUri(Uri uri) {
        String phoneNumber = null;
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
            Cursor phoneCursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    new String[]{contactId},
                    null);
            if (phoneCursor != null && phoneCursor.moveToFirst()) {
                phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                phoneCursor.close();
            }
            cursor.close();
        }
        // Remove spaces, dashes, parentheses
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.replaceAll("[\\s\\-()]", "");
        }
        return phoneNumber;
    }

    // --- Contact list UI ---
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