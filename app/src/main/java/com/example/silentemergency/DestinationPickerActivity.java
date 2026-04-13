package com.example.silentemergency;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener; // Essential for the fix
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Filter;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class DestinationPickerActivity extends AppCompatActivity {

    private MapView              mapView;
    private AutoCompleteTextView searchBox;
    private Button               btnConfirm;
    private GeoPoint             selectedPoint;
    private Marker               currentMarker;

    // Added for the location fix
    private LocationManager locationManager;
    private LocationListener locationListener;

    private final List<String>         suggestionNames   = new ArrayList<>();
    private final List<PlaceSuggestion> suggestionObjects = new ArrayList<>();
    private ArrayAdapter<String>        suggestionsAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable      searchRunnable;

    private static final int LOCATION_PERMISSION_REQUEST = 400;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(
                this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        setContentView(R.layout.activity_destination_picker);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Pick Destination");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mapView   = findViewById(R.id.mapView);
        searchBox = findViewById(R.id.searchBox);
        btnConfirm = findViewById(R.id.btnConfirm);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(12.0);

        // This method now handles the active location fix
        centerOnCurrentLocation();

        // Custom adapter — internal filtering disabled
        suggestionsAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_dropdown_item_1line, suggestionNames) {
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = suggestionNames;
                        results.count  = suggestionNames.size();
                        return results;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint,
                                                  FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };

        searchBox.setAdapter(suggestionsAdapter);
        searchBox.setThreshold(1);

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                if (searchRunnable != null)
                    handler.removeCallbacks(searchRunnable);
                searchRunnable = () -> fetchSuggestions(s.toString());
                handler.postDelayed(searchRunnable, 400);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchBox.setOnItemClickListener((parent, view, position, id) -> {
            if (position < suggestionObjects.size()) {
                PlaceSuggestion selected = suggestionObjects.get(position);
                GeoPoint point = new GeoPoint(selected.lat, selected.lon);
                selectPoint(point);
                mapView.getController().animateTo(point);
                mapView.getController().setZoom(16.0);
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null)
                    imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
            }
        });

        mapView.getOverlays().add(new Overlay() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e, MapView mapView) {
                GeoPoint point = (GeoPoint) mapView.getProjection()
                        .fromPixels((int) e.getX(), (int) e.getY());
                selectPoint(point);
                return true;
            }
        });

        btnConfirm.setOnClickListener(v -> confirmSelection());
    }

    @SuppressWarnings("MissingPermission")
    private void centerOnCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            // Default center if no permission
            mapView.getController().setCenter(new GeoPoint(23.8103, 90.4125));
            return;
        }

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) return;

        // Try to get a fast last known location first so map isn't blank
        Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        Location best = (lastGps != null) ? lastGps : lastNet;

        if (best != null) {
            mapView.getController().setCenter(new GeoPoint(best.getLatitude(), best.getLongitude()));
        } else {
            // If absolutely no last location, center on a default until fix arrives
            mapView.getController().setCenter(new GeoPoint(23.8103, 90.4125));
        }

        // Setup active listener to catch the fix as soon as GPS/Network updates
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                GeoPoint current = new GeoPoint(location.getLatitude(), location.getLongitude());
                mapView.getController().animateTo(current);
                mapView.getController().setZoom(14.0);
                // We got the fix, we can stop listening now to save battery
                locationManager.removeUpdates(this);
            }
            @Override public void onProviderDisabled(@NonNull String provider) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        // Request updates from both to ensure we get a fix quickly
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
    }

    private void fetchSuggestions(String query) {
        if (query == null || query.trim().length() < 1) return;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String url = "https://photon.komoot.io/api/?q="
                        + URLEncoder.encode(query, "UTF-8")
                        + "&limit=6&lang=en";
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) response.append(line);
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray  features     = jsonResponse.getJSONArray("features");

                List<PlaceSuggestion> tempObjects = new ArrayList<>();
                List<String>          tempNames   = new ArrayList<>();

                for (int i = 0; i < features.length(); i++) {
                    JSONObject feature = features.getJSONObject(i);
                    JSONArray  coords  = feature
                            .getJSONObject("geometry")
                            .getJSONArray("coordinates");
                    double lon = coords.getDouble(0);
                    double lat = coords.getDouble(1);

                    JSONObject    props   = feature.getJSONObject("properties");
                    String        name    = props.optString("name", "");
                    String        city    = props.optString("city", "");
                    String        country = props.optString("country", "");
                    StringBuilder display = new StringBuilder();

                    if (!name.isEmpty()) display.append(name);
                    if (!city.isEmpty() && !city.equals(name)) {
                        if (display.length() > 0) display.append(", ");
                        display.append(city);
                    }
                    if (!country.isEmpty()) {
                        if (display.length() > 0) display.append(", ");
                        display.append(country);
                    }
                    if (display.length() == 0) display.append("Unknown location");

                    tempObjects.add(new PlaceSuggestion(display.toString(), lat, lon));
                    tempNames.add(display.toString());
                }

                runOnUiThread(() -> {
                    suggestionObjects.clear();
                    suggestionObjects.addAll(tempObjects);
                    suggestionNames.clear();
                    suggestionNames.addAll(tempNames);
                    suggestionsAdapter.notifyDataSetChanged();
                    if (!suggestionNames.isEmpty() && searchBox.isAttachedToWindow())
                        searchBox.showDropDown();
                });

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void selectPoint(GeoPoint point) {
        selectedPoint = point;
        if (currentMarker != null)
            mapView.getOverlays().remove(currentMarker);
        currentMarker = new Marker(mapView);
        currentMarker.setPosition(point);
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(currentMarker);
        mapView.invalidate();
    }

    private void confirmSelection() {
        if (selectedPoint == null) {
            Toast.makeText(this,
                    "Please tap on the map or search for a location",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent();
        intent.putExtra("address",
                selectedPoint.getLatitude() + ","
                        + selectedPoint.getLongitude());
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            centerOnCurrentLocation();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        // Clean up search runnable
        if (searchRunnable != null) {
            handler.removeCallbacks(searchRunnable);
        }
        // Clean up location listener to save battery
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Final cleanup
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
        handler.removeCallbacksAndMessages(null);
    }
}