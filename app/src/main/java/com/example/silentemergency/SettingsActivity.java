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
    private PrefManager prefManager;
    private LinearLayout contactsContainer;
    private Button btnToggleProtection;
    private Button btnSetCurrentLocation;
    private TextView tvTimer, tvStartingPoint, tvDestination;
    private List<String> contacts = new ArrayList<>();
    private boolean isActive = false;
    private long startTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private LocationListener activeLocationListener = null;
    private final Handler locationHandler = new Handler(Looper.getMainLooper());
    private Runnable locationTimeoutRunnable = null;
    private final AtomicBoolean locationFetchDone = new AtomicBoolean(false);
    private int pendingEditIndex = -1;

    private static final int COLOR_RED = 0xFFFF4444;
    private static final int COLOR_GREEN = 0xFF4CAF50;
    private static final int REQUEST_CURRENT_LOCATION = 200;
    private static final long LOCATION_TIMEOUT_MS = 10_000L;
    private static final String KEY_IS_PROTECTION_ACTIVE = "is_protection_active";
    private static final String KEY_PROTECTION_START_TIME = "protection_start_time";

    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            handleContactResult(result.getData().getData());
                        }
                    });

    private final ActivityResultLauncher<Intent> destinationLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK
                                && result.getData() != null) {
                            String address = result.getData().getStringExtra("address");
                            if (address != null && !address.isEmpty()) {
                                prefManager.setDestination(address);
                                // FIX: save the readable label if the user searched for a place.
                                // If they tapped the map, no label exists — clear the old one.
                                String label = result.getData().getStringExtra("label");
                                if (label != null && !label.isEmpty()) {
                                    prefManager.setDestinationLabel(label);
                                } else {
                                    prefManager.clearDestinationLabel();
                                }
                                updateDestinationDisplay();
                                Toast.makeText(this,
                                        "Destination saved",
                                        Toast.LENGTH_SHORT).show();
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Emergency Settings");

        findViewById(R.id.btnShakeSettings).setOnClickListener(v -> startActivity(new Intent(this, ShakeConfigActivity.class)));
        contactsContainer = findViewById(R.id.contactsContainer);
        btnToggleProtection = findViewById(R.id.btnToggleProtection);
        btnSetCurrentLocation = findViewById(R.id.btnSetCurrentLocation);
        tvTimer = findViewById(R.id.tvTimer);
        tvStartingPoint = findViewById(R.id.tvStartingPoint);
        tvDestination = findViewById(R.id.tvDestination);

        findViewById(R.id.btnAddContact).setOnClickListener(v -> showAddContactOptions());
        btnSetCurrentLocation.setOnClickListener(v -> getCurrentLocation());
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
        destinationLauncher.launch(new Intent(this, DestinationPickerActivity.class));
    }

    private void startProtection() {
        ContextCompat.startForegroundService(this, new Intent(this, EmergencyService.class));
        isActive = true;
        startTime = System.currentTimeMillis();
        prefManager.getPrefs().edit()
                .putBoolean(KEY_IS_PROTECTION_ACTIVE, true)
                .putLong(KEY_PROTECTION_START_TIME, startTime).apply();
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
            long m = (elapsed / 60000) % 60;
            long h = elapsed / 3600000;
            tvTimer.setText(String.format(Locale.US, "Protection active for: %02d:%02d:%02d", h, m, s));
            timerHandler.postDelayed(timerRunnable, 1000);
        };
        timerHandler.post(timerRunnable);
    }

    private void updateStartingPointDisplay() {
        String start = prefManager.getStartingPoint();
        tvStartingPoint.setText(start.isEmpty() ? "Starting point: not set" : "Starting point: " + start);
    }

    private void updateDestinationDisplay() {
        String dest  = prefManager.getDestination();
        String label = prefManager.getDestinationLabel();
        if (dest.isEmpty()) {
            tvDestination.setText("Destination: not set");
        } else if (!label.isEmpty()) {
            // Show the readable name in the UI
            tvDestination.setText("Destination: " + label);
        } else {
            // Fallback to coordinates if user tapped the map
            tvDestination.setText("Destination: " + dest);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CURRENT_LOCATION);
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) {
            Toast.makeText(this, "Location service unavailable", Toast.LENGTH_LONG).show();
            return;
        }

        boolean gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!gpsEnabled && !networkEnabled) {
            Toast.makeText(this, "GPS / Location is off.", Toast.LENGTH_LONG).show();
            return;
        }

        setLocationButtonLoading(true);
        tvStartingPoint.setText("Starting point: getting location…");
        locationFetchDone.set(false);

        locationTimeoutRunnable = () -> {
            if (locationFetchDone.compareAndSet(false, true)) {
                cleanupLocationListener(lm);
                runOnUiThread(() -> {
                    setLocationButtonLoading(false);
                    tvStartingPoint.setText("Starting point: not set");
                    Toast.makeText(this, "Could not get a fix. Try again outdoors.", Toast.LENGTH_LONG).show();
                });
            }
        };

        locationHandler.postDelayed(locationTimeoutRunnable, LOCATION_TIMEOUT_MS);

        activeLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (locationFetchDone.compareAndSet(false, true)) {
                    locationHandler.removeCallbacks(locationTimeoutRunnable);
                    cleanupLocationListener(lm);
                    processLocation(location);
                }
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };

        if (networkEnabled) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, activeLocationListener, Looper.getMainLooper());
        if (gpsEnabled) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, activeLocationListener, Looper.getMainLooper());
    }

    private void setLocationButtonLoading(boolean loading) {
        btnSetCurrentLocation.setEnabled(!loading);
        btnSetCurrentLocation.setText(loading ? "Getting location…" : "Set Current Location as Starting Point");
    }

    private void cleanupLocationListener(LocationManager lm) {
        try {
            if (lm != null && activeLocationListener != null) lm.removeUpdates(activeLocationListener);
        } catch (Exception ignored) {}
        activeLocationListener = null;
    }

    private void processLocation(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        new Thread(() -> {
            String addr = getAddressFromLatLng(lat, lng);
            if (addr != null) saveAndDisplayStartingPoint(addr);
            else fetchPhotonAddress(lat, lng);
        }).start();
    }

    private String getAddressFromLatLng(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(lat, lng, 1);
            if (list != null && !list.isEmpty()) {
                String line = list.get(0).getAddressLine(0);
                if (line != null && !line.isEmpty()) return line;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void fetchPhotonAddress(double lat, double lng) {
        HttpURLConnection connection = null;
        try {
            String url = "https://photon.komoot.io/reverse?lon=" + lng
                    + "&lat=" + lat + "&limit=1";
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            // FIX: timeouts were missing — background thread could hang forever
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            in.close();
            JSONObject json     = new JSONObject(sb.toString());
            JSONArray  features = json.getJSONArray("features");
            String     finalAddr;
            if (features.length() > 0) {
                JSONObject props   = features.getJSONObject(0).getJSONObject("properties");
                String name    = props.optString("name",        "");
                String street  = props.optString("street",      "");
                String housenr = props.optString("housenumber", "");
                String city    = props.optString("city",
                        props.optString("town",
                                props.optString("village",     "")));
                String country = props.optString("country",     "");
                StringBuilder addr = new StringBuilder();
                if (!name.isEmpty())    addr.append(name);
                if (!housenr.isEmpty()) { if (addr.length()>0) addr.append(" ");  addr.append(housenr); }
                if (!street.isEmpty())  { if (addr.length()>0) addr.append(", "); addr.append(street);  }
                if (!city.isEmpty())    { if (addr.length()>0) addr.append(", "); addr.append(city);    }
                if (!country.isEmpty()) { if (addr.length()>0) addr.append(", "); addr.append(country); }
                finalAddr = addr.length() > 0
                        ? addr.toString()
                        : String.format(Locale.US, "%.6f,%.6f", lat, lng);
            } else {
                finalAddr = String.format(Locale.US, "%.6f,%.6f", lat, lng);
            }
            saveAndDisplayStartingPoint(finalAddr);
        } catch (Exception e) {
            e.printStackTrace();
            saveAndDisplayStartingPoint(
                    String.format(Locale.US, "%.6f,%.6f", lat, lng));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void saveAndDisplayStartingPoint(String address) {
        prefManager.setStartingPoint(address);
        String timestamp = new SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault()).format(new Date());
        runOnUiThread(() -> {
            setLocationButtonLoading(false);
            tvStartingPoint.setText("Starting point: " + address + "\n(set at " + timestamp + ")");
            Toast.makeText(this, "Location set: " + address, Toast.LENGTH_LONG).show();
        });
    }

    private void showAddContactOptions() {
        if (contacts.size() >= 3) {
            Toast.makeText(this, "Max 3 contacts", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Add Contact").setItems(new String[]{"Import", "Manual"}, (d, w) -> {
            if (w == 0) openContactPicker();
            else showManualEntryDialog();
        }).show();
    }

    private void openContactPicker() {
        if (!checkContactPermission()) return;
        pendingEditIndex = -1;
        contactPickerLauncher.launch(new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI));
    }

    private boolean isPhoneNumberDuplicate(String phoneNumber) {
        for (String contact : contacts) {
            String[] parts   = contact.split(":", 2);
            String   existing = parts.length > 1 ? parts[1] : parts[0];
            if (existing.equals(phoneNumber)) return true;
        }
        return false;
    }

    private void showManualEntryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);
        final EditText inputName = new EditText(this); inputName.setHint("Name"); layout.addView(inputName);
        final EditText inputNumber = new EditText(this); inputNumber.setHint("Phone Number"); layout.addView(inputNumber);

        new AlertDialog.Builder(this).setTitle("Manual Entry").setView(layout).setPositiveButton("Add", (dialog, which) -> {
            String phone = inputNumber.getText().toString().trim();
            if (!phone.isEmpty()) {
                if (isPhoneNumberDuplicate(phone)) {
                    Toast.makeText(this, "This number is already in your emergency contacts", Toast.LENGTH_SHORT).show();
                    return;
                }
                contacts.add((inputName.getText().toString().trim().isEmpty() ? "Emergency" : inputName.getText().toString().trim()) + ":" + phone);
                saveContacts();
                refreshContactList();
            }
        }).show();
    }

    private boolean checkContactPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 100);
            return false;
        }
        return true;
    }

    private void handleContactResult(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                String name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                try (Cursor p = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", new String[]{id}, null)) {
                    if (p != null && p.moveToFirst()) {
                        String clean = p.getString(p.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                .replaceAll("[\\s\\-()]", "");
                        if (isPhoneNumberDuplicate(clean)) {
                            Toast.makeText(this, "This number is already in your emergency contacts", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        contacts.add(name + ":" + clean);
                        saveContacts();
                        refreshContactList();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            String[] p = contacts.get(i).split(":", 2);
            View v = getLayoutInflater().inflate(R.layout.item_contact, null);
            ((TextView) v.findViewById(R.id.tvContact)).setText(p.length > 1 ? p[0] + "\n" + p[1] : p[0]);
            int index = i;
            v.findViewById(R.id.btnRemove).setOnClickListener(x -> { contacts.remove(index); saveContacts(); refreshContactList(); });
            contactsContainer.addView(v);
        }
    }

    private void saveContacts() {
        for (int i = 1; i <= 3; i++) prefManager.setEmergencyNumber(i, "");
        for (int i = 0; i < contacts.size(); i++) prefManager.setEmergencyNumber(i + 1, contacts.get(i));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CURRENT_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // FIX: always clean up regardless of whether a fetch was in progress
        locationFetchDone.set(true);
        locationHandler.removeCallbacks(locationTimeoutRunnable);
        cleanupLocationListener((LocationManager) getSystemService(LOCATION_SERVICE));
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
    }
}