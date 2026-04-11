package com.example.silentemergency;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.silentemergency.utils.PrefManager;

public class ShakeConfigActivity extends AppCompatActivity {

    private PrefManager prefManager;

    private CheckBox cbShakeOnly, cbPowerAndShake, cbPowerOnly;
    private LinearLayout layoutShakeOnly, layoutPowerAndShake, layoutPowerOnly;
    private SeekBar seekShakeSensitivity, seekShakeCount;
    private TextView tvShakeSensitivityVal, tvShakeCountVal;
    private SeekBar seekPowerShakeSensitivity, seekPowerShakeCount;
    private TextView tvPowerShakeSensitivityVal, tvPowerShakeCountVal;
    private SeekBar seekPowerPressCount;
    private TextView tvPowerPressCountVal;
    private Switch swSmsOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shake_config);

        prefManager = new PrefManager(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Trigger Settings");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Find views
        cbShakeOnly = findViewById(R.id.cbShakeOnly);
        cbPowerAndShake = findViewById(R.id.cbPowerAndShake);
        cbPowerOnly = findViewById(R.id.cbPowerOnly);
        layoutShakeOnly = findViewById(R.id.layoutShakeOnly);
        layoutPowerAndShake = findViewById(R.id.layoutPowerAndShake);
        layoutPowerOnly = findViewById(R.id.layoutPowerOnly);
        seekShakeSensitivity = findViewById(R.id.seekShakeSensitivity);
        seekShakeCount = findViewById(R.id.seekShakeCount);
        tvShakeSensitivityVal = findViewById(R.id.tvShakeSensitivityVal);
        tvShakeCountVal = findViewById(R.id.tvShakeCountVal);
        seekPowerShakeSensitivity = findViewById(R.id.seekPowerShakeSensitivity);
        seekPowerShakeCount = findViewById(R.id.seekPowerShakeCount);
        tvPowerShakeSensitivityVal = findViewById(R.id.tvPowerShakeSensitivityVal);
        tvPowerShakeCountVal = findViewById(R.id.tvPowerShakeCountVal);
        seekPowerPressCount = findViewById(R.id.seekPowerPressCount);
        tvPowerPressCountVal = findViewById(R.id.tvPowerPressCountVal);
        swSmsOnly = findViewById(R.id.swSmsOnly);

        // Load saved gesture mode
        String savedMode = prefManager.getGestureMode();
        cbShakeOnly.setChecked(savedMode.equals("shake"));
        cbPowerAndShake.setChecked(savedMode.equals("power_shake"));
        cbPowerOnly.setChecked(savedMode.equals("power_only"));
        updateGestureLayouts();

        // Load saved values
        loadShakeSettings();
        loadPowerPressSettings();

        // Checkbox listeners
        cbShakeOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                cbPowerAndShake.setChecked(false);
                cbPowerOnly.setChecked(false);
                prefManager.setGestureMode("shake");
                updateGestureLayouts();
            }
        });
        cbPowerAndShake.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                cbShakeOnly.setChecked(false);
                cbPowerOnly.setChecked(false);
                prefManager.setGestureMode("power_shake");
                updateGestureLayouts();
            }
        });
        cbPowerOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                cbShakeOnly.setChecked(false);
                cbPowerAndShake.setChecked(false);
                prefManager.setGestureMode("power_only");
                updateGestureLayouts();
            }
        });

        // Shake SeekBars
        seekShakeSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = progress / 10.0f;
                if (val < 5.0f) val = 5.0f;
                tvShakeSensitivityVal.setText(String.format("%.1f", val));
                prefManager.setShakeSensitivity(val);
                // Sync power+shake SeekBars
                seekPowerShakeSensitivity.setProgress(progress);
                tvPowerShakeSensitivityVal.setText(String.format("%.1f", val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        seekShakeCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress + 1;
                tvShakeCountVal.setText(String.valueOf(val));
                prefManager.setShakeCount(val);
                seekPowerShakeCount.setProgress(progress);
                tvPowerShakeCountVal.setText(String.valueOf(val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Power+Shake SeekBars (mirror)
        seekPowerShakeSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = progress / 10.0f;
                if (val < 5.0f) val = 5.0f;
                tvPowerShakeSensitivityVal.setText(String.format("%.1f", val));
                prefManager.setShakeSensitivity(val);
                seekShakeSensitivity.setProgress(progress);
                tvShakeSensitivityVal.setText(String.format("%.1f", val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        seekPowerShakeCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress + 1;
                tvPowerShakeCountVal.setText(String.valueOf(val));
                prefManager.setShakeCount(val);
                seekShakeCount.setProgress(progress);
                tvShakeCountVal.setText(String.valueOf(val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Power only press count
        seekPowerPressCount.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress + 1;
                tvPowerPressCountVal.setText(String.valueOf(val));
                prefManager.setPowerPressCount(val);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // SMS only switch
        swSmsOnly.setChecked(prefManager.isSmsOnly());
        swSmsOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefManager.setSmsOnly(isChecked);
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
        seekShakeSensitivity.setProgress((int)(sens * 10));
        seekShakeCount.setProgress(count - 1);
        tvShakeSensitivityVal.setText(String.format("%.1f", sens));
        tvShakeCountVal.setText(String.valueOf(count));
        // Sync power+shake SeekBars
        seekPowerShakeSensitivity.setProgress((int)(sens * 10));
        seekPowerShakeCount.setProgress(count - 1);
        tvPowerShakeSensitivityVal.setText(String.format("%.1f", sens));
        tvPowerShakeCountVal.setText(String.valueOf(count));
    }

    private void loadPowerPressSettings() {
        int count = prefManager.getPowerPressCount();
        seekPowerPressCount.setProgress(count - 1);
        tvPowerPressCountVal.setText(String.valueOf(count));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}