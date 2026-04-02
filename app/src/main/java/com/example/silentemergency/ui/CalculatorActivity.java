package com.example.silentemergency.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.example.silentemergency.R;

public class CalculatorActivity extends AppCompatActivity {

    EditText display;
    String input = "";
    SharedPreferences pref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        display = findViewById(R.id.display);
        pref = getSharedPreferences("AppData", MODE_PRIVATE);

        requestPermissions(new String[]{
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CALL_PHONE
        }, 1);
    }

    public void click(android.view.View v) {
        Button b = (Button) v;
        input += b.getText().toString();
        display.setText(input);
    }

    public void clear(android.view.View v) {
        input = "";
        display.setText("");
    }

    public void equal(android.view.View v) {

        String pass = pref.getString("password", "1234");

        if(input.equals(pass)) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            display.setText("0");
            input = "";
        }
    }
}