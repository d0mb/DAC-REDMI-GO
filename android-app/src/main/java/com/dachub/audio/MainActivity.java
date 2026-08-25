package com.dachub.audio;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
    private static final String TAG = "MainActivity";

    public static final int MODE_WIFI = 0;
    public static final int MODE_AIRPLAY = 1;
    public static final int MODE_BLUETOOTH = 2;

    private int currentMode = MODE_WIFI;

    private Button btnModeWifi;
    private Button btnModeAirPlay;
    private Button btnModeBluetooth;
    private TextView tvActiveModeTitle;
    private TextView tvActiveModeDetails;
    private TextView tvFormatDetails;
    private TextView tvVuVal;
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
    private AirPlayServer airPlayServer;
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
        setMode(MODE_WIFI);
        setupTelemetryLoop();
    }

    private void initViews() {
        btnModeWifi = findViewById(R.id.btnModeWifi);
        btnModeAirPlay = findViewById(R.id.btnModeAirPlay);
        btnModeBluetooth = findViewById(R.id.btnModeBluetooth);
        tvActiveModeTitle = findViewById(R.id.tvActiveModeTitle);
        tvActiveModeDetails = findViewById(R.id.tvActiveModeDetails);
        tvFormatDetails = findViewById(R.id.tvFormatDetails);
        tvVuVal = findViewById(R.id.tvVuVal);
        pbVuMeter = findViewById(R.id.pbVuMeter);
        layoutDevicesList = findViewById(R.id.layoutDevicesList);
        tvNoDevices = findViewById(R.id.tvNoDevices);
        btnMakeDiscoverable = findViewById(R.id.btnMakeDiscoverable);
        tvVolumeVal = findViewById(R.id.tvVolumeVal);
        sbVolume = findViewById(R.id.sbVolume);
        btnTestTone = findViewById(R.id.btnTestTone);

        btnModeWifi.setOnClickListener(v -> setMode(MODE_WIFI));
        btnModeAirPlay.setOnClickListener(v -> setMode(MODE_AIRPLAY));
        btnModeBluetooth.setOnClickListener(v -> setMode(MODE_BLUETOOTH));

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
            }
        });
    }

    private void setMode(int mode) {
        currentMode = mode;

        // Resetar cores das pílulas
        btnModeWifi.setBackgroundColor(Color.parseColor("#282828"));
        btnModeWifi.setTextColor(Color.parseColor("#FFFFFF"));
        btnModeAirPlay.setBackgroundColor(Color.parseColor("#282828"));
        btnModeAirPlay.setTextColor(Color.parseColor("#FFFFFF"));
        btnModeBluetooth.setBackgroundColor(Color.parseColor("#282828"));
        btnModeBluetooth.setTextColor(Color.parseColor("#FFFFFF"));

        btnMakeDiscoverable.setVisibility(View.GONE);

        if (mode == MODE_WIFI) {
            btnModeWifi.setBackgroundColor(Color.parseColor("#1DB954"));
            btnModeWifi.setTextColor(Color.parseColor("#000000"));

            tvActiveModeTitle.setText("Transmissão Wi-Fi (PC / Lossless)");
            tvActiveModeTitle.setTextColor(Color.parseColor("#FFFFFF"));
            String ip = (wifiAudioServer != null) ? wifiAudioServer.getIpAddress() : "192.168.15.12";
            tvActiveModeDetails.setText("IP do Receptor: http://" + ip + ":8080");
            tvActiveModeDetails.setTextColor(Color.parseColor("#1DB954"));
            tvFormatDetails.setText("Qualidade: PCM 16-bit 44.1kHz Estéreo • Lossless Direct (1411 kbps)");
        } else if (mode == MODE_AIRPLAY) {
            btnModeAirPlay.setBackgroundColor(Color.parseColor("#1DB954"));
            btnModeAirPlay.setTextColor(Color.parseColor("#000000"));

            tvActiveModeTitle.setText("Apple AirPlay 2 (iPhone / iPad / Mac)");
            tvActiveModeTitle.setTextColor(Color.parseColor("#FFFFFF"));
            tvActiveModeDetails.setText("Nome no AirPlay: DAC-HiFi-Audio");
            tvActiveModeDetails.setTextColor(Color.parseColor("#1DB954"));
            tvFormatDetails.setText("Qualidade: ALAC Lossless 44.1kHz • Decodificador Nativo C++");
        } else {
            btnModeBluetooth.setBackgroundColor(Color.parseColor("#1DB954"));
            btnModeBluetooth.setTextColor(Color.parseColor("#000000"));

            tvActiveModeTitle.setText("Bluetooth Audio (A2DP Receiver)");
            tvActiveModeTitle.setTextColor(Color.parseColor("#FFFFFF"));
            tvActiveModeDetails.setText("Nome Bluetooth: DAC-HiFi-Audio");
            tvActiveModeDetails.setTextColor(Color.parseColor("#1DB954"));
            tvFormatDetails.setText("Recepção de áudio sem fio para smartphones e tablets");
            btnMakeDiscoverable.setVisibility(View.VISIBLE);
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

        try {
            airPlayServer = new AirPlayServer(this, (isStreaming, clientIp, deviceName) -> {
                runOnUiThread(() -> refreshDevicesList());
            });
            airPlayServer.start();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Erro iniciando AirPlay: " + e.getMessage());
        }
    }

    private void refreshDevicesList() {
        layoutDevicesList.removeAllViews();
        boolean hasDevice = false;

        // 1. Verificar Dispositivo Wi-Fi conectado (PC)
        if (wifiAudioServer != null && wifiAudioServer.isStreaming()) {
            hasDevice = true;
            String clientIp = wifiAudioServer.getConnectedClientIp();

            LinearLayout row = createDeviceRow(
                    "💻 Computador Transmissor (PC)",
                    "IP: " + clientIp + " • Lossless PCM (1411 kbps)",
                    "PARAR",
                    v -> {
                        wifiAudioServer.disconnectActiveStream();
                        Toast.makeText(this, "Transmissão Wi-Fi interrompida!", Toast.LENGTH_SHORT).show();
                        refreshDevicesList();
                    }
            );
            layoutDevicesList.addView(row);
        }

        // 2. Verificar Dispositivo Apple AirPlay conectado (iPhone / iPad)
        if (airPlayServer != null && airPlayServer.isStreaming()) {
            hasDevice = true;
            String clientIp = airPlayServer.getConnectedClientIp();
            String devName = airPlayServer.getConnectedDeviceName();

            LinearLayout row = createDeviceRow(
                    "🍏 " + devName,
                    "IP: " + clientIp + " • Apple AirPlay Lossless ALAC",
                    "DESCONECTAR",
                    v -> {
                        airPlayServer.disconnect();
                        Toast.makeText(this, "AirPlay desconectado!", Toast.LENGTH_SHORT).show();
                        refreshDevicesList();
                    }
            );
            layoutDevicesList.addView(row);
        }

        // 3. Verificar Dispositivos Bluetooth Pareados/Conectados
        if (bluetoothSinkManager != null) {
            List<BluetoothDevice> bondedList = bluetoothSinkManager.getBondedDevices();
            for (BluetoothDevice dev : bondedList) {
                hasDevice = true;
                String name = dev.getName() != null ? dev.getName() : "Dispositivo Bluetooth";
                String address = dev.getAddress();

                LinearLayout row = createDeviceRow(
                        "📱 " + name,
                        "MAC: " + address + " • Bluetooth Pareado",
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
            String emptyMsg = (currentMode == MODE_WIFI) ? "Aguardando transmissão do Computador via Wi-Fi..." :
                    (currentMode == MODE_AIRPLAY) ? "Aguardando conexão do iPhone via AirPlay (Central de Controle)..." :
                            "Nenhum celular conectado via Bluetooth. Toque no botão abaixo para parear.";
            emptyTv.setText(emptyMsg);
            emptyTv.setTextColor(Color.parseColor("#727272"));
            emptyTv.setTextSize(12);
            emptyTv.setPadding(0, 8, 0, 8);
            layoutDevicesList.addView(emptyTv);
        }
    }

    private LinearLayout createDeviceRow(String title, String subtitle, String buttonText, View.OnClickListener onStop) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(12, 10, 12, 10);
        row.setBackgroundColor(Color.parseColor("#242424"));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, 8);
        row.setLayoutParams(rowParams);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        textLayout.setLayoutParams(textParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor("#FFFFFF"));
        tvTitle.setTextSize(13);
        tvTitle.setTypeface(null, Typeface.BOLD);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextColor(Color.parseColor("#1DB954"));
        tvSub.setTextSize(11);

        textLayout.addView(tvTitle);
        textLayout.addView(tvSub);

        Button btnStop = new Button(this);
        btnStop.setText(buttonText);
        btnStop.setTextColor(Color.parseColor("#FFFFFF"));
        btnStop.setBackgroundColor(Color.parseColor("#E50914"));
        btnStop.setTextSize(11);
        btnStop.setTypeface(null, Typeface.BOLD);
        btnStop.setPadding(16, 4, 16, 4);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(34)
        );
        btnStop.setLayoutParams(btnParams);
        btnStop.setOnClickListener(onStop);

        row.addView(textLayout);
        row.addView(btnStop);

        return row;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void setupTelemetryLoop() {
        telemetryRunnable = new Runnable() {
            @Override
            public void run() {
                updateTelemetry();
                uiHandler.postDelayed(this, 1000);
            }
        };
        uiHandler.post(telemetryRunnable);
    }

    private void updateTelemetry() {
        if (wifiAudioServer != null) {
            int volumePercent = wifiAudioServer.getCurrentAudioLevel();
            if (volumePercent > 100) volumePercent = 100;
            pbVuMeter.setProgress(volumePercent);
            tvVuVal.setText(volumePercent + " %");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (uiHandler != null && telemetryRunnable != null) {
            uiHandler.removeCallbacks(telemetryRunnable);
        }
        if (bluetoothSinkManager != null) {
            bluetoothSinkManager.release();
        }
        if (wifiAudioServer != null) {
            wifiAudioServer.stop();
        }
        if (airPlayServer != null) {
            airPlayServer.stop();
        }
    }
}
