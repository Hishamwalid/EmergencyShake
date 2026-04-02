package com.example.silentemergency.ui;

import android.content.*;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.silentemergency.R;
import com.example.silentemergency.service.EmergencyService;

public class SettingsActivity extends AppCompatActivity {

    EditText number;
    SharedPreferences pref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        number = findViewById(R.id.number);
        pref = getSharedPreferences("AppData", MODE_PRIVATE);
    }

    public void save(android.view.View v) {
        pref.edit().putString("contact", number.getText().toString()).apply();
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    public void start(android.view.View v) {
        startService(new Intent(this, EmergencyService.class));
    }

    public void stop(android.view.View v) {
        stopService(new Intent(this, EmergencyService.class));
    }
}