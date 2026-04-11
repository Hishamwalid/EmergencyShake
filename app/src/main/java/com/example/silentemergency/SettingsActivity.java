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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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

    private static final String KEY_IS_PROTECTION_ACTIVE = "is_protection_active";
    private static final String KEY_PROTECTION_START_TIME = "protection_start_time";

    // Contact picker
    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri contactUri = result.getData().getData();
                            handleContactResult(contactUri);
                        }
                    });

    // Destination picker launcher
    private final ActivityResultLauncher<Intent> destinationLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            String address = result.getData().getStringExtra("address");
                            if (address != null && !address.isEmpty()) {
                                prefManager.setDestination(address);
                                updateDestinationDisplay();
                                Toast.makeText(this, "Destination saved: " + address, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefManager = new PrefManager(this);

        if (prefManager.isDarkMode()) setTheme(R.style.AppTheme_Dark);
        else setTheme(R.style.AppTheme_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle("Emergency Settings");

        findViewById(R.id.btnShakeSettings).setOnClickListener(v ->
                startActivity(new Intent(this, ShakeConfigActivity.class)));

        contactsContainer = findViewById(R.id.contactsContainer);
        btnToggleProtection = findViewById(R.id.btnToggleProtection);
        tvTimer = findViewById(R.id.tvTimer);
        tvStartingPoint = findViewById(R.id.tvStartingPoint);
        tvDestination = findViewById(R.id.tvDestination);

        findViewById(R.id.btnAddContact).setOnClickListener(v -> showAddContactOptions());
        findViewById(R.id.btnSetCurrentLocation).setOnClickListener(v -> getCurrentLocation());
        findViewById(R.id.btnSetDestination).setOnClickListener(v -> openDestinationPicker());

        loadContacts();
        updateStartingPointDisplay();
        updateDestinationDisplay();

        isActive = prefManager.getPrefs().getBoolean(KEY_IS_PROTECTION_ACTIVE, false);

        if (isActive) {
            startTime = prefManager.getPrefs().getLong(KEY_PROTECTION_START_TIME, System.currentTimeMillis());
            startTimer();
            btnToggleProtection.setText("DEACTIVATE");
            tvTimer.setTextColor(COLOR_GREEN);
        } else {
            tvTimer.setText("Protection inactive");
            tvTimer.setTextColor(COLOR_RED);
            btnToggleProtection.setText("ACTIVATE");
        }

        btnToggleProtection.setOnClickListener(v -> {
            if (isActive) stopProtection();
            else startProtection();
        });
    }

    private void openDestinationPicker() {
        Intent intent = new Intent(this, DestinationPickerActivity.class);
        destinationLauncher.launch(intent);
    }

    private void startProtection() {
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
        tvTimer.setTextColor(COLOR_GREEN);
    }

    private void stopProtection() {
        stopService(new Intent(this, EmergencyService.class));
        isActive = false;
        prefManager.getPrefs().edit().putBoolean(KEY_IS_PROTECTION_ACTIVE, false).apply();
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        tvTimer.setText("Protection inactive");
        tvTimer.setTextColor(COLOR_RED);
        btnToggleProtection.setText("ACTIVATE");
    }

    private void startTimer() {
        timerRunnable = () -> {
            long elapsed = System.currentTimeMillis() - startTime;
            long s = (elapsed / 1000) % 60;
            long m = (elapsed / (1000 * 60)) % 60;
            long h = (elapsed / (1000 * 60 * 60));
            tvTimer.setText(String.format("Protection active for: %02d:%02d:%02d", h, m, s));
            timerHandler.postDelayed(timerRunnable, 1000);
        };
        timerHandler.post(timerRunnable);
    }

    private void updateStartingPointDisplay() {
        String start = prefManager.getStartingPoint();
        tvStartingPoint.setText(start.isEmpty() ? "Starting point: not set" : "Starting point: " + start);
    }

    private void updateDestinationDisplay() {
        String dest = prefManager.getDestination();
        tvDestination.setText(dest.isEmpty() ? "Destination: not set" : "Destination: " + dest);
    }

    @SuppressWarnings("deprecation")
    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CURRENT_LOCATION);
            return;
        }
        Toast.makeText(this, "Getting location...", Toast.LENGTH_SHORT).show();
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (loc == null) {
            Toast.makeText(this, "Enable GPS and try again", Toast.LENGTH_LONG).show();
            return;
        }
        double lat = loc.getLatitude();
        double lng = loc.getLongitude();
        String addr = getAddressFromLatLng(lat, lng);
        if (addr != null) {
            prefManager.setStartingPoint(addr);
            updateStartingPointDisplay();
            return;
        }
        // Fallback to Photon reverse geocoding
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String url = "https://photon.komoot.io/reverse?lon=" + lng + "&lat=" + lat + "&limit=1";
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) response.append(line);
                in.close();
                JSONObject json = new JSONObject(response.toString());
                JSONArray features = json.getJSONArray("features");
                String finalAddr;
                if (features.length() > 0) {
                    JSONObject props = features.getJSONObject(0).getJSONObject("properties");
                    String name = props.optString("name", "");
                    String street = props.optString("street", "");
                    String housenr = props.optString("housenumber", "");
                    String city = props.optString("city", props.optString("town", props.optString("village", "")));
                    String country = props.optString("country", "");
                    StringBuilder sb = new StringBuilder();
                    if (!name.isEmpty()) sb.append(name);
                    if (!housenr.isEmpty()) { if (sb.length() > 0) sb.append(" "); sb.append(housenr); }
                    if (!street.isEmpty()) { if (sb.length() > 0) sb.append(", "); sb.append(street); }
                    if (!city.isEmpty()) { if (sb.length() > 0) sb.append(", "); sb.append(city); }
                    if (!country.isEmpty()) { if (sb.length() > 0) sb.append(", "); sb.append(country); }
                    finalAddr = sb.length() > 0 ? sb.toString() : (lat + "," + lng);
                } else {
                    finalAddr = lat + "," + lng;
                }
                runOnUiThread(() -> {
                    prefManager.setStartingPoint(finalAddr);
                    updateStartingPointDisplay();
                    Toast.makeText(this, "Location set: " + finalAddr, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    prefManager.setStartingPoint(lat + "," + lng);
                    updateStartingPointDisplay();
                    Toast.makeText(this, "Could not resolve address", Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    @SuppressWarnings("deprecation")
    private String getAddressFromLatLng(double lat, double lng) {
        try {
            Geocoder g = new Geocoder(this, Locale.getDefault());
            List<Address> list = g.getFromLocation(lat, lng, 1);
            if (list != null && !list.isEmpty()) {
                String line = list.get(0).getAddressLine(0);
                if (line != null && !line.isEmpty()) return line;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // --- CONTACT SECTION with duplicate prevention ---
    private void showAddContactOptions() {
        if (contacts.size() >= 3) {
            Toast.makeText(this, "Max 3 contacts", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] opt = {"Import from Contacts", "Manual"};
        new AlertDialog.Builder(this)
                .setTitle("Add Contact")
                .setItems(opt, (d, w) -> {
                    if (w == 0) openContactPicker();
                    else showManualEntryDialog();
                }).show();
    }

    private void openContactPicker() {
        if (!checkContactPermission()) return;
        pendingEditIndex = -1;
        Intent i = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(i);
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
                        if (isPhoneNumberDuplicate(phone)) {
                            Toast.makeText(this, "This phone number is already in your emergency contacts", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        contacts.add((name.isEmpty() ? "Emergency" : name) + ":" + phone);
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS}, 100);
            return false;
        }
        return true;
    }

    private void handleContactResult(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                String name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                Cursor p = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                        new String[]{id}, null);
                if (p != null && p.moveToFirst()) {
                    String num = p.getString(p.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    String cleanNumber = num.replaceAll("[\\s\\-()]", "");
                    if (isPhoneNumberDuplicate(cleanNumber)) {
                        Toast.makeText(this, "This phone number is already in your emergency contacts", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    contacts.add(name + ":" + cleanNumber);
                    saveContacts();
                    refreshContactList();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isPhoneNumberDuplicate(String phoneNumber) {
        for (String contact : contacts) {
            String[] parts = contact.split(":");
            String existingNumber = parts.length > 1 ? parts[1] : parts[0];
            if (existingNumber.equals(phoneNumber)) {
                return true;
            }
        }
        return false;
    }

    private void loadContacts() {
        contacts.clear();
        for (int i = 1; i <= 3; i++) {
            String d = prefManager.getEmergencyNumber(i);
            if (d != null && !d.isEmpty()) contacts.add(d);
        }
        refreshContactList();
    }

    private void refreshContactList() {
        contactsContainer.removeAllViews();
        for (int i = 0; i < contacts.size(); i++) {
            String data = contacts.get(i);
            String[] p = data.split(":");
            View v = getLayoutInflater().inflate(R.layout.item_contact, null);
            ((TextView) v.findViewById(R.id.tvContact)).setText(p[0] + "\n" + p[1]);
            int index = i;
            v.findViewById(R.id.btnRemove).setOnClickListener(x -> {
                contacts.remove(index);
                saveContacts();
                refreshContactList();
            });
            contactsContainer.addView(v);
        }
    }

    private void saveContacts() {
        for (int i = 1; i <= 3; i++) prefManager.setEmergencyNumber(i, "");
        for (int i = 0; i < contacts.size(); i++) prefManager.setEmergencyNumber(i + 1, contacts.get(i));
    }
}