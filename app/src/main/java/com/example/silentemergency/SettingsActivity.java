package com.example.silentemergency;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.silentemergency.utils.PrefManager;

public class SettingsActivity extends AppCompatActivity {
    private EditText etContact;
    private Switch swDarkMode;
    private Button btnSaveContact;
    private PrefManager prefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefManager = new PrefManager(this);
        etContact = findViewById(R.id.etContact);
        btnSaveContact = findViewById(R.id.btnSaveContact);
        swDarkMode = findViewById(R.id.swDarkMode);
        etContact.setText(prefManager.getEmergencyNumber());
        swDarkMode.setChecked(prefManager.isDarkMode());
        swDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefManager.setDarkMode(isChecked);
            recreate();
        });
        btnSaveContact.setOnClickListener(v -> {
            prefManager.setEmergencyNumber(etContact.getText().toString().trim());
            Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show();
        });
    }
}