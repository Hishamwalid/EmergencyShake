package com.example.silentemergency;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import com.example.silentemergency.utils.PrefManager;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class CalculatorActivity extends AppCompatActivity {
    private EditText display;
    private TextView preview;
    private PrefManager prefManager;
    private List<String> historyEntries = new ArrayList<>();

    private String currentExpression = "0";
    private String lastResult = "";
    private boolean isResultDisplayed = false;
    private static final int MAX_DIGITS = 12;
    private DecimalFormat df = new DecimalFormat("#.##########");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefManager = new PrefManager(this);
        if (prefManager.isDarkMode()) setTheme(R.style.AppTheme_Dark);
        else setTheme(R.style.AppTheme_Light);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(null);

        display = findViewById(R.id.display);
        preview = findViewById(R.id.preview);
        ImageButton historyButton = findViewById(R.id.historyButton);

        historyButton.setOnClickListener(v -> showHistoryDialog());

        if (prefManager.isFirstTime()) {
            startActivity(new Intent(this, FirstTimeSetupActivity.class));
            finish();
        }

        updateDisplay();
    }

    private void showHistoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.TransparentDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_history, null);
        LinearLayout historyList = dialogView.findViewById(R.id.historyList);
        Button closeButton = dialogView.findViewById(R.id.closeHistoryButton);

        historyList.removeAllViews();
        if (historyEntries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No calculations yet");
            empty.setPadding(16, 16, 16, 16);
            empty.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            historyList.addView(empty);
        } else {
            for (String entry : historyEntries) {
                TextView tv = new TextView(this);
                tv.setText(entry);
                tv.setTextSize(16);
                tv.setTextColor(ContextCompat.getColor(this, android.R.color.black));
                tv.setPadding(16, 12, 16, 12);
                historyList.addView(tv);
            }
        }

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(params);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addToHistory(String expr, String result) {
        historyEntries.add(0, expr + " = " + result);
        if (historyEntries.size() > 10) historyEntries.remove(historyEntries.size() - 1);
    }

    // Square Root Button: appends "√("
    public void onSquareRootClick(View view) {
        if (isResultDisplayed) {
            currentExpression = "√(";
            isResultDisplayed = false;
        } else {
            currentExpression += "√(";
        }
        updateDisplay();
        evaluatePreview();
    }

    // Modified to auto‑close parenthesis after typing first digit inside √(
    public void onNumberClick(View view) {
        Button b = (Button) view;
        String digit = b.getText().toString();

        if (isResultDisplayed) {
            currentExpression = digit;
            isResultDisplayed = false;
            updateDisplay();
            evaluatePreview();
            return;
        }

        // Check if we are typing the first digit after an unclosed "√("
        boolean insideUnclosedSqrt = false;
        if (currentExpression.endsWith("√(")) {
            insideUnclosedSqrt = true;
        }

        // Normal digit insertion
        if (currentExpression.equals("0") && !digit.equals(".")) {
            currentExpression = digit;
        } else if (digit.equals(".") && lastCharIsOperator()) {
            currentExpression += "0.";
        } else if (digit.equals(".") && currentExpression.contains(".") && !lastCharIsOperator()) {
            return;
        } else {
            currentExpression += digit;
        }

        // If we were inside an unclosed sqrt, automatically close the parenthesis
        if (insideUnclosedSqrt) {
            currentExpression += ")";
        }

        limitDigits();
        updateDisplay();
        evaluatePreview();
    }

    public void onOperatorClick(View view) {
        Button b = (Button) view;
        String op = b.getText().toString();
        if (isResultDisplayed) {
            currentExpression = lastResult + op;
            isResultDisplayed = false;
        } else {
            if (lastCharIsOperator()) {
                currentExpression = currentExpression.substring(0, currentExpression.length() - 1) + op;
            } else {
                currentExpression += op;
            }
        }
        updateDisplay();
        evaluatePreview();
    }

    public void onBackspaceClick(View view) {
        if (isResultDisplayed) return;
        if (currentExpression.length() > 0) {
            currentExpression = currentExpression.substring(0, currentExpression.length() - 1);
            if (currentExpression.isEmpty()) currentExpression = "0";
        }
        updateDisplay();
        evaluatePreview();
    }

    public void onClearClick(View view) {
        currentExpression = "0";
        isResultDisplayed = false;
        lastResult = "";
        updateDisplay();
        preview.setText("");
    }

    public void onPercentClick(View view) {
        List<String> tokens = tokenizeExpression(currentExpression);
        if (tokens.isEmpty()) return;
        for (int i = tokens.size() - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if (!isOperator(token) && !token.contains("√")) {
                try {
                    double val = Double.parseDouble(token);
                    val = val / 100;
                    String newVal = formatNumber(val);
                    tokens.set(i, newVal);
                    break;
                } catch (NumberFormatException e) { }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) sb.append(t);
        currentExpression = sb.toString();
        updateDisplay();
        evaluatePreview();
    }

    public void onEqualClick(View view) {
        String storedPass = prefManager.getPassword();
        if (!storedPass.isEmpty() && currentExpression.equals(storedPass)) {
            startActivity(new Intent(this, SettingsActivity.class));
            currentExpression = "0";
            updateDisplay();
            return;
        }
        try {
            double result = evaluateExpression(currentExpression);
            String resultStr = formatNumber(result);
            addToHistory(currentExpression, resultStr);
            lastResult = resultStr;
            currentExpression = resultStr;
            isResultDisplayed = true;
            updateDisplay();
            preview.setText("");
        } catch (Exception e) {
            display.setText("Error");
            currentExpression = "0";
            isResultDisplayed = false;
            e.printStackTrace();
        }
    }

    // ---------- Safe evaluation (no regex) ----------
    private double evaluateExpression(String expr) {
        return evaluateSqrt(expr);
    }

    private double evaluateSqrt(String expr) {
        int index = expr.indexOf("√(");
        if (index == -1) {
            return evaluateSimple(expr);
        }
        int openPos = index + 2;
        int closePos = findMatchingParenthesis(expr, openPos);
        if (closePos == -1) {
            closePos = expr.length();
        }
        String inside = expr.substring(openPos, closePos);
        double innerVal = evaluateSqrt(inside);
        double sqrtVal = Math.sqrt(innerVal);
        String sqrtStr = formatNumber(sqrtVal);
        String remaining = expr.substring(0, index) + sqrtStr + expr.substring(closePos);
        return evaluateSqrt(remaining);
    }

    private int findMatchingParenthesis(String expr, int start) {
        int depth = 1;
        for (int i = start; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private double evaluateSimple(String expr) {
        List<String> tokens = new ArrayList<>();
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '+' || c == '−' || c == '×' || c == '÷') {
                if (num.length() > 0) {
                    tokens.add(num.toString());
                    num.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                num.append(c);
            }
        }
        if (num.length() > 0) tokens.add(num.toString());
        if (tokens.isEmpty()) return 0;

        List<String> postTokens = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equals("×") || t.equals("÷")) {
                double left = Double.parseDouble(postTokens.remove(postTokens.size() - 1));
                double right = Double.parseDouble(tokens.get(i + 1));
                double result = t.equals("×") ? left * right : left / right;
                postTokens.add(formatNumber(result));
                i++;
            } else {
                postTokens.add(t);
            }
        }
        double result = Double.parseDouble(postTokens.get(0));
        for (int i = 1; i < postTokens.size(); i += 2) {
            String op = postTokens.get(i);
            double next = Double.parseDouble(postTokens.get(i + 1));
            if (op.equals("+")) result += next;
            else if (op.equals("−")) result -= next;
        }
        return result;
    }

    private void updateDisplay() {
        display.setText(currentExpression);
    }

    private void evaluatePreview() {
        if (isResultDisplayed || currentExpression.isEmpty() || lastCharIsOperator()) {
            preview.setText("");
            return;
        }
        try {
            double result = evaluateExpression(currentExpression);
            preview.setText("= " + formatNumber(result));
        } catch (Exception e) {
            preview.setText("");
        }
    }

    private boolean lastCharIsOperator() {
        if (currentExpression.isEmpty()) return false;
        char last = currentExpression.charAt(currentExpression.length() - 1);
        return last == '+' || last == '−' || last == '×' || last == '÷';
    }

    private boolean isOperator(String s) {
        return s.equals("+") || s.equals("−") || s.equals("×") || s.equals("÷");
    }

    private List<String> tokenizeExpression(String expr) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '+' || c == '−' || c == '×' || c == '÷') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private void limitDigits() {
        List<String> tokens = tokenizeExpression(currentExpression);
        StringBuilder limited = new StringBuilder();
        for (String token : tokens) {
            if (isOperator(token)) {
                limited.append(token);
            } else if (token.contains("√")) {
                limited.append(token);
            } else {
                if (token.contains(".")) {
                    String[] parts = token.split("\\.");
                    if (parts[0].length() > MAX_DIGITS) {
                        parts[0] = parts[0].substring(0, MAX_DIGITS);
                    }
                    limited.append(parts[0]).append(".").append(parts[1]);
                } else {
                    if (token.length() > MAX_DIGITS) {
                        token = token.substring(0, MAX_DIGITS);
                    }
                    limited.append(token);
                }
            }
        }
        currentExpression = limited.toString();
        if (currentExpression.isEmpty()) currentExpression = "0";
    }

    private String formatNumber(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        else return df.format(d).replace(",", ".");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_calculator, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_theme) {
            boolean isDark = prefManager.isDarkMode();
            prefManager.setDarkMode(!isDark);
            recreate();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}