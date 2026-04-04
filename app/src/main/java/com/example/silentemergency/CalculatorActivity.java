package com.example.silentemergency;

import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.silentemergency.utils.PrefManager;
import org.json.JSONArray;
import org.json.JSONException;
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
    private static final int MAX_DIGITS = 12;
    private DecimalFormat df = new DecimalFormat("#.##########");

    // Key for SharedPreferences
    private static final String KEY_HISTORY = "calculator_history";

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
        historyContainer = findViewById(R.id.historyContainer);
        historyScroll = findViewById(R.id.historyScroll);

        if (prefManager.isFirstTime()) {
            startActivity(new Intent(this, FirstTimeSetupActivity.class));
            finish();
        }

        loadHistory();      // Load saved history
        updateDisplay();
        updateHistoryUI();  // Apply correct theme color
    }

    private void loadHistory() {
        String json = prefManager.getPrefs().getString(KEY_HISTORY, "");
        if (json.isEmpty()) return;
        try {
            JSONArray arr = new JSONArray(json);
            historyEntries.clear();
            for (int i = 0; i < arr.length(); i++) {
                historyEntries.add(arr.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveHistory() {
        JSONArray arr = new JSONArray();
        for (String entry : historyEntries) {
            arr.put(entry);
        }
        prefManager.getPrefs().edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    private void addToHistory(String expr, String result) {
        historyEntries.add(0, expr + " = " + result);
        if (historyEntries.size() > 20) historyEntries.remove(historyEntries.size() - 1);
        saveHistory();
        updateHistoryUI();
        historyScroll.post(() -> historyScroll.fullScroll(View.FOCUS_UP));
    }

    private void clearHistory() {
        historyEntries.clear();
        saveHistory();
        updateHistoryUI();
    }

    private void updateHistoryUI() {
        historyContainer.removeAllViews();
        // Get history text color from current theme
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(R.attr.calcHistoryTextColor, typedValue, true);
        int historyColor = typedValue.data;

        if (historyEntries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No calculations yet");
            empty.setPadding(16, 16, 16, 16);
            empty.setTextColor(historyColor);
            empty.setTextSize(14);
            empty.setGravity(android.view.Gravity.END);
            historyContainer.addView(empty);
        } else {
            for (String entry : historyEntries) {
                TextView tv = new TextView(this);
                tv.setText(entry);
                tv.setTextSize(14);
                tv.setTextColor(historyColor);
                tv.setPadding(8, 6, 8, 6);
                tv.setGravity(android.view.Gravity.END);
                historyContainer.addView(tv);
            }
        }
    }

    // ---------- Calculator logic (unchanged from your working version) ----------
    private String getLastNumber() {
        List<String> tokens = tokenizeExpression(currentExpression);
        if (tokens.isEmpty()) return "";
        String last = tokens.get(tokens.size() - 1);
        if (isOperator(last)) return "";
        return last;
    }

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

        if (digit.equals(".")) {
            if (currentExpression.equals("0")) {
                currentExpression = "0.";
            } else if (currentExpression.endsWith(".")) {
                return;
            } else if (lastCharIsOperator()) {
                currentExpression += "0.";
            } else if (getLastNumber().contains(".")) {
                return;
            } else {
                currentExpression += ".";
            }
        } else {
            if (currentExpression.equals("0") && !digit.equals(".")) {
                currentExpression = digit;
            } else {
                currentExpression += digit;
            }
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
        clearHistory();
        updateDisplay();
        preview.setText("");
    }

    public void onPercentClick(View view) {
        List<String> tokens = tokenizeExpression(currentExpression);
        if (tokens.isEmpty()) return;
        for (int i = tokens.size() - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if (!isOperator(token)) {
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

    private double evaluateExpression(String expr) {
        String sanitized = expr.replaceAll("\\.$", ".0");
        return evaluateFull(sanitized);
    }

    private double evaluateFull(String expr) {
        List<String> tokens = tokenizeExpression(expr);
        if (tokens.isEmpty()) return 0;

        List<String> powTokens = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equals("^")) {
                double left = Double.parseDouble(powTokens.remove(powTokens.size() - 1));
                double right = Double.parseDouble(tokens.get(i + 1));
                double result = Math.pow(left, right);
                powTokens.add(formatNumber(result));
                i++;
            } else {
                powTokens.add(t);
            }
        }

        List<String> mulDivTokens = new ArrayList<>();
        for (int i = 0; i < powTokens.size(); i++) {
            String t = powTokens.get(i);
            if (t.equals("×") || t.equals("÷")) {
                double left = Double.parseDouble(mulDivTokens.remove(mulDivTokens.size() - 1));
                double right = Double.parseDouble(powTokens.get(i + 1));
                double result = t.equals("×") ? left * right : left / right;
                mulDivTokens.add(formatNumber(result));
                i++;
            } else {
                mulDivTokens.add(t);
            }
        }

        double result = Double.parseDouble(mulDivTokens.get(0));
        for (int i = 1; i < mulDivTokens.size(); i += 2) {
            String op = mulDivTokens.get(i);
            double next = Double.parseDouble(mulDivTokens.get(i + 1));
            if (op.equals("+")) result += next;
            else if (op.equals("−")) result -= next;
        }
        return result;
    }

    private void updateDisplay() {
        display.setText(currentExpression);
    }

    private void evaluatePreview() {
        if (isResultDisplayed || currentExpression.isEmpty() || lastCharIsOperator() || currentExpression.endsWith(".")) {
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
        return last == '+' || last == '−' || last == '×' || last == '÷' || last == '^';
    }

    private boolean isOperator(String s) {
        return s.equals("+") || s.equals("−") || s.equals("×") || s.equals("÷") || s.equals("^");
    }

    private List<String> tokenizeExpression(String expr) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '+' || c == '−' || c == '×' || c == '÷' || c == '^') {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens;
    }

    private void limitDigits() {
        List<String> tokens = tokenizeExpression(currentExpression);
        StringBuilder limited = new StringBuilder();

        for (String token : tokens) {
            if (isOperator(token)) {
                limited.append(token);
            } else {
                int dotIndex = token.indexOf('.');
                if (dotIndex != -1) {
                    String intPart = token.substring(0, dotIndex);
                    String decPart = token.substring(dotIndex);
                    if (intPart.length() > MAX_DIGITS) {
                        intPart = intPart.substring(0, MAX_DIGITS);
                    }
                    limited.append(intPart).append(decPart);
                } else {
                    if (token.length() > MAX_DIGITS) {
                        token = token.substring(0, MAX_DIGITS);
                    }
                    limited.append(token);
                }
            }
        }
        currentExpression = limited.toString();
        if (currentExpression.isEmpty()) {
            currentExpression = "0";
        }
    }

    private String formatNumber(double d) {
        if (d == (long) d) return String.valueOf((long) d);
        else return df.format(d).replace(",", ".");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_calculator, menu);
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
            startActivity(new Intent(this, ResetPasswordActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}