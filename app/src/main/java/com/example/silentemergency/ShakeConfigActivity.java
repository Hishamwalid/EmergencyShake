package com.example.silentemergency;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.silentemergency.utils.PrefManager;

public class ShakeConfigActivity extends AppCompatActivity {
    private PrefManager prefManager;
    private CheckBox cbShakeOnly, cbPowerAndShake, cbPowerOnly, cbSmsOnly;
    private LinearLayout layoutShakeOnly, layoutPowerAndShake, layoutPowerOnly;

    private SeekBar seekShakeSensitivity, seekShakeCount, seekShakeWindow;
    private TextView tvShakeSensitivityVal, tvShakeCountVal, tvShakeWindowVal;

    private SeekBar seekPowerShakeSensitivity, seekPowerShakeCount, seekPowerShakeWindow;
    private TextView tvPowerShakeSensitivityVal, tvPowerShakeCountVal, tvPowerShakeWindowVal;

    private SeekBar seekPowerPressCount, seekPowerPressWindow;
    private TextView tvPowerPressCountVal, tvPowerPressWindowVal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefManager = new PrefManager(this);
        if (prefManager.isDarkMode()) setTheme(R.style.AppTheme_Dark);
        else setTheme(R.style.AppTheme_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shake_config);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Trigger Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        cbShakeOnly             = findViewById(R.id.cbShakeOnly);
        cbPowerAndShake         = findViewById(R.id.cbPowerAndShake);
        cbPowerOnly             = findViewById(R.id.cbPowerOnly);
        cbSmsOnly               = findViewById(R.id.cbSmsOnly);

        layoutShakeOnly         = findViewById(R.id.layoutShakeOnly);
        layoutPowerAndShake     = findViewById(R.id.layoutPowerAndShake);
        layoutPowerOnly         = findViewById(R.id.layoutPowerOnly);

        seekShakeSensitivity    = findViewById(R.id.seekShakeSensitivity);
        seekShakeCount          = findViewById(R.id.seekShakeCount);
        seekShakeWindow         = findViewById(R.id.seekShakeWindow);
        tvShakeSensitivityVal   = findViewById(R.id.tvShakeSensitivityVal);
        tvShakeCountVal         = findViewById(R.id.tvShakeCountVal);
        tvShakeWindowVal        = findViewById(R.id.tvShakeWindowVal);

        seekPowerShakeSensitivity  = findViewById(R.id.seekPowerShakeSensitivity);
        seekPowerShakeCount        = findViewById(R.id.seekPowerShakeCount);
        seekPowerShakeWindow       = findViewById(R.id.seekPowerShakeWindow);
        tvPowerShakeSensitivityVal = findViewById(R.id.tvPowerShakeSensitivityVal);
        tvPowerShakeCountVal       = findViewById(R.id.tvPowerShakeCountVal);
        tvPowerShakeWindowVal      = findViewById(R.id.tvPowerShakeWindowVal);

        seekPowerPressCount     = findViewById(R.id.seekPowerPressCount);
        tvPowerPressCountVal    = findViewById(R.id.tvPowerPressCountVal);
        seekPowerPressWindow    = findViewById(R.id.seekPowerPressWindow);
        tvPowerPressWindowVal   = findViewById(R.id.tvPowerPressWindowVal);

        String savedMode = prefManager.getGestureMode();
        cbShakeOnly.setChecked(savedMode.equals("shake"));
        cbPowerAndShake.setChecked(savedMode.equals("power_shake"));
        cbPowerOnly.setChecked(savedMode.equals("power_only"));

        updateGestureLayouts();
        loadShakeSettings();
        loadPowerPressSettings();

        cbSmsOnly.setChecked(prefManager.isSmsOnly());
        cbSmsOnly.setOnCheckedChangeListener((b, checked) -> prefManager.setSmsOnly(checked));

        cbShakeOnly.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                cbPowerAndShake.setChecked(false);
                cbPowerOnly.setChecked(false);
                prefManager.setGestureMode("shake");
                updateGestureLayouts();
            }
        });

        cbPowerAndShake.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                cbShakeOnly.setChecked(false);
                cbPowerOnly.setChecked(false);
                prefManager.setGestureMode("power_shake");
                updateGestureLayouts();
            }
        });

        cbPowerOnly.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                cbShakeOnly.setChecked(false);
                cbPowerAndShake.setChecked(false);
                prefManager.setGestureMode("power_only");
                updateGestureLayouts();
            }
        });

        // ✅ NEW MATH: 0 on slider = 5.0, 200 on slider = 25.0
        seekShakeSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                float val = 5.0f + (progress / 10.0f);
                tvShakeSensitivityVal.setText(String.format("%.1f", val));
                prefManager.setShakeSensitivity(val);
                seekPowerShakeSensitivity.setProgress(progress);
                tvPowerShakeSensitivityVal.setText(String.format("%.1f", val));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekShakeCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int val = progress + 1;
                tvShakeCountVal.setText(val + " shake" + (val == 1 ? "" : "s"));
                prefManager.setShakeCount(val);
                seekPowerShakeCount.setProgress(progress);
                tvPowerShakeCountVal.setText(val + " shake" + (val == 1 ? "" : "s"));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekShakeWindow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int val = progress + 1;
                tvShakeWindowVal.setText(val + " second" + (val == 1 ? "" : "s"));
                prefManager.setShakeWindow(val);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekPowerShakeSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                float val = 5.0f + (progress / 10.0f);
                tvPowerShakeSensitivityVal.setText(String.format("%.1f", val));
                prefManager.setShakeSensitivity(val);
                seekShakeSensitivity.setProgress(progress);
                tvShakeSensitivityVal.setText(String.format("%.1f", val));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekPowerShakeCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int val = progress + 1;
                tvPowerShakeCountVal.setText(val + " shake" + (val == 1 ? "" : "s"));
                prefManager.setShakeCount(val);
                seekShakeCount.setProgress(progress);
                tvShakeCountVal.setText(val + " shake" + (val == 1 ? "" : "s"));
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekPowerShakeWindow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int val = progress + 1;
                tvPowerShakeWindowVal.setText(val + " second" + (val == 1 ? "" : "s"));
                prefManager.setPowerShakeWindow(val);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekPowerPressCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int val = progress + 1;
                tvPowerPressCountVal.setText(val + " press" + (val == 1 ? "" : "es"));
                prefManager.setPowerPressCount(val);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekPowerPressWindow.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int val = progress + 1;
                tvPowerPressWindowVal.setText(val + " second" + (val == 1 ? "" : "s"));
                prefManager.setPowerPressWindow(val);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void updateGestureLayouts() {
        layoutShakeOnly.setVisibility(cbShakeOnly.isChecked() ? View.VISIBLE : View.GONE);
        layoutPowerAndShake.setVisibility(cbPowerAndShake.isChecked() ? View.VISIBLE : View.GONE);
        layoutPowerOnly.setVisibility(cbPowerOnly.isChecked() ? View.VISIBLE : View.GONE);
    }

    private void loadShakeSettings() {
        float sens = prefManager.getShakeSensitivity();
        int count = prefManager.getShakeCount();
        int sWindow = prefManager.getShakeWindow();
        int psWindow = prefManager.getPowerShakeWindow();

        // ✅ Load with new math logic
        seekShakeSensitivity.setProgress((int)((sens - 5.0f) * 10));
        seekShakeCount.setProgress(count - 1);
        seekShakeWindow.setProgress(sWindow - 1);

        tvShakeSensitivityVal.setText(String.format("%.1f", sens));
        tvShakeCountVal.setText(count + " shake" + (count == 1 ? "" : "s"));
        tvShakeWindowVal.setText(sWindow + " second" + (sWindow == 1 ? "" : "s"));

        seekPowerShakeSensitivity.setProgress((int)((sens - 5.0f) * 10));
        seekPowerShakeCount.setProgress(count - 1);
        seekPowerShakeWindow.setProgress(psWindow - 1);

        tvPowerShakeSensitivityVal.setText(String.format("%.1f", sens));
        tvPowerShakeCountVal.setText(count + " shake" + (count == 1 ? "" : "s"));
        tvPowerShakeWindowVal.setText(psWindow + " second" + (psWindow == 1 ? "" : "s"));
    }

    private void loadPowerPressSettings() {
        int count = prefManager.getPowerPressCount();
        seekPowerPressCount.setProgress(count - 1);
        tvPowerPressCountVal.setText(count + " press" + (count == 1 ? "" : "es"));

        int window = prefManager.getPowerPressWindow();
        seekPowerPressWindow.setProgress(window - 1);
        tvPowerPressWindowVal.setText(window + " second" + (window == 1 ? "" : "s"));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}