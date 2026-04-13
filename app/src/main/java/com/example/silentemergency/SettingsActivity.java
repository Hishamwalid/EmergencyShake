package com.example.silentemergency;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class SettingsActivity extends AppCompatActivity {
    private PrefManager prefManager; // [cite: 3]
    private LinearLayout contactsContainer; // [cite: 4]
    private Button btnToggleProtection; // [cite: 4]
    private Button btnSetCurrentLocation; // [cite: 5]
    private TextView tvTimer, tvStartingPoint, tvDestination; // [cite: 6]
    private List<String> contacts = new ArrayList<>(); // [cite: 7]
    private boolean isActive = false; // [cite: 8]
    private long startTime = 0; // [cite: 9]
    private final Handler timerHandler = new Handler(Looper.getMainLooper()); // [cite: 10]
    private Runnable timerRunnable; // [cite: 10]

    private LocationListener activeLocationListener = null; // [cite: 11]
    private final Handler locationHandler = new Handler(Looper.getMainLooper()); // [cite: 12]
    private Runnable locationTimeoutRunnable = null; // [cite: 13]
    private final AtomicBoolean locationFetchDone = new AtomicBoolean(false); // [cite: 13]
    private int pendingEditIndex = -1; //

    private static final int COLOR_RED = 0xFFFF4444; //
    private static final int COLOR_GREEN = 0xFF4CAF50; // [cite: 15]
    private static final int REQUEST_CURRENT_LOCATION = 200; // [cite: 16]
    private static final long LOCATION_TIMEOUT_MS = 10_000L; // [cite: 17]
    private static final String KEY_IS_PROTECTION_ACTIVE = "is_protection_active"; // [cite: 18]
    private static final String KEY_PROTECTION_START_TIME = "protection_start_time"; // [cite: 18]

    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            handleContactResult(result.getData().getData()); // [cite: 19, 20]
                        }
                    });

    private final ActivityResultLauncher<Intent> destinationLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            String address = result.getData().getStringExtra("address");
                            if (address != null && !address.isEmpty()) {
                                prefManager.setDestination(address); // [cite: 23]
                                updateDestinationDisplay(); // [cite: 23]
                                Toast.makeText(this, "Destination saved: " + address, Toast.LENGTH_SHORT).show(); // [cite: 24]
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefManager = new PrefManager(this); // [cite: 26]
        if (prefManager.isDarkMode()) setTheme(R.style.AppTheme_Dark); // [cite: 27]
        else setTheme(R.style.AppTheme_Light); // [cite: 27]
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings); // [cite: 27]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { //
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101); // [cite: 29]
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar); // [cite: 30]
        setSupportActionBar(toolbar); // [cite: 31]
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Emergency Settings"); // [cite: 31]

        findViewById(R.id.btnShakeSettings).setOnClickListener(v -> startActivity(new Intent(this, ShakeConfigActivity.class))); // [cite: 32]
        contactsContainer = findViewById(R.id.contactsContainer); // [cite: 33]
        btnToggleProtection = findViewById(R.id.btnToggleProtection); // [cite: 33]
        btnSetCurrentLocation = findViewById(R.id.btnSetCurrentLocation); // [cite: 33]
        tvTimer = findViewById(R.id.tvTimer); // [cite: 34]
        tvStartingPoint = findViewById(R.id.tvStartingPoint); // [cite: 35]
        tvDestination = findViewById(R.id.tvDestination); // [cite: 36]

        findViewById(R.id.btnAddContact).setOnClickListener(v -> showAddContactOptions()); // [cite: 36]
        btnSetCurrentLocation.setOnClickListener(v -> getCurrentLocation()); // [cite: 36]
        findViewById(R.id.btnSetDestination).setOnClickListener(v -> openDestinationPicker()); // [cite: 37]

        loadContacts(); // [cite: 37]
        updateStartingPointDisplay(); // [cite: 37]
        updateDestinationDisplay(); // [cite: 37]

        isActive = prefManager.getPrefs().getBoolean(KEY_IS_PROTECTION_ACTIVE, false); // [cite: 37]
        if (isActive) {
            startTime = prefManager.getPrefs().getLong(KEY_PROTECTION_START_TIME, System.currentTimeMillis()); // [cite: 38]
            startTimer(); // [cite: 39]
            btnToggleProtection.setText("DEACTIVATE"); // [cite: 39]
            tvTimer.setTextColor(COLOR_GREEN); // [cite: 39]
        } else {
            tvTimer.setText("Protection inactive"); // [cite: 39]
            tvTimer.setTextColor(COLOR_RED); // [cite: 40]
            btnToggleProtection.setText("ACTIVATE"); // [cite: 40]
        }

        btnToggleProtection.setOnClickListener(v -> {
            if (isActive) stopProtection(); // [cite: 40]
            else startProtection(); // [cite: 40]
        });
    }

    private void openDestinationPicker() {
        destinationLauncher.launch(new Intent(this, DestinationPickerActivity.class)); // [cite: 41]
    }

    private void startProtection() {
        ContextCompat.startForegroundService(this, new Intent(this, EmergencyService.class)); // [cite: 43]
        isActive = true;
        startTime = System.currentTimeMillis();
        prefManager.getPrefs().edit()
                .putBoolean(KEY_IS_PROTECTION_ACTIVE, true)
                .putLong(KEY_PROTECTION_START_TIME, startTime).apply(); // [cite: 44]
        startTimer(); // [cite: 45]
        btnToggleProtection.setText("DEACTIVATE");
        tvTimer.setTextColor(COLOR_GREEN);
    }

    private void stopProtection() {
        stopService(new Intent(this, EmergencyService.class)); // [cite: 45]
        isActive = false;
        prefManager.getPrefs().edit().putBoolean(KEY_IS_PROTECTION_ACTIVE, false).apply(); // [cite: 46]
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable); // [cite: 47]
        tvTimer.setText("Protection inactive");
        tvTimer.setTextColor(COLOR_RED);
        btnToggleProtection.setText("ACTIVATE");
    }

    private void startTimer() {
        timerRunnable = () -> {
            long elapsed = System.currentTimeMillis() - startTime; // [cite: 48]
            long s = (elapsed / 1000) % 60; // [cite: 49]
            long m = (elapsed / 60000) % 60; // [cite: 49]
            long h = elapsed / 3600000; // [cite: 50]
            tvTimer.setText(String.format(Locale.US, "Protection active for: %02d:%02d:%02d", h, m, s)); // [cite: 50]
            timerHandler.postDelayed(timerRunnable, 1000); // [cite: 51]
        };
        timerHandler.post(timerRunnable);
    }

    private void updateStartingPointDisplay() {
        String start = prefManager.getStartingPoint(); // [cite: 51]
        tvStartingPoint.setText(start.isEmpty() ? "Starting point: not set" : "Starting point: " + start); // [cite: 52, 53]
    }

    private void updateDestinationDisplay() {
        String dest = prefManager.getDestination(); // [cite: 54]
        tvDestination.setText(dest.isEmpty() ? "Destination: not set" : "Destination: " + dest); // [cite: 55]
    }

    @SuppressWarnings("MissingPermission")
    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CURRENT_LOCATION); // [cite: 60]
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE); // [cite: 61]
        if (lm == null) {
            Toast.makeText(this, "Location service unavailable", Toast.LENGTH_LONG).show(); // [cite: 63]
            return;
        }

        boolean gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER); // [cite: 64]
        boolean networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); // [cite: 65]

        if (!gpsEnabled && !networkEnabled) {
            Toast.makeText(this, "GPS / Location is off.", Toast.LENGTH_LONG).show(); // [cite: 66]
            return;
        }

        setLocationButtonLoading(true); // [cite: 67]
        tvStartingPoint.setText("Starting point: getting location\u2026"); // [cite: 68]
        locationFetchDone.set(false); // [cite: 68]

        locationTimeoutRunnable = () -> {
            if (locationFetchDone.compareAndSet(false, true)) {
                cleanupLocationListener(lm); // [cite: 69]
                runOnUiThread(() -> {
                    setLocationButtonLoading(false); // [cite: 70]
                    tvStartingPoint.setText("Starting point: not set");
                    Toast.makeText(this, "Could not get a fix. Try again outdoors.", Toast.LENGTH_LONG).show(); // [cite: 71]
                });
            }
        };
        locationHandler.postDelayed(locationTimeoutRunnable, LOCATION_TIMEOUT_MS); // [cite: 72]

        activeLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (locationFetchDone.compareAndSet(false, true)) {
                    locationHandler.removeCallbacks(locationTimeoutRunnable); // [cite: 73]
                    cleanupLocationListener(lm); // [cite: 74]
                    processLocation(location); // [cite: 74]
                }
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };

        // FIXED: Listen to both providers for a faster result
        if (networkEnabled) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, activeLocationListener, Looper.getMainLooper());
        if (gpsEnabled) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, activeLocationListener, Looper.getMainLooper());
    }

    private void setLocationButtonLoading(boolean loading) {
        btnSetCurrentLocation.setEnabled(!loading); // [cite: 81]
        btnSetCurrentLocation.setText(loading ? "Getting location\u2026" : "Set Current Location as Starting Point"); // [cite: 82]
    }

    private void cleanupLocationListener(LocationManager lm) {
        try {
            if (lm != null && activeLocationListener != null) lm.removeUpdates(activeLocationListener); // [cite: 83]
        } catch (Exception ignored) {}
        activeLocationListener = null; // [cite: 84]
    }

    private void processLocation(Location location) {
        double lat = location.getLatitude(); // [cite: 85]
        double lng = location.getLongitude(); // [cite: 86]
        new Thread(() -> {
            String addr = getAddressFromLatLng(lat, lng); // [cite: 86]
            if (addr != null) saveAndDisplayStartingPoint(addr); // [cite: 86]
            else fetchPhotonAddress(lat, lng); // [cite: 87]
        }).start();
    }

    private String getAddressFromLatLng(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault()); // [cite: 88]
            List<Address> list = geocoder.getFromLocation(lat, lng, 1); // [cite: 89]
            if (list != null && !list.isEmpty()) {
                String line = list.get(0).getAddressLine(0); // [cite: 89]
                if (line != null && !line.isEmpty()) return line; // [cite: 90]
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null; // [cite: 91]
    }

    private void fetchPhotonAddress(double lat, double lng) {
        HttpURLConnection connection = null;
        try {
            String url = "https://photon.komoot.io/reverse?lon=" + lng + "&lat=" + lat + "&limit=1"; // [cite: 93]
            connection = (HttpURLConnection) new URL(url).openConnection(); // [cite: 94]
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream())); // [cite: 94]
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) response.append(line);
            in.close();
            JSONObject json = new JSONObject(response.toString()); // [cite: 96]
            JSONArray features = json.getJSONArray("features"); // [cite: 96]
            if (features.length() > 0) {
                JSONObject props = features.getJSONObject(0).getJSONObject("properties"); // [cite: 97]
                String name = props.optString("name", ""); // [cite: 98]
                saveAndDisplayStartingPoint(!name.isEmpty() ? name : String.format(Locale.US, "%.6f,%.6f", lat, lng)); // [cite: 107]
            } else {
                saveAndDisplayStartingPoint(String.format(Locale.US, "%.6f,%.6f", lat, lng)); // [cite: 107]
            }
        } catch (Exception e) {
            saveAndDisplayStartingPoint(String.format(Locale.US, "%.6f,%.6f", lat, lng)); // [cite: 109]
        } finally {
            if (connection != null) connection.disconnect(); // [cite: 110]
        }
    }

    private void saveAndDisplayStartingPoint(String address) {
        prefManager.setStartingPoint(address); // [cite: 111]
        String timestamp = new SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault()).format(new Date()); // [cite: 112]
        runOnUiThread(() -> {
            setLocationButtonLoading(false); // [cite: 113]
            tvStartingPoint.setText("Starting point: " + address + "\n(set at " + timestamp + ")"); // [cite: 113]
            Toast.makeText(this, "Location set: " + address, Toast.LENGTH_LONG).show(); // [cite: 113]
        });
    }

    private void showAddContactOptions() {
        if (contacts.size() >= 3) {
            Toast.makeText(this, "Max 3 contacts", Toast.LENGTH_SHORT).show(); // [cite: 115]
            return;
        }
        new AlertDialog.Builder(this).setTitle("Add Contact").setItems(new String[]{"Import", "Manual"}, (d, w) -> {
            if (w == 0) openContactPicker(); // [cite: 116]
            else showManualEntryDialog(); // [cite: 116]
        }).show();
    }

    private void openContactPicker() {
        if (!checkContactPermission()) return; // [cite: 117]
        pendingEditIndex = -1; // [cite: 118]
        contactPickerLauncher.launch(new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)); // [cite: 118]
    }

    private void showManualEntryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); // [cite: 120]
        layout.setPadding(50, 20, 50, 10);
        final EditText inputName = new EditText(this); inputName.setHint("Name"); layout.addView(inputName); // [cite: 120]
        final EditText inputNumber = new EditText(this); inputNumber.setHint("Phone Number"); layout.addView(inputNumber); // [cite: 121]
        new AlertDialog.Builder(this).setTitle("Manual Entry").setView(layout).setPositiveButton("Add", (dialog, which) -> {
            String phone = inputNumber.getText().toString().trim();
            if (!phone.isEmpty()) {
                contacts.add((inputName.getText().toString().trim().isEmpty() ? "Emergency" : inputName.getText().toString().trim()) + ":" + phone); // [cite: 125]
                saveContacts(); refreshContactList(); // [cite: 125]
            }
        }).show();
    }

    private boolean checkContactPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 100); // [cite: 129]
            return false;
        }
        return true; // [cite: 130]
    }

    private void handleContactResult(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)); // [cite: 131]
                String name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)); // [cite: 132]
                try (Cursor p = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", new String[]{id}, null)) {
                    if (p != null && p.moveToFirst()) {
                        String clean = p.getString(p.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)).replaceAll("[\\s\\-()]", ""); // [cite: 135]
                        contacts.add(name + ":" + clean); // [cite: 138]
                        saveContacts(); refreshContactList(); // [cite: 139]
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadContacts() {
        contacts.clear(); // [cite: 143]
        for (int i = 1; i <= 3; i++) {
            String d = prefManager.getEmergencyNumber(i); // [cite: 144]
            if (d != null && !d.isEmpty()) contacts.add(d); // [cite: 145]
        }
        refreshContactList(); // [cite: 145]
    }

    private void refreshContactList() {
        contactsContainer.removeAllViews(); // [cite: 146]
        for (int i = 0; i < contacts.size(); i++) {
            String[] p = contacts.get(i).split(":", 2); // [cite: 147]
            View v = getLayoutInflater().inflate(R.layout.item_contact, null); // [cite: 148]
            ((TextView) v.findViewById(R.id.tvContact)).setText(p.length > 1 ? p[0] + "\n" + p[1] : p[0]); // [cite: 149]
            int index = i;
            v.findViewById(R.id.btnRemove).setOnClickListener(x -> { contacts.remove(index); saveContacts(); refreshContactList(); }); // [cite: 150]
            contactsContainer.addView(v); // [cite: 151]
        }
    }

    private void saveContacts() {
        for (int i = 1; i <= 3; i++) prefManager.setEmergencyNumber(i, ""); // [cite: 151]
        for (int i = 0; i < contacts.size(); i++) prefManager.setEmergencyNumber(i + 1, contacts.get(i)); // [cite: 152]
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CURRENT_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation(); // [cite: 154]
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!locationFetchDone.get()) {
            locationFetchDone.set(true); // [cite: 156]
            locationHandler.removeCallbacks(locationTimeoutRunnable); // [cite: 157]
            cleanupLocationListener((LocationManager) getSystemService(LOCATION_SERVICE)); // [cite: 157]
        }
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable); // [cite: 157]
    }
}