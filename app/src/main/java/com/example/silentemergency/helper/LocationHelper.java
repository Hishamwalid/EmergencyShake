package com.example.silentemergency.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.silentemergency.utils.PrefManager;

import java.util.concurrent.atomic.AtomicBoolean;

public class LocationHelper {

    private static final String TAG                = "LocationHelper";
    private static final long   FRESH_FIX_TIMEOUT = 8000L;

    private final Context context;

    public LocationHelper(Context context) {
        this.context = context;
    }

    public interface LocationMessageCallback {
        void onMessageReady(String message);
    }

    // ── Main async entry point called by EmergencyHandler ────────────────────
    public void generateEmergencyMessageAsync(LocationMessageCallback callback) {
        PrefManager pref     = new PrefManager(context);
        String      route    = buildRouteInfo(pref.getStartingPoint(), pref.getDestination());

        // ✅ PRIMARY PATH: use the live location tracked by EmergencyService
        // This is updated every 30 seconds while protection is active.
        // Zero delay — use it immediately.
        if (pref.hasRecentLiveLocation()) {
            double lat = pref.getLiveLat();
            double lon = pref.getLiveLon();
            Log.d(TAG, "Using live tracked location: " + lat + ", " + lon);
            callback.onMessageReady(buildMessage(route, lat, lon));
            return;
        }

        // ⚠ FALLBACK PATH: protection was not active long enough to get a live
        // fix (e.g. triggered within the first 30 seconds). Request a fresh fix
        // with an 8-second timeout, then fall back to last-known.
        Log.w(TAG, "No live location available — requesting fresh fix");
        requestFreshFix(route, callback);
    }

    @SuppressLint("MissingPermission")
    private void requestFreshFix(String route, LocationMessageCallback callback) {
        LocationManager lm      = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Handler         handler = new Handler(Looper.getMainLooper());
        AtomicBoolean   done    = new AtomicBoolean(false);
        LocationListener[] ref  = new LocationListener[1];

        Runnable timeout = () -> {
            if (done.compareAndSet(false, true)) {
                if (lm != null && ref[0] != null)
                    try { lm.removeUpdates(ref[0]); } catch (Exception ignored) {}
                Log.w(TAG, "Fresh fix timed out — using last-known");
                callback.onMessageReady(buildMessage(route, getLastKnownFallback(lm)));
            }
        };

        ref[0] = new LocationListener() {
            @Override public void onLocationChanged(Location loc) {
                if (done.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeout);
                    try { lm.removeUpdates(this); } catch (Exception ignored) {}
                    callback.onMessageReady(buildMessage(route, loc.getLatitude(), loc.getLongitude()));
                }
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };

        boolean registered = false;
        if (lm != null) {
            for (String provider : new String[]{
                    LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                try {
                    if (lm.isProviderEnabled(provider)) {
                        lm.requestSingleUpdate(provider, ref[0], Looper.getMainLooper());
                        registered = true;
                        break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Provider " + provider + " failed: " + e.getMessage());
                }
            }
        }

        if (registered) {
            handler.postDelayed(timeout, FRESH_FIX_TIMEOUT);
        } else {
            callback.onMessageReady(buildMessage(route, getLastKnownFallback(lm)));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String buildRouteInfo(String start, String dest) {
        if (!start.isEmpty() && !dest.isEmpty())
            return "I was going from " + start + " to " + dest + ". ";
        else if (!start.isEmpty())
            return "I was at " + start + ". ";
        else if (!dest.isEmpty())
            return "I was heading to " + dest + ". ";
        return "";
    }

    private String buildMessage(String route, double lat, double lon) {
        String link = "https://www.google.com/maps?q=" + lat + "," + lon;
        return route + "I am in danger. Please help. My location: " + link;
    }

    private String buildMessage(String route, Location location) {
        if (location != null) return buildMessage(route, location.getLatitude(), location.getLongitude());
        return route + "I am in danger. Please help. (Location unavailable — GPS may be off)";
    }

    @SuppressLint("MissingPermission")
    private Location getLastKnownFallback(LocationManager lm) {
        if (lm == null) return null;
        try {
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            return loc;
        } catch (Exception e) { return null; }
    }

    // ── Legacy sync method — kept so nothing else in the app breaks ───────────
    @SuppressLint("MissingPermission")
    public String generateEmergencyMessage() {
        PrefManager     pref  = new PrefManager(context);
        String          route = buildRouteInfo(pref.getStartingPoint(), pref.getDestination());
        LocationManager lm    = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return buildMessage(route, getLastKnownFallback(lm));
    }

    public void sendLocationAndAlert(String phoneNumber, boolean smsOnly, String customPrefix) {
        SmsHelper.sendSMS(context, phoneNumber, generateEmergencyMessage());
        if (!smsOnly) CallHelper.makeCall(context, phoneNumber);
    }

    public void sendLocationAndAlert(String phoneNumber, boolean smsOnly) {
        sendLocationAndAlert(phoneNumber, smsOnly, "");
    }
}