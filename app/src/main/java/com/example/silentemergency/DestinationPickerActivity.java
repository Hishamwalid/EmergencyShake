package com.example.silentemergency;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
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
import java.util.*;

public class DestinationPickerActivity extends AppCompatActivity {

    private MapView mapView;
    private AutoCompleteTextView searchBox;
    private Button btnConfirm;

    private GeoPoint selectedPoint;
    private Marker marker;

    private ArrayAdapter<String> adapter;
    private List<String> displayList = new ArrayList<>();
    private List<PlaceSuggestion> suggestionList = new ArrayList<>();

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private static final int LOCATION_PERMISSION = 400;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        setContentView(R.layout.activity_destination_picker);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mapView = findViewById(R.id.mapView);
        searchBox = findViewById(R.id.searchBox);
        btnConfirm = findViewById(R.id.btnConfirm);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(13.0);

        centerLocation();

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line,
                displayList);

        searchBox.setAdapter(adapter);
        searchBox.setThreshold(1);

        // 🔥 SEARCH LISTENER
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(Editable s){}

            public void onTextChanged(CharSequence s,int a,int b,int c){
                if(searchRunnable!=null) handler.removeCallbacks(searchRunnable);
                searchRunnable=()->fetchSuggestions(s.toString());
                handler.postDelayed(searchRunnable,500);
            }
        });

        // 🔥 CLICK RESULT
        searchBox.setOnItemClickListener((p,v,pos,id)->{
            PlaceSuggestion s=suggestionList.get(pos);

            GeoPoint point=new GeoPoint(s.lat,s.lon);
            selectPoint(point);

            mapView.getController().animateTo(point);
            mapView.getController().setZoom(16.0);
        });

        // 🔥 MAP TAP
        mapView.getOverlays().add(new Overlay(){
            public boolean onSingleTapConfirmed(MotionEvent e,MapView mv){
                GeoPoint p=(GeoPoint)mv.getProjection().fromPixels((int)e.getX(),(int)e.getY());
                selectPoint(p);
                return true;
            }
        });

        btnConfirm.setOnClickListener(v->confirmSelection());
    }

    // ✅ CURRENT LOCATION
    private void centerLocation(){

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION);

            mapView.getController().setCenter(new GeoPoint(23.8103, 90.4125));
            return;
        }

        try{
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);

            Location loc=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(loc==null) loc=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if(loc!=null){
                GeoPoint current=new GeoPoint(loc.getLatitude(),loc.getLongitude());
                mapView.getController().setCenter(current);
                mapView.getController().setZoom(15.0);
            }

        }catch(Exception e){e.printStackTrace();}
    }

    // 🔥 FINAL SEARCH FIX
    private void fetchSuggestions(String query){

        if(query==null || query.trim().length()<2) return;

        new Thread(()->{
            try{

                String urlStr="https://nominatim.openstreetmap.org/search?q="
                        + URLEncoder.encode(query,"UTF-8")
                        + "&format=json&limit=5&addressdetails=1";

                HttpURLConnection conn=(HttpURLConnection)new URL(urlStr).openConnection();

                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent","SilentEmergencyApp");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                BufferedReader reader=new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));

                StringBuilder result=new StringBuilder();
                String line;

                while((line=reader.readLine())!=null){
                    result.append(line);
                }

                reader.close();

                JSONArray arr=new JSONArray(result.toString());

                List<PlaceSuggestion> temp=new ArrayList<>();
                List<String> names=new ArrayList<>();

                for(int i=0;i<arr.length();i++){

                    JSONObject obj=arr.getJSONObject(i);

                    String name=obj.getString("display_name");

                    double lat=Double.parseDouble(obj.getString("lat"));
                    double lon=Double.parseDouble(obj.getString("lon"));

                    temp.add(new PlaceSuggestion(name,lat,lon));
                    names.add(name);
                }

                runOnUiThread(()->{
                    suggestionList.clear();
                    suggestionList.addAll(temp);

                    displayList.clear();
                    displayList.addAll(names);

                    adapter.notifyDataSetChanged();

                    searchBox.post(()->searchBox.showDropDown()); // 🔥 FORCE SHOW
                });

            }catch(Exception e){
                e.printStackTrace();
            }
        }).start();
    }

    private void selectPoint(GeoPoint p){
        selectedPoint=p;

        if(marker!=null) mapView.getOverlays().remove(marker);

        marker=new Marker(mapView);
        marker.setPosition(p);
        mapView.getOverlays().add(marker);

        mapView.invalidate();
    }

    private void confirmSelection(){

        if(selectedPoint==null){
            Toast.makeText(this,"Select location",Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i=new Intent();
        i.putExtra("address",
                selectedPoint.getLatitude()+","+selectedPoint.getLongitude());

        setResult(RESULT_OK,i);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp(){
        finish();
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);

        if(requestCode==LOCATION_PERMISSION &&
                grantResults.length>0 &&
                grantResults[0]==PackageManager.PERMISSION_GRANTED){

            centerLocation();
        }
    }
}