package com.example.silentemergency;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.silentemergency.utils.PrefManager;

public class CalculatorActivity extends AppCompatActivity {
    private EditText display;
    private String currentInput = "";
    private String lastOperator = "";
    private double firstOperand = 0;
    private boolean isNewInput = true;
    private PrefManager prefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        display = findViewById(R.id.display);
        prefManager = new PrefManager(this);

        if (prefManager.isFirstTime()) {
            startActivity(new Intent(this, FirstTimeSetupActivity.class));
            finish();
        }
    }

    public void onNumberClick(View view) {
        Button b = (Button) view;
        String text = b.getText().toString();
        if (isNewInput) {
            currentInput = "";
            isNewInput = false;
        }
        if (text.equals(".") && currentInput.contains(".")) return;
        currentInput += text;
        display.setText(currentInput);
    }

    public void onOperatorClick(View view) {
        Button b = (Button) view;
        String op = b.getText().toString();
        if (currentInput.isEmpty() && lastOperator.isEmpty()) return;
        if (!currentInput.isEmpty()) {
            double value = Double.parseDouble(currentInput);
            if (lastOperator.isEmpty()) {
                firstOperand = value;
            } else {
                firstOperand = calculate(firstOperand, value, lastOperator);
                display.setText(formatNumber(firstOperand));
                currentInput = formatNumber(firstOperand);
            }
        }
        lastOperator = op;
        isNewInput = true;
    }

    public void onEqualClick(View view) {
        String storedPass = prefManager.getPassword();
        if (!storedPass.isEmpty() && currentInput.equals(storedPass)) {
            startActivity(new Intent(this, SettingsActivity.class));
            currentInput = "";
            display.setText("0");
            isNewInput = true;
            lastOperator = "";
            return;
        }
        if (currentInput.isEmpty() || lastOperator.isEmpty()) return;
        double secondOperand = Double.parseDouble(currentInput);
        double result = calculate(firstOperand, secondOperand, lastOperator);
        display.setText(formatNumber(result));
        currentInput = formatNumber(result);
        lastOperator = "";
        isNewInput = true;
        firstOperand = 0;
    }

    public void onClearClick(View view) {
        currentInput = "";
        lastOperator = "";
        firstOperand = 0;
        isNewInput = true;
        display.setText("0");
    }

    public void onPercentClick(View view) {
        if (currentInput.isEmpty()) return;
        double val = Double.parseDouble(currentInput) / 100;
        currentInput = formatNumber(val);
        display.setText(currentInput);
    }

    private double calculate(double a, double b, String op) {
        switch (op) {
            case "+": return a + b;
            case "−": return a - b;
            case "×": return a * b;
            case "÷": return a / b;
            default: return b;
        }
    }

    private String formatNumber(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        else return String.valueOf(d);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_calculator, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_change_password) {
            showChangePasswordDialog();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showChangePasswordDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Change Password");
        View view = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        final EditText etSecurityAnswer = view.findViewById(R.id.etSecurityAnswer);
        final EditText etNewPass = view.findViewById(R.id.etNewPassword);
        final EditText etConfirmPass = view.findViewById(R.id.etConfirmPassword);
        builder.setView(view);
        builder.setPositiveButton("Change", (dialog, which) -> {
            String answer = etSecurityAnswer.getText().toString().trim();
            String newPass = etNewPass.getText().toString().trim();
            String confirm = etConfirmPass.getText().toString().trim();
            if (prefManager.checkSecurityAnswer(answer)) {
                if (newPass.equals(confirm) && !newPass.isEmpty()) {
                    prefManager.setPassword(newPass);
                    Toast.makeText(this, "Password changed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Passwords do not match or empty", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Security answer incorrect", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
