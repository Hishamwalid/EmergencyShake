package com.example.silentemergency;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.silentemergency.utils.PrefManager;

public class SettingsActivity extends AppCompatActivity {
    private EditText etContact;
    private Button btnSaveContact;
    private PrefManager prefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefManager = new PrefManager(this);
        etContact = findViewById(R.id.etContact);
        btnSaveContact = findViewById(R.id.btnSaveContact);
        etContact.setText(prefManager.getEmergencyNumber());
        btnSaveContact.setOnClickListener(v -> {
            String number = etContact.getText().toString().trim();
            prefManager.setEmergencyNumber(number);
            Toast.makeText(this, "Emergency contact saved", Toast.LENGTH_SHORT).show();
        });
    }
}