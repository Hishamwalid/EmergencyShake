package com.example.silentemergency;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.silentemergency.utils.PrefManager;

public class FirstTimeSetupActivity extends AppCompatActivity {
    private EditText etPassword, etConfirmPassword, etAnswer, etCustomQuestion;
    private Spinner spQuestions;
    private Button btnSave;
    private PrefManager prefManager;

    private String[] predefinedQuestions = {
            "What is your oldest sibling's nickname?",
            "What was the name of your first-grade teacher or childhood best friend?",
            "What was the name of your first pet or stuffed toy?",
            "In what city did your parents meet?",
            "What is the name of the street you grew up on?",
            "What was your childhood nickname?",
            "What was the name of your first employer?"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_time_setup);
        prefManager = new PrefManager(this);

        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        spQuestions = findViewById(R.id.spQuestions);
        etAnswer = findViewById(R.id.etAnswer);
        etCustomQuestion = findViewById(R.id.etCustomQuestion);
        btnSave = findViewById(R.id.btnSave);

        // Build list with "Custom" option at the end
        String[] items = new String[predefinedQuestions.length + 1];
        System.arraycopy(predefinedQuestions, 0, items, 0, predefinedQuestions.length);
        items[predefinedQuestions.length] = "Custom...";

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spQuestions.setAdapter(adapter);

        spQuestions.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == predefinedQuestions.length) {
                    etCustomQuestion.setVisibility(View.VISIBLE);
                } else {
                    etCustomQuestion.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        btnSave.setOnClickListener(v -> {
            String pass = etPassword.getText().toString().trim();
            String confirm = etConfirmPassword.getText().toString().trim();
            String answer = etAnswer.getText().toString().trim();
            String selectedQuestion;
            int pos = spQuestions.getSelectedItemPosition();

            if (pos == predefinedQuestions.length) {
                selectedQuestion = etCustomQuestion.getText().toString().trim();
                if (selectedQuestion.isEmpty()) {
                    Toast.makeText(this, "Please enter a custom security question", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                selectedQuestion = predefinedQuestions[pos];
            }

            if (pass.isEmpty() || confirm.isEmpty() || answer.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!pass.equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!pass.matches("\\d+")) {
                Toast.makeText(this, "Password must contain only digits", Toast.LENGTH_SHORT).show();
                return;
            }

            prefManager.setPassword(pass);
            prefManager.setSecurityQuestion(selectedQuestion);
            prefManager.setSecurityAnswer(answer);
            prefManager.setFirstTime(false);

            Toast.makeText(this, "Setup complete! Please login.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, CalculatorActivity.class));
            finish();
        });
    }
}