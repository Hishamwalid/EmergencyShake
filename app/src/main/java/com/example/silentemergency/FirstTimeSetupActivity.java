package com.example.silentemergency;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.silentemergency.utils.PrefManager;

public class FirstTimeSetupActivity extends AppCompatActivity {
    private EditText etPassword, etConfirmPassword, etAnswer;
    private Spinner spQuestions;
    private Button btnSave;
    private PrefManager prefManager;

    private String[] questions = {
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
        btnSave = findViewById(R.id.btnSave);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, questions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spQuestions.setAdapter(adapter);

        btnSave.setOnClickListener(v -> {
            String pass = etPassword.getText().toString().trim();
            String confirm = etConfirmPassword.getText().toString().trim();
            String answer = etAnswer.getText().toString().trim();

            if (pass.isEmpty() || confirm.isEmpty() || answer.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!pass.equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            prefManager.setPassword(pass);
            prefManager.setSecurityQuestion(questions[spQuestions.getSelectedItemPosition()]);
            prefManager.setSecurityAnswer(answer);
            prefManager.setFirstTime(false);

            Toast.makeText(this, "Setup complete! Please login.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, CalculatorActivity.class));
            finish();
        });
    }
}