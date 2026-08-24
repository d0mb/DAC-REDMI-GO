package com.dachub.audio;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class MainActivity extends Activity {
    private TextView tvDeviceStatus;
    private Button btnModeWifi;
    private Button btnModeBluetooth;
    private TextView tvActiveModeTitle;
    private TextView tvActiveModeDetails;
    private TextView tvFormatDetails;
    private ProgressBar pbVuMeter;
    private LinearLayout layoutDevicesList;
    private TextView tvNoDevices;
    private Button btnMakeDiscoverable;
    private TextView tvVolumeVal;
    private SeekBar sbVolume;
    private Button btnTestTone;

    private AudioManager audioManager;
    private BluetoothSinkManager bluetoothSinkManager;
    private WifiAudioServer wifiAudioServer;
    private Handler uiHandler;
    private Runnable telemetryRunnable;

    private boolean isWifiMode = true;

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
        setMode(true); // Padrão: Modo Wi-Fi
        setupTelemetryLoop();
    }

    private void initViews() {
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus);
        btnModeWifi = findViewById(R.id.btnModeWifi);
        btnModeBluetooth = findViewById(R.id.btnModeBluetooth);
        tvActiveModeTitle = findViewById(R.id.tvActiveModeTitle);
        tvActiveModeDetails = findViewById(R.id.tvActiveModeDetails);
        tvFormatDetails = findViewById(R.id.tvFormatDetails);
        pbVuMeter = findViewById(R.id.pbVuMeter);
        layoutDevicesList = findViewById(R.id.layoutDevicesList);
        tvNoDevices = findViewById(R.id.tvNoDevices);
        btnMakeDiscoverable = findViewById(R.id.btnMakeDiscoverable);
        tvVolumeVal = findViewById(R.id.tvVolumeVal);
        sbVolume = findViewById(R.id.sbVolume);
        btnTestTone = findViewById(R.id.btnTestTone);

        btnModeWifi.setOnClickListener(v -> setMode(true));
        btnModeBluetooth.setOnClickListener(v -> setMode(false));

        btnTestTone.setOnClickListener(v -> {
            if (wifiAudioServer != null) {
                wifiAudioServer.playTestTone();
                Toast.makeText(this, "Reproduzindo tom de teste nas caixas...", Toast.LENGTH_SHORT).show();
            }
        });

        btnMakeDiscoverable.setOnClickListener(v -> {
            if (bluetoothSinkManager != null) {
                bluetoothSinkManager.makeDiscoverable();
                Toast.makeText(this, "Bluetooth visível por 300 segundos!", Toast.LENGTH_SHORT).show();
                setMode(false);
            }
        });
    }

    private void setMode(boolean wifi) {
        isWifiMode = wifi;
        if (wifi) {
            btnModeWifi.setBackgroundColor(Color.parseColor("#00B0FF"));
            btnModeWifi.setTextColor(Color.parseColor("#0A0E17"));
            btnModeBluetooth.setBackgroundColor(Color.parseColor("#1E2C44"));
            btnModeBluetooth.setTextColor(Color.parseColor("#94A3B8"));

            tvActiveModeTitle.setText("MODO ATIVO: WI-FI (REDE / PC)");
            tvActiveModeTitle.setTextColor(Color.parseColor("#00E5FF"));
            if (wifiAudioServer != null) {
                tvActiveModeDetails.setText("IP do Receptor: http://" + wifiAudioServer.getIpAddress() + ":8080");
            }
            tvFormatDetails.setText("Qualidade: PCM 44.1kHz 16-Bit Estéreo (Lossless 1411 kbps)");
        } else {
            btnModeBluetooth.setBackgroundColor(Color.parseColor("#00B0FF"));
            btnModeBluetooth.setTextColor(Color.parseColor("#0A0E17"));
            btnModeWifi.setBackgroundColor(Color.parseColor("#1E2C44"));
            btnModeWifi.setTextColor(Color.parseColor("#94A3B8"));

            tvActiveModeTitle.setText("MODO ATIVO: BLUETOOTH (A2DP SINK)");
            tvActiveModeTitle.setTextColor(Color.parseColor("#38BDF8"));
            tvActiveModeDetails.setText("Nome do Dispositivo: DAC-HiFi-Audio");
            tvFormatDetails.setText("Pronto para receber som de Celulares, iPhones e Tablets");
            if (bluetoothSinkManager != null) {
                bluetoothSinkManager.makeDiscoverable();
            }
        }
        refreshDevicesList();
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

        bluetoothSinkManager = new BluetoothSinkManager(this, new BluetoothSinkManager.SinkStatusListener() {
            @Override
            public void onStatusChanged(String status, String connectedDevice) {
                runOnUiThread(() -> refreshDevicesList());
            }

            @Override
            public void onDevicesUpdated() {
                runOnUiThread(() -> refreshDevicesList());
            }
        });
        bluetoothSinkManager.init();

        wifiAudioServer = WifiAudioServer.getInstance(this);
        wifiAudioServer.start();
    }

    private void refreshDevicesList() {
        layoutDevicesList.removeAllViews();
        boolean hasDevice = false;

        // 1. Verificar Dispositivo Wi-Fi conectado
        if (wifiAudioServer != null && wifiAudioServer.isStreaming()) {
            hasDevice = true;
            String clientIp = wifiAudioServer.getConnectedClientIp();

            LinearLayout row = createDeviceRow(
                    "💻 Computador Transmissor",
                    "IP: " + clientIp + " • Lossless PCM",
                    "PARAR",
                    v -> {
                        wifiAudioServer.disconnectActiveStream();
                        Toast.makeText(this, "Transmissão Wi-Fi interrompida!", Toast.LENGTH_SHORT).show();
                        refreshDevicesList();
                    }
            );
            layoutDevicesList.addView(row);
        }

        // 2. Verificar Dispositivos Bluetooth Pareados/Conectados
        if (bluetoothSinkManager != null) {
            List<BluetoothDevice> bondedList = bluetoothSinkManager.getBondedDevices();
            for (BluetoothDevice dev : bondedList) {
                hasDevice = true;
                String name = dev.getName() != null ? dev.getName() : "Dispositivo Bluetooth";
                String address = dev.getAddress();

                LinearLayout row = createDeviceRow(
                        "📱 " + name,
                        "MAC: " + address + " (Bluetooth Pareado)",
                        "DESCONECTAR",
                        v -> {
                            bluetoothSinkManager.unpairDevice(dev);
                            Toast.makeText(this, "Dispositivo " + name + " desconectado!", Toast.LENGTH_SHORT).show();
                            refreshDevicesList();
                        }
                );
                layoutDevicesList.addView(row);
            }
        }

        if (!hasDevice) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText(isWifiMode ? "Aguardando transmissão do computador..." : "Nenhum celular conectado via Bluetooth. Toque no botão abaixo para parear.");
            emptyTv.setTextColor(Color.parseColor("#64748B"));
            emptyTv.setTextSize(13);
            emptyTv.setPadding(0, 8, 0, 8);
            layoutDevicesList.addView(emptyTv);
        }
    }

    private LinearLayout createDeviceRow(String title, String subtitle, String buttonText, View.OnClickListener onStop) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(12, 12, 12, 12);
        row.setBackgroundColor(Color.parseColor("#1E293B"));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, 8);
        row.setLayoutParams(rowParams);

        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        infoLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#FFFFFF"));
        tvTitle.setTextSize(14);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextColor(Color.parseColor("#00E5FF"));
        tvSub.setTextSize(11);

        infoLayout.addView(tvTitle);
        infoLayout.addView(tvSub);

        Button btnStop = new Button(this);
        btnStop.setText(buttonText);
        btnStop.setBackgroundColor(Color.parseColor("#EF4444")); // Vermelho
        btnStop.setTextColor(Color.parseColor("#FFFFFF"));
        btnStop.setTextSize(11);
        btnStop.setPadding(16, 4, 16, 4);
        btnStop.setOnClickListener(onStop);

        row.addView(infoLayout);
        row.addView(btnStop);

        return row;
    }

    private void setupTelemetryLoop() {
        telemetryRunnable = new Runnable() {
            @Override
            public void run() {
                if (wifiAudioServer != null) {
                    int level = wifiAudioServer.getCurrentAudioLevel();
                    pbVuMeter.setProgress(wifiAudioServer.isStreaming() ? Math.max(5, level) : 0);
                }
                uiHandler.postDelayed(this, 300);
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
