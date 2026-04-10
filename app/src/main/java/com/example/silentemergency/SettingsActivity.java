package com.example.silentemergency;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.silentemergency.service.EmergencyService;
import com.example.silentemergency.utils.PrefManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private PrefManager prefManager;
    private LinearLayout contactsContainer;
    private Button btnToggleProtection;
    private TextView tvTimer, tvStartingPoint, tvDestination;
    private List<String> contacts = new ArrayList<>();

    private boolean isActive = false;
    private long startTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    private int pendingEditIndex = -1;

    private static final int COLOR_RED = 0xFFFF4444;
    private static final int COLOR_GREEN = 0xFF4CAF50;

    private static final int REQUEST_CURRENT_LOCATION = 200;

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

        // Notification permission for Android 13+
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
        tvStartingPoint = findViewById(R.id.tvStartingPoint);
        tvDestination = findViewById(R.id.tvDestination);
        findViewById(R.id.btnAddContact).setOnClickListener(v -> showAddContactOptions());
        findViewById(R.id.btnSetCurrentLocation).setOnClickListener(v -> getCurrentLocation());
        findViewById(R.id.btnSetDestination).setOnClickListener(v -> openDestinationDialog());

        loadContacts();
        updateStartingPointDisplay();
        updateDestinationDisplay();

        // Restore protection state
        isActive = prefManager.getPrefs().getBoolean(KEY_IS_PROTECTION_ACTIVE, false);
        if (isActive) {
            startTime = prefManager.getPrefs().getLong(KEY_PROTECTION_START_TIME, System.currentTimeMillis());
            startTimer();
            btnToggleProtection.setText("DEACTIVATE");
            btnToggleProtection.setBackgroundResource(R.drawable.btn_round_toggle);
            tvTimer.setTextColor(COLOR_GREEN);
        } else {
            tvTimer.setText("Protection inactive");
            tvTimer.setTextColor(COLOR_RED);
            btnToggleProtection.setText("ACTIVATE");
            btnToggleProtection.setBackgroundResource(R.drawable.btn_round_toggle);
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
            btnToggleProtection.setText("DEACTIVATE");
            btnToggleProtection.setBackgroundResource(R.drawable.btn_round_toggle);
            tvTimer.setTextColor(COLOR_GREEN);
            Toast.makeText(this, "Protection activated", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to start protection", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopProtection() {
        try {
            Intent intent = new Intent(this, EmergencyService.class);
            stopService(intent);
            isActive = false;
            prefManager.getPrefs().edit().putBoolean(KEY_IS_PROTECTION_ACTIVE, false).apply();
            if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
            tvTimer.setText("Protection inactive");
            tvTimer.setTextColor(COLOR_RED);
            btnToggleProtection.setText("ACTIVATE");
            btnToggleProtection.setBackgroundResource(R.drawable.btn_round_toggle);
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

    // --- Starting point & destination helpers ---
    private void updateStartingPointDisplay() {
        String start = prefManager.getStartingPoint();
        if (start.isEmpty()) {
            tvStartingPoint.setText("Starting point: not set");
        } else {
            tvStartingPoint.setText("Starting point: " + start);
        }
    }

    private void updateDestinationDisplay() {
        String dest = prefManager.getDestination();
        if (dest.isEmpty()) {
            tvDestination.setText("Destination: not set");
        } else {
            tvDestination.setText("Destination: " + dest);
        }
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CURRENT_LOCATION);
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (location == null) {
            Toast.makeText(this, "Unable to get current location. Please enable GPS.", Toast.LENGTH_LONG).show();
            return;
        }
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        String address = getAddressFromLatLng(lat, lon);
        if (address != null) {
            prefManager.setStartingPoint(address);
            updateStartingPointDisplay();
            Toast.makeText(this, "Starting point saved", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Could not get address. Saved coordinates.", Toast.LENGTH_SHORT).show();
            prefManager.setStartingPoint(lat + "," + lon);
            updateStartingPointDisplay();
        }
    }

    private void openDestinationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Destination");

        final EditText input = new EditText(this);
        input.setHint("Enter destination address");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String address = input.getText().toString().trim();
            if (!address.isEmpty()) {
                prefManager.setDestination(address);
                updateDestinationDisplay();
                Toast.makeText(this, "Destination saved", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Destination not saved (empty)", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private String getAddressFromLatLng(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String addressLine = addr.getAddressLine(0);
                if (!TextUtils.isEmpty(addressLine)) {
                    return addressLine;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingEditIndex == -1) openContactPicker();
            else editContactAtIndex(pendingEditIndex);
        } else if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
        } else if (requestCode == 101) {
            Toast.makeText(this, "Notification permission denied. Service may not start.", Toast.LENGTH_SHORT).show();
        } else if (requestCode == REQUEST_CURRENT_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        }
    }

    // --- Contact management (unchanged) ---
    private void showAddContactOptions() {
        if (contacts.size() >= 3) {
            Toast.makeText(this, "Maximum 3 contacts allowed", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] options = {"Import from Contacts", "Enter Number Manually"};
        new AlertDialog.Builder(this)
                .setTitle("Add Contact")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openContactPicker();
                    else showManualEntryDialog();
                })
                .show();
    }

    private void openContactPicker() {
        if (!checkContactPermission()) return;
        pendingEditIndex = -1;
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void showManualEntryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        final EditText inputName = new EditText(this);
        inputName.setHint("Name");
        layout.addView(inputName);

        final EditText inputNumber = new EditText(this);
        inputNumber.setHint("Phone Number");
        inputNumber.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        layout.addView(inputNumber);

        new AlertDialog.Builder(this)
                .setTitle("Manual Entry")
                .setView(layout)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    String phone = inputNumber.getText().toString().trim();
                    if (!phone.isEmpty()) {
                        addContactToList(name.isEmpty() ? "Emergency" : name, phone);
                    } else {
                        Toast.makeText(this, "Phone number required", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleContactResult(Uri contactUri) {
        String name = "Unknown";
        String number = "";
        try (Cursor cursor = getContentResolver().query(contactUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                try (Cursor pCursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{id}, null)) {
                    if (pCursor != null && pCursor.moveToFirst()) {
                        number = pCursor.getString(pCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!number.isEmpty()) {
            addContactToList(name, number.replaceAll("[\\s\\-()]", ""));
        } else {
            Toast.makeText(this, "No phone number found for this contact", Toast.LENGTH_SHORT).show();
        }
    }

    private void addContactToList(String name, String number) {
        contacts.add(name + ":" + number);
        saveContacts();
        refreshContactList();
    }

    private void editContactAtIndex(int index) {
        String data = contacts.get(index);
        String[] parts = data.split(":");
        String oldName = parts[0];
        String oldNumber = parts.length > 1 ? parts[1] : "";

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        final EditText inputName = new EditText(this);
        inputName.setText(oldName);
        inputName.setHint("Name");
        layout.addView(inputName);

        final EditText inputNumber = new EditText(this);
        inputNumber.setText(oldNumber);
        inputNumber.setHint("Phone Number");
        inputNumber.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        layout.addView(inputNumber);

        new AlertDialog.Builder(this)
                .setTitle("Edit Contact")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = inputName.getText().toString().trim();
                    String newNumber = inputNumber.getText().toString().trim();
                    if (!newNumber.isEmpty()) {
                        contacts.set(index, newName + ":" + newNumber);
                        saveContacts();
                        refreshContactList();
                    } else {
                        Toast.makeText(this, "Phone number required", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean checkContactPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 100);
            return false;
        }
        return true;
    }

    private void loadContacts() {
        contacts.clear();
        for (int i = 1; i <= 3; i++) {
            String data = prefManager.getEmergencyNumber(i);
            if (data != null && !data.isEmpty()) {
                contacts.add(data);
            }
        }
        refreshContactList();
    }

    private void refreshContactList() {
        contactsContainer.removeAllViews();
        for (int i = 0; i < contacts.size(); i++) {
            String data = contacts.get(i);
            String name = data.contains(":") ? data.split(":")[0] : "Contact";
            String number = data.contains(":") ? data.split(":")[1] : data;

            View itemView = getLayoutInflater().inflate(R.layout.item_contact, null);
            TextView tvContact = itemView.findViewById(R.id.tvContact);
            tvContact.setText(name + "\n" + number);

            Button btnEdit = itemView.findViewById(R.id.btnEdit);
            Button btnRemove = itemView.findViewById(R.id.btnRemove);
            final int index = i;
            btnEdit.setOnClickListener(v -> editContactAtIndex(index));
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