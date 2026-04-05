package com.example.silentemergency;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.silentemergency.utils.PrefManager;
import org.json.JSONArray;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class CalculatorActivity extends AppCompatActivity {
    private EditText display;
    private TextView preview;
    private LinearLayout historyContainer;
    private ScrollView historyScroll;
    private PrefManager prefManager;
    private List<String> historyEntries = new ArrayList<>();

    private String currentExpression = "0";
    private String lastResult = "";
    private boolean isResultDisplayed = false;
    private DecimalFormat df = new DecimalFormat("#.##########");

    private static final String KEY_HISTORY = "calculator_history";
    private static final String PREF_NAME = "SilentEmergencyPrefs";

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefManager = new PrefManager(this);
        setTheme(prefManager.isDarkMode() ? R.style.AppTheme_Dark : R.style.AppTheme_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("VeilCal");

        display = findViewById(R.id.display);
        preview = findViewById(R.id.preview);
        historyContainer = findViewById(R.id.historyContainer);
        historyScroll = findViewById(R.id.historyScroll);

        // Fix scrolling on physical phones
        historyScroll.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        // First‑time setup
        if (prefManager.isFirstTime()) {
            startActivity(new Intent(this, FirstTimeSetupActivity.class));
            finish();
            return;
        }

        loadHistory();
        updateDisplay();
        updateHistoryUI();
    }

    // --- Number input ---
    public void onNumberClick(View view) {
        Button b = (Button) view;
        String digit = b.getText().toString();

        if (isResultDisplayed) {
            currentExpression = digit.equals(".") ? "0." : digit;
            isResultDisplayed = false;
        } else {
            if (digit.equals(".")) {
                handleDotInput();
            } else {
                if (currentExpression.equals("0")) currentExpression = digit;
                else currentExpression += digit;
            }
        }
        updateDisplay();
        evaluatePreview();
    }

    private void handleDotInput() {
        String[] parts = currentExpression.split("[+−×÷^]");
        String lastNumber = parts[parts.length - 1];
        if (!lastNumber.contains(".")) {
            currentExpression += ".";
        }
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
    }

    public void onPercentClick(View view) {
        if (currentExpression.isEmpty() || isResultDisplayed) return;
        try {
            double val = Double.parseDouble(currentExpression);
            val = val / 100;
            currentExpression = formatNumber(val);
            updateDisplay();
            evaluatePreview();
        } catch (NumberFormatException e) {
            // ignore
        }
    }

    public void onPowerClick(View view) {
        if (isResultDisplayed) {
            currentExpression = "^";
            isResultDisplayed = false;
        } else {
            currentExpression += "^";
        }
        updateDisplay();
        evaluatePreview();
    }

    public void onEqualClick(View view) {
        String storedPass = prefManager.getPassword();
        if (!storedPass.isEmpty() && currentExpression.equals(storedPass)) {
            // Correct password – open hidden emergency settings
            startActivity(new Intent(this, SettingsActivity.class));
            currentExpression = "0";
            updateDisplay();
            return;
        }

        try {
            String exprToEval = currentExpression;
            while (lastCharIsOperator(exprToEval)) {
                exprToEval = exprToEval.substring(0, exprToEval.length() - 1);
            }

            double result = evaluateExpression(exprToEval);
            String resultStr = formatNumber(result);

            addToHistory(exprToEval, resultStr);
            lastResult = resultStr;
            currentExpression = resultStr;
            isResultDisplayed = true;
            updateDisplay();
            preview.setText("");
        } catch (Exception e) {
            Toast.makeText(this, "Invalid Format", Toast.LENGTH_SHORT).show();
            display.setText("Error");
            currentExpression = "0";
        }
    }

    // --- Math evaluation (BODMAS) ---
    private double evaluateExpression(String expr) {
        String sanitized = expr.replace("×", "*").replace("÷", "/").replace("−", "-");
        return evaluateFull(sanitized);
    }

    private double evaluateFull(String expr) {
        List<String> tokens = tokenize(expr);
        if (tokens.isEmpty()) return 0;

        // Powers
        List<String> postPower = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equals("^")) {
                double left = Double.parseDouble(postPower.remove(postPower.size() - 1));
                double right = Double.parseDouble(tokens.get(++i));
                postPower.add(String.valueOf(Math.pow(left, right)));
            } else postPower.add(tokens.get(i));
        }

        // Multiplication & Division
        List<String> postMD = new ArrayList<>();
        for (int i = 0; i < postPower.size(); i++) {
            String t = postPower.get(i);
            if (t.equals("*") || t.equals("/")) {
                double left = Double.parseDouble(postMD.remove(postMD.size() - 1));
                double right = Double.parseDouble(postPower.get(++i));
                postMD.add(String.valueOf(t.equals("*") ? left * right : left / right));
            } else postMD.add(t);
        }

        // Addition & Subtraction
        double result = Double.parseDouble(postMD.get(0));
        for (int i = 1; i < postMD.size(); i += 2) {
            String op = postMD.get(i);
            double next = Double.parseDouble(postMD.get(i + 1));
            if (op.equals("+")) result += next;
            else if (op.equals("-")) result -= next;
        }
        return result;
    }

    private List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if ("+-*/^".indexOf(c) != -1) {
                if (sb.length() > 0) tokens.add(sb.toString());
                tokens.add(String.valueOf(c));
                sb.setLength(0);
            } else sb.append(c);
        }
        if (sb.length() > 0) tokens.add(sb.toString());
        return tokens;
    }

    // --- Helpers ---
    private void updateDisplay() {
        display.setText(currentExpression);
    }

    private boolean lastCharIsOperator() {
        return lastCharIsOperator(currentExpression);
    }

    private boolean lastCharIsOperator(String s) {
        if (s == null || s.isEmpty()) return false;
        char last = s.charAt(s.length() - 1);
        return "+−×÷^".indexOf(last) != -1;
    }

    private String formatNumber(double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) return "Error";
        if (d == (long) d) return String.valueOf((long) d);
        return df.format(d).replace(",", ".");
    }

    public void onClearClick(View view) {
        currentExpression = "0";
        isResultDisplayed = false;
        lastResult = "";
        clearHistory();
        updateDisplay();
        preview.setText("");
    }

    public void onBackspaceClick(View view) {
        if (isResultDisplayed) {
            onClearClick(null);
            return;
        }
        if (currentExpression.length() > 0) {
            currentExpression = currentExpression.substring(0, currentExpression.length() - 1);
            if (currentExpression.isEmpty()) currentExpression = "0";
        }
        updateDisplay();
        evaluatePreview();
    }

    private void evaluatePreview() {
        if (isResultDisplayed || currentExpression.isEmpty() || lastCharIsOperator()) {
            preview.setText("");
            return;
        }
        try {
            double res = evaluateExpression(currentExpression);
            preview.setText("= " + formatNumber(res));
        } catch (Exception e) {
            preview.setText("");
        }
    }

    // --- History handling ---
    private void addToHistory(String expr, String result) {
        historyEntries.add(0, expr + " = " + result);
        if (historyEntries.size() > 20) historyEntries.remove(historyEntries.size() - 1);
        saveHistory();
        updateHistoryUI();
    }

    private void clearHistory() {
        historyEntries.clear();
        saveHistory();
        updateHistoryUI();
    }

    private void updateHistoryUI() {
        historyContainer.removeAllViews();
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.calcHistoryTextColor, typedValue, true);
        int color = typedValue.data;

        for (String entry : historyEntries) {
            TextView tv = new TextView(this);
            tv.setText(entry);
            tv.setTextColor(color);
            tv.setTextSize(14);
            tv.setPadding(0, 8, 0, 8);
            tv.setGravity(android.view.Gravity.END);
            historyContainer.addView(tv);
        }
        historyScroll.post(() -> historyScroll.fullScroll(View.FOCUS_UP));
    }

    private void loadHistory() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            historyEntries.clear();
            for (int i = 0; i < arr.length(); i++) historyEntries.add(arr.getString(i));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveHistory() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        JSONArray arr = new JSONArray(historyEntries);
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    // --- Three‑dot menu (Theme & Password Reset) ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_calculator, menu);
        // Optional: set sun/moon icon dynamically
        MenuItem themeItem = menu.findItem(R.id.action_theme);
        if (prefManager.isDarkMode()) {
            themeItem.setIcon(R.drawable.ic_moon);
        } else {
            themeItem.setIcon(R.drawable.ic_sun);
        }
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
            // Open password reset activity (three‑dot menu “Settings”)
            startActivity(new Intent(this, ResetPasswordActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}