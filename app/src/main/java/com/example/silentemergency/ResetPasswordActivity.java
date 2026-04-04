package com.example.silentemergency;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.silentemergency.utils.PrefManager;

public class ResetPasswordActivity extends AppCompatActivity {

    private LinearLayout verifyContainer;
    private TextView tvCurrentQuestion;
    private EditText etAnswer;
    private Button btnVerifyAnswer;
    private LinearLayout layoutNewData;
    private CheckBox cbChangePassword, cbChangeQuestion;
    private LinearLayout layoutPassword, layoutChangeQuestion;
    private EditText etNewPassword, etConfirmPassword;
    private Spinner spNewQuestions;
    private EditText etNewCustomQuestion, etNewAnswer;
    private Button btnSetPassword;
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
        prefManager = new PrefManager(this);
        // Apply current theme before layout
        if (prefManager.isDarkMode()) setTheme(R.style.AppTheme_Dark);
        else setTheme(R.style.AppTheme_Light);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        verifyContainer = findViewById(R.id.verifyContainer);
        tvCurrentQuestion = findViewById(R.id.tvCurrentQuestion);
        etAnswer = findViewById(R.id.etAnswer);
        btnVerifyAnswer = findViewById(R.id.btnVerifyAnswer);
        layoutNewData = findViewById(R.id.layoutNewData);
        cbChangePassword = findViewById(R.id.cbChangePassword);
        cbChangeQuestion = findViewById(R.id.cbChangeQuestion);
        layoutPassword = findViewById(R.id.layoutPassword);
        layoutChangeQuestion = findViewById(R.id.layoutChangeQuestion);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        spNewQuestions = findViewById(R.id.spNewQuestions);
        etNewCustomQuestion = findViewById(R.id.etNewCustomQuestion);
        etNewAnswer = findViewById(R.id.etNewAnswer);
        btnSetPassword = findViewById(R.id.btnSetPassword);

        // Display current question
        String currentQuestion = prefManager.getSecurityQuestion();
        tvCurrentQuestion.setText(currentQuestion != null ? currentQuestion : "No question set");

        // Setup spinner for new question
        String[] items = new String[predefinedQuestions.length + 1];
        System.arraycopy(predefinedQuestions, 0, items, 0, predefinedQuestions.length);
        items[predefinedQuestions.length] = "Custom...";
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spNewQuestions.setAdapter(adapter);

        spNewQuestions.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == predefinedQuestions.length) {
                    etNewCustomQuestion.setVisibility(View.VISIBLE);
                } else {
                    etNewCustomQuestion.setVisibility(View.GONE);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Show/hide password section
        cbChangePassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutPassword.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Show/hide question section
        cbChangeQuestion.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutChangeQuestion.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        btnVerifyAnswer.setOnClickListener(v -> {
            String answer = etAnswer.getText().toString().trim();
            if (prefManager.checkSecurityAnswer(answer)) {
                verifyContainer.setVisibility(View.GONE);
                layoutNewData.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, "Incorrect answer", Toast.LENGTH_SHORT).show();
            }
        });

        btnSetPassword.setOnClickListener(v -> {
            boolean passwordChanged = false;
            boolean questionChanged = false;

            if (cbChangePassword.isChecked()) {
                String newPass = etNewPassword.getText().toString().trim();
                String confirm = etConfirmPassword.getText().toString().trim();

                if (newPass.isEmpty() || confirm.isEmpty()) {
                    Toast.makeText(this, "Please fill both password fields", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPass.equals(confirm)) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPass.matches("\\d+")) {
                    Toast.makeText(this, "Password must contain only numbers", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefManager.setPassword(newPass);
                passwordChanged = true;
            }

            if (cbChangeQuestion.isChecked()) {
                int pos = spNewQuestions.getSelectedItemPosition();
                String newQuestion;
                if (pos == predefinedQuestions.length) {
                    newQuestion = etNewCustomQuestion.getText().toString().trim();
                    if (newQuestion.isEmpty()) {
                        Toast.makeText(this, "Please enter a custom question", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else {
                    newQuestion = predefinedQuestions[pos];
                }
                String newAnswer = etNewAnswer.getText().toString().trim();
                if (newAnswer.isEmpty()) {
                    Toast.makeText(this, "Please provide an answer for the new security question", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefManager.setSecurityQuestion(newQuestion);
                prefManager.setSecurityAnswer(newAnswer);
                questionChanged = true;
            }

            if (passwordChanged && questionChanged) {
                Toast.makeText(this, "Password and security question updated", Toast.LENGTH_SHORT).show();
            } else if (passwordChanged) {
                Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show();
            } else if (questionChanged) {
                Toast.makeText(this, "Security question updated successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No changes selected", Toast.LENGTH_SHORT).show();
                return;
            }
            finish();
        });
    }
}