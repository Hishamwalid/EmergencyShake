package com.example.silentemergency;

import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.silentemergency.utils.PrefManager;

public class ShakeConfigActivity extends AppCompatActivity {

    private SeekBar seekSensitivity, seekShakeCount;
    private TextView tvSensitivityVal, tvShakeCountVal;
    private PrefManager prefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefManager = new PrefManager(this);
        if (prefManager.isDarkMode()) setTheme(R.style.AppTheme_Dark);
        else setTheme(R.style.AppTheme_Light);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shake_config);

        seekSensitivity = findViewById(R.id.seekSensitivity);
        seekShakeCount = findViewById(R.id.seekShakeCount);
        tvSensitivityVal = findViewById(R.id.tvSensitivityVal);
        tvShakeCountVal = findViewById(R.id.tvShakeCountVal);

        float sens = prefManager.getShakeSensitivity();
        int count = prefManager.getShakeCount();
        seekSensitivity.setProgress((int)(sens * 10));
        seekShakeCount.setProgress(count - 1);
        tvSensitivityVal.setText(String.format("%.1f", sens));
        tvShakeCountVal.setText(String.valueOf(count));

        seekSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = progress / 10.0f;
                if (val < 5.0f) val = 5.0f;
                tvSensitivityVal.setText(String.format("%.1f", val));
                prefManager.setShakeSensitivity(val);
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
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
}