package com.example.silentemergency;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.silentemergency.service.EmergencyService;
import com.example.silentemergency.utils.PrefManager;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private PrefManager prefManager;
    private LinearLayout contactsContainer;
    private Switch swDarkMode;
    private Button btnActivate, btnDeactivate;
    private List<String> contacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefManager = new PrefManager(this);
        if (prefManager.isDarkMode()) setTheme(R.style.AppTheme_Dark);
        else setTheme(R.style.AppTheme_Light);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Emergency Settings");

        findViewById(R.id.btnShakeSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, ShakeConfigActivity.class));
        });

        contactsContainer = findViewById(R.id.contactsContainer);
        swDarkMode = findViewById(R.id.swDarkMode);
        btnActivate = findViewById(R.id.btnActivate);
        btnDeactivate = findViewById(R.id.btnDeactivate);
        findViewById(R.id.btnAddContact).setOnClickListener(v -> showAddContactDialog());

        loadContacts();

        swDarkMode.setChecked(prefManager.isDarkMode());
        swDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefManager.setDarkMode(isChecked);
            recreate();
        });

        btnActivate.setOnClickListener(v -> {
            Intent intent = new Intent(this, EmergencyService.class);
            startService(intent);
            Toast.makeText(this, "Protection activated", Toast.LENGTH_SHORT).show();
        });

        btnDeactivate.setOnClickListener(v -> {
            Intent intent = new Intent(this, EmergencyService.class);
            stopService(intent);
            Toast.makeText(this, "Protection deactivated", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadContacts() {
        contacts.clear();
        for (int i = 1; i <= 3; i++) {
            String contact = prefManager.getEmergencyNumber(i);
            if (contact != null && !contact.isEmpty()) {
                contacts.add(contact);
            }
        }
        refreshContactList();
    }

    private void refreshContactList() {
        contactsContainer.removeAllViews();
        for (int i = 0; i < contacts.size(); i++) {
            String contact = contacts.get(i);
            View itemView = getLayoutInflater().inflate(R.layout.item_contact, null);
            TextView tvContact = itemView.findViewById(R.id.tvContact);
            Button btnEdit = itemView.findViewById(R.id.btnEdit);
            Button btnRemove = itemView.findViewById(R.id.btnRemove);
            tvContact.setText(contact);
            final int index = i;
            btnEdit.setOnClickListener(v -> showEditContactDialog(index, contact));
            btnRemove.setOnClickListener(v -> {
                contacts.remove(index);
                saveContacts();
                refreshContactList();
            });
            contactsContainer.addView(itemView);
        }
    }

    private void showAddContactDialog() {
        if (contacts.size() >= 3) {
            Toast.makeText(this, "Maximum 3 contacts allowed", Toast.LENGTH_SHORT).show();
            return;
        }
        showContactDialog(null, -1);
    }

    private void showEditContactDialog(int index, String existing) {
        showContactDialog(existing, index);
    }

    private void showContactDialog(String existing, int index) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_contact, null);
        EditText etContact = view.findViewById(R.id.etContact);
        if (existing != null) etContact.setText(existing);
        builder.setView(view)
                .setTitle(existing == null ? "Add Contact" : "Edit Contact")
                .setPositiveButton("Save", (dialog, which) -> {
                    String number = etContact.getText().toString().trim();
                    if (number.isEmpty()) {
                        Toast.makeText(this, "Number cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (existing == null) {
                        contacts.add(number);
                    } else {
                        contacts.set(index, number);
                    }
                    saveContacts();
                    refreshContactList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveContacts() {
        for (int i = 1; i <= 3; i++) {
            prefManager.setEmergencyNumber(i, "");
        }
        for (int i = 0; i < contacts.size(); i++) {
            prefManager.setEmergencyNumber(i + 1, contacts.get(i));
        }
    }
}