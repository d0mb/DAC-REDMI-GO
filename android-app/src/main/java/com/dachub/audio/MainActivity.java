package com.dachub.audio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView tvDeviceStatus;
    private TextView tvConnectedClient;
    private TextView tvStreamBadge;
    private ProgressBar pbVuMeter;
    private TextView tvBtName;
    private TextView tvBtStatus;
    private TextView tvWifiIp;
    private TextView tvVolumeVal;
    private SeekBar sbVolume;
    private Button btnToggleBt;
    private Button btnTestTone;

    private AudioManager audioManager;
    private BluetoothSinkManager bluetoothSinkManager;
    private WifiAudioServer wifiAudioServer;
    private Handler uiHandler;
    private Runnable telemetryRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        uiHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupAudioService();
        setupVolumeControls();
        setupTelemetryLoop();
    }

    private void initViews() {
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus);
        tvConnectedClient = findViewById(R.id.tvConnectedClient);
        tvStreamBadge = findViewById(R.id.tvStreamBadge);
        pbVuMeter = findViewById(R.id.pbVuMeter);
        tvBtName = findViewById(R.id.tvBtName);
        tvBtStatus = findViewById(R.id.tvBtStatus);
        tvWifiIp = findViewById(R.id.tvWifiIp);
        tvVolumeVal = findViewById(R.id.tvVolumeVal);
        sbVolume = findViewById(R.id.sbVolume);
        btnToggleBt = findViewById(R.id.btnToggleBt);
        btnTestTone = findViewById(R.id.btnTestTone);

        btnTestTone.setOnClickListener(v -> {
            if (wifiAudioServer != null) {
                wifiAudioServer.playTestTone();
                Toast.makeText(this, "Reproduzindo tom de teste nas caixas...", Toast.LENGTH_SHORT).show();
            }
        });

        btnToggleBt.setOnClickListener(v -> {
            if (bluetoothSinkManager != null) {
                bluetoothSinkManager.makeDiscoverable();
                Toast.makeText(this, "Visível por 300 segundos para pareamento!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupVolumeControls() {
        if (audioManager != null) {
            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

            sbVolume.setMax(maxVol);
            sbVolume.setProgress(curVol);
            updateVolumeLabel(curVol, maxVol);

            sbVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && audioManager != null) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                        updateVolumeLabel(progress, maxVol);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    private void updateVolumeLabel(int cur, int max) {
        int pct = (cur * 100) / max;
        tvVolumeVal.setText(pct + "%");
    }

    private void setupAudioService() {
        Intent serviceIntent = new Intent(this, DacAudioService.class);
        startService(serviceIntent);

        bluetoothSinkManager = new BluetoothSinkManager(this, (status, connectedDevice) -> runOnUiThread(() -> {
            tvBtStatus.setText(status);
            if (connectedDevice != null) {
                tvBtName.setText(connectedDevice);
            }
        }));
        bluetoothSinkManager.init();

        wifiAudioServer = WifiAudioServer.getInstance(this);
        wifiAudioServer.start();
        tvWifiIp.setText("http://" + wifiAudioServer.getIpAddress() + ":8080");
    }

    private void setupTelemetryLoop() {
        telemetryRunnable = new Runnable() {
            @Override
            public void run() {
                if (wifiAudioServer != null) {
                    boolean streaming = wifiAudioServer.isStreaming();
                    String client = wifiAudioServer.getConnectedClientIp();
                    int level = wifiAudioServer.getCurrentAudioLevel();

                    if (streaming) {
                        tvStreamBadge.setText("🟢 TRANSMITINDO");
                        tvStreamBadge.setTextColor(0xFF00E676);
                        tvConnectedClient.setText("PC / Dispositivo: " + client);
                        pbVuMeter.setProgress(Math.max(5, level));
                    } else {
                        tvStreamBadge.setText("⚪ EM ESPERA");
                        tvStreamBadge.setTextColor(0xFF64748B);
                        tvConnectedClient.setText(client.equals("Nenhum") ? "Aguardando Transmissão do PC..." : client);
                        pbVuMeter.setProgress(0);
                    }
                }
                uiHandler.postDelayed(this, 200);
            }
        };
        uiHandler.post(telemetryRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (uiHandler != null && telemetryRunnable != null) {
            uiHandler.removeCallbacks(telemetryRunnable);
        }
    }
}
