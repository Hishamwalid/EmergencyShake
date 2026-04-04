package com.example.silentemergency;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.silentemergency.utils.PrefManager;

public class ResetPasswordActivity extends AppCompatActivity {

    private TextView tvSecurityQuestion;
    private EditText etAnswer;
    private LinearLayout layoutNewPassword;
    private EditText etNewPassword, etConfirmPassword;
    private Button btnVerifyAnswer, btnSetPassword;
    private PrefManager prefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        prefManager = new PrefManager(this);

        tvSecurityQuestion = findViewById(R.id.tvSecurityQuestion);
        etAnswer = findViewById(R.id.etAnswer);
        layoutNewPassword = findViewById(R.id.layoutNewPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnVerifyAnswer = findViewById(R.id.btnVerifyAnswer);
        btnSetPassword = findViewById(R.id.btnSetPassword);

        // Display the stored security question
        String question = prefManager.getSecurityQuestion();
        if (question != null && !question.isEmpty()) {
            tvSecurityQuestion.setText(question);
        } else {
            tvSecurityQuestion.setText("What is your security question?");
        }

        btnVerifyAnswer.setOnClickListener(v -> {
            String answer = etAnswer.getText().toString().trim();
            if (prefManager.checkSecurityAnswer(answer)) {
                // Answer correct – show password fields
                layoutNewPassword.setVisibility(View.VISIBLE);
                btnVerifyAnswer.setVisibility(View.GONE);
                etAnswer.setEnabled(false);
            } else {
                Toast.makeText(this, "Incorrect answer. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });

        btnSetPassword.setOnClickListener(v -> {
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
            Toast.makeText(this, "Password changed successfully!", Toast.LENGTH_SHORT).show();
            finish(); // Close activity
        });
    }
}