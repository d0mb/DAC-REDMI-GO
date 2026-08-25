package com.dachub.audio;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    public static final int TAB_WIFI = 0;
    public static final int TAB_AIRPLAY = 1;
    public static final int TAB_BLUETOOTH = 2;
    public static final int TAB_SETTINGS = 3;

    private int currentTab = TAB_WIFI;

    // Header & Status
    private TextView tvOnlineBadge;

    // Now Playing Card (Global Spotify Player View)
    private ImageView ivAlbumArt;
    private TextView tvTrackTitle;
    private TextView tvTrackArtist;
    private TextView tvAudioFormatInfo;
    private TextView tvVuVal;
    private ProgressBar pbGlobalVuMeter;

    // Containers de Abas
    private LinearLayout layoutTabWifi;
    private LinearLayout layoutTabAirplay;
    private LinearLayout layoutTabBluetooth;
    private LinearLayout layoutTabSettings;

    // Footer Navigation Items
    private LinearLayout tabNavWifi;
    private LinearLayout tabNavAirplay;
    private LinearLayout tabNavBluetooth;
    private LinearLayout tabNavSettings;

    private TextView tvNavWifi;
    private TextView tvNavAirplay;
    private TextView tvNavBluetooth;
    private TextView tvNavSettings;

    // Wi-Fi Controls
    private TextView tvWifiIpAddress;
    private LinearLayout layoutWifiDevicesList;

    // AirPlay Controls
    private LinearLayout layoutAirplayDevicesList;

    // Bluetooth Controls
    private Button btnMakeDiscoverable;
    private LinearLayout layoutBluetoothDevicesList;

    // Settings & Volume Controls
    private Button btnCleanSystem;
    private TextView tvVolumeVal;
    private SeekBar sbVolume;
    private Button btnTestTone;

    // Audio Managers & Servers
    private AudioManager audioManager;
    private BluetoothSinkManager bluetoothSinkManager;
    private WifiAudioServer wifiAudioServer;
    private AirPlayServer airPlayServer;
    private Handler uiHandler;
    private static final int[] VU_PULSE = new int[]{75, 82, 88, 92, 85, 80, 86, 78};
    private int lastReportedLevel = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        uiHandler = new Handler(Looper.getMainLooper());

        initViews();
        setupAudioService();
        setupVolumeControls();
        switchTab(TAB_WIFI);
        setupTelemetryLoop();
    }

    private void initViews() {
        tvOnlineBadge = findViewById(R.id.tvOnlineBadge);

        ivAlbumArt = findViewById(R.id.ivAlbumArt);
        tvTrackTitle = findViewById(R.id.tvTrackTitle);
        tvTrackArtist = findViewById(R.id.tvTrackArtist);
        tvAudioFormatInfo = findViewById(R.id.tvAudioFormatInfo);
        tvVuVal = findViewById(R.id.tvVuVal);
        pbGlobalVuMeter = findViewById(R.id.pbGlobalVuMeter);

        layoutTabWifi = findViewById(R.id.layoutTabWifi);
        layoutTabAirplay = findViewById(R.id.layoutTabAirplay);
        layoutTabBluetooth = findViewById(R.id.layoutTabBluetooth);
        layoutTabSettings = findViewById(R.id.layoutTabSettings);

        tabNavWifi = findViewById(R.id.tabNavWifi);
        tabNavAirplay = findViewById(R.id.tabNavAirplay);
        tabNavBluetooth = findViewById(R.id.tabNavBluetooth);
        tabNavSettings = findViewById(R.id.tabNavSettings);

        tvNavWifi = findViewById(R.id.tvNavWifi);
        tvNavAirplay = findViewById(R.id.tvNavAirplay);
        tvNavBluetooth = findViewById(R.id.tvNavBluetooth);
        tvNavSettings = findViewById(R.id.tvNavSettings);

        tvWifiIpAddress = findViewById(R.id.tvWifiIpAddress);
        layoutWifiDevicesList = findViewById(R.id.layoutWifiDevicesList);

        layoutAirplayDevicesList = findViewById(R.id.layoutAirplayDevicesList);

        btnMakeDiscoverable = findViewById(R.id.btnMakeDiscoverable);
        layoutBluetoothDevicesList = findViewById(R.id.layoutBluetoothDevicesList);

        btnCleanSystem = findViewById(R.id.btnCleanSystem);
        tvVolumeVal = findViewById(R.id.tvVolumeVal);
        sbVolume = findViewById(R.id.sbVolume);
        btnTestTone = findViewById(R.id.btnTestTone);

        // Listeners do Footer
        tabNavWifi.setOnClickListener(v -> switchTab(TAB_WIFI));
        tabNavAirplay.setOnClickListener(v -> switchTab(TAB_AIRPLAY));
        tabNavBluetooth.setOnClickListener(v -> switchTab(TAB_BLUETOOTH));
        tabNavSettings.setOnClickListener(v -> switchTab(TAB_SETTINGS));

        btnCleanSystem.setOnClickListener(v -> performSystemCleanup());

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

    private void switchTab(int tab) {
        currentTab = tab;

        // Ocultar todas as seções
        layoutTabWifi.setVisibility(View.GONE);
        layoutTabAirplay.setVisibility(View.GONE);
        layoutTabBluetooth.setVisibility(View.GONE);
        layoutTabSettings.setVisibility(View.GONE);

        // Resetar cores do Footer
        tvNavWifi.setTextColor(Color.parseColor("#B3B3B3"));
        tvNavWifi.setTypeface(null, Typeface.NORMAL);
        tvNavAirplay.setTextColor(Color.parseColor("#B3B3B3"));
        tvNavAirplay.setTypeface(null, Typeface.NORMAL);
        tvNavBluetooth.setTextColor(Color.parseColor("#B3B3B3"));
        tvNavBluetooth.setTypeface(null, Typeface.NORMAL);
        tvNavSettings.setTextColor(Color.parseColor("#B3B3B3"));
        tvNavSettings.setTypeface(null, Typeface.NORMAL);

        if (tab == TAB_WIFI) {
            layoutTabWifi.setVisibility(View.VISIBLE);
            tvNavWifi.setTextColor(Color.parseColor("#1ED760"));
            tvNavWifi.setTypeface(null, Typeface.BOLD);
            if (wifiAudioServer != null) {
                tvWifiIpAddress.setText("IP: http://" + wifiAudioServer.getIpAddress() + ":8080");
            }
        } else if (tab == TAB_AIRPLAY) {
            layoutTabAirplay.setVisibility(View.VISIBLE);
            tvNavAirplay.setTextColor(Color.parseColor("#1ED760"));
            tvNavAirplay.setTypeface(null, Typeface.BOLD);
        } else if (tab == TAB_BLUETOOTH) {
            layoutTabBluetooth.setVisibility(View.VISIBLE);
            tvNavBluetooth.setTextColor(Color.parseColor("#1ED760"));
            tvNavBluetooth.setTypeface(null, Typeface.BOLD);
        } else if (tab == TAB_SETTINGS) {
            layoutTabSettings.setVisibility(View.VISIBLE);
            tvNavSettings.setTextColor(Color.parseColor("#1ED760"));
            tvNavSettings.setTypeface(null, Typeface.BOLD);
        }

        refreshAllDeviceLists();
    }

    private void performSystemCleanup() {
        Toast.makeText(this, "Iniciando limpeza e liberação de portas...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                if (wifiAudioServer != null) {
                    wifiAudioServer.disconnectActiveStream();
                    wifiAudioServer.stop();
                }
                if (airPlayServer != null) {
                    airPlayServer.disconnect();
                    airPlayServer.stop();
                }

                Thread.sleep(500);

                // Reiniciar servidores limpos
                wifiAudioServer = WifiAudioServer.getInstance(this);
                wifiAudioServer.start();

                airPlayServer = new AirPlayServer(this, createAirPlayListener());
                airPlayServer.start();

                System.gc();

                runOnUiThread(() -> {
                    Toast.makeText(this, "✨ Limpeza concluída! Portas 5000, 7000 e 8080 liberadas com sucesso!", Toast.LENGTH_LONG).show();
                    refreshAllDeviceLists();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Erro na limpeza: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
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

    private AirPlayServer.AirPlayEventListener createAirPlayListener() {
        return new AirPlayServer.AirPlayEventListener() {
            @Override
            public void onStatusChanged(boolean isStreaming, String clientIp, String deviceName) {
                runOnUiThread(() -> {
                    refreshAllDeviceLists();
                    if (isStreaming) {
                        tvTrackArtist.setText(deviceName != null && !deviceName.isEmpty() ? deviceName : "Apple iPhone (Spotify)");
                        tvAudioFormatInfo.setText("ALAC 16-bit 44.1kHz • Lossless Hi-Fi");
                    } else {
                        tvTrackTitle.setText("Aguardando Áudio...");
                        tvTrackArtist.setText("AirPlay 2 • ALAC Hi-Fi Lossless");
                        tvAudioFormatInfo.setText("PCM 16-bit 44.1kHz • 1411 kbps");
                        ivAlbumArt.setImageResource(android.R.drawable.ic_media_play);
                    }
                });
            }

            @Override
            public void onMetadataReceived(String title, String artist, String album) {
                runOnUiThread(() -> {
                    if (title != null && !title.isEmpty()) tvTrackTitle.setText(title);
                    if (artist != null && !artist.isEmpty()) {
                        tvTrackArtist.setText(artist + (album != null && !album.isEmpty() ? " • " + album : ""));
                    }
                    tvAudioFormatInfo.setText("ALAC 16-bit 44.1kHz • Lossless Hi-Fi");
                });
            }

            @Override
            public void onCoverArtReceived(Bitmap coverArt) {
                runOnUiThread(() -> {
                    if (coverArt != null) {
                        ivAlbumArt.setImageBitmap(coverArt);
                    }
                });
            }

            @Override
            public void onVolumeChanged(int volumePercent) {
                runOnUiThread(() -> {
                    if (audioManager != null) {
                        int cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        sbVolume.setProgress(cur);
                        updateVolumeLabel(cur, max);
                    }
                });
            }
        };
    }

    private void setupAudioService() {
        Intent serviceIntent = new Intent(this, DacAudioService.class);
        startService(serviceIntent);

        bluetoothSinkManager = new BluetoothSinkManager(this, new BluetoothSinkManager.SinkStatusListener() {
            @Override
            public void onStatusChanged(String status, String connectedDevice) {
                runOnUiThread(() -> refreshAllDeviceLists());
            }

            @Override
            public void onDevicesUpdated() {
                runOnUiThread(() -> refreshAllDeviceLists());
            }
        });
        bluetoothSinkManager.init();

        wifiAudioServer = WifiAudioServer.getInstance(this);
        wifiAudioServer.start();

        try {
            airPlayServer = new AirPlayServer(this, createAirPlayListener());
            airPlayServer.start();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Erro iniciando AirPlay: " + e.getMessage());
        }
    }

    private void refreshAllDeviceLists() {
        // 1. Atualizar Dispositivos Wi-Fi (PC)
        layoutWifiDevicesList.removeAllViews();
        if (wifiAudioServer != null && wifiAudioServer.isStreaming()) {
            String clientIp = wifiAudioServer.getConnectedClientIp();
            LinearLayout row = createDeviceRow(
                    "💻 Computador Transmissor (PC)",
                    "IP: " + clientIp + " • Lossless PCM (1411 kbps)",
                    "PARAR",
                    v -> {
                        wifiAudioServer.disconnectActiveStream();
                        Toast.makeText(this, "Transmissão Wi-Fi interrompida!", Toast.LENGTH_SHORT).show();
                        refreshAllDeviceLists();
                    }
            );
            layoutWifiDevicesList.addView(row);
        } else {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("Aguardando transmissão do computador via Wi-Fi...");
            emptyTv.setTextColor(Color.parseColor("#727272"));
            emptyTv.setTextSize(12);
            emptyTv.setPadding(0, 8, 0, 8);
            layoutWifiDevicesList.addView(emptyTv);
        }

        // 2. Atualizar Dispositivos AirPlay (Apple)
        layoutAirplayDevicesList.removeAllViews();
        if (airPlayServer != null && airPlayServer.isStreaming()) {
            String clientIp = airPlayServer.getConnectedClientIp();
            String devName = airPlayServer.getConnectedDeviceName();
            LinearLayout row = createDeviceRow(
                    "🍏 " + devName,
                    "IP: " + clientIp + " • Apple AirPlay Lossless ALAC",
                    "DESCONECTAR",
                    v -> {
                        airPlayServer.disconnect();
                        Toast.makeText(this, "AirPlay desconectado!", Toast.LENGTH_SHORT).show();
                        refreshAllDeviceLists();
                    }
            );
            layoutAirplayDevicesList.addView(row);
        } else {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("Nenhum iPhone transmitindo no momento. Abra a Central de Controle para conectar.");
            emptyTv.setTextColor(Color.parseColor("#727272"));
            emptyTv.setTextSize(12);
            emptyTv.setPadding(0, 8, 0, 8);
            layoutAirplayDevicesList.addView(emptyTv);
        }

        // 3. Atualizar Dispositivos Bluetooth
        layoutBluetoothDevicesList.removeAllViews();
        boolean hasBt = false;
        if (bluetoothSinkManager != null) {
            List<BluetoothDevice> bondedList = bluetoothSinkManager.getBondedDevices();
            for (BluetoothDevice dev : bondedList) {
                hasBt = true;
                String name = dev.getName() != null ? dev.getName() : "Dispositivo Bluetooth";
                String address = dev.getAddress();
                LinearLayout row = createDeviceRow(
                        "📱 " + name,
                        "MAC: " + address + " • Bluetooth Pareado",
                        "DESCONECTAR",
                        v -> {
                            bluetoothSinkManager.unpairDevice(dev);
                            Toast.makeText(this, "Dispositivo " + name + " desconectado!", Toast.LENGTH_SHORT).show();
                            refreshAllDeviceLists();
                        }
                );
                layoutBluetoothDevicesList.addView(row);
            }
        }
        if (!hasBt) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("Nenhum dispositivo Bluetooth pareado no momento.");
            emptyTv.setTextColor(Color.parseColor("#727272"));
            emptyTv.setTextSize(12);
            emptyTv.setPadding(0, 8, 0, 8);
            layoutBluetoothDevicesList.addView(emptyTv);
        }

        // Atualizar Badge superior
        if (tvOnlineBadge != null) {
            boolean active = (wifiAudioServer != null && wifiAudioServer.isStreaming()) || (airPlayServer != null && airPlayServer.isStreaming());
            if (active) {
                tvOnlineBadge.setText("🟢 TOCANDO");
                tvOnlineBadge.setTextColor(Color.parseColor("#1ED760"));
            } else {
                tvOnlineBadge.setText("🟢 PRONTO");
                tvOnlineBadge.setTextColor(Color.parseColor("#1ED760"));
            }
        }
    }

    private LinearLayout createDeviceRow(String title, String subtitle, String buttonText, View.OnClickListener onStop) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(16, 12, 16, 12);
        row.setBackgroundResource(R.drawable.bg_device_tile);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, 10);
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
        tvSub.setTextColor(Color.parseColor("#1ED760"));
        tvSub.setTextSize(11);

        textLayout.addView(tvTitle);
        textLayout.addView(tvSub);

        Button btnStop = new Button(this);
        btnStop.setText(buttonText);
        btnStop.setTextColor(Color.parseColor("#FF4D4D"));
        btnStop.setBackgroundResource(R.drawable.bg_button_stop);
        btnStop.setTextSize(11);
        btnStop.setTypeface(null, Typeface.BOLD);
        btnStop.setPadding(20, 6, 20, 6);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(36)
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

    private int vuSimStep = 0;
    private final Runnable telemetryRunnable = new Runnable() {
        @Override
        public void run() {
            updateTelemetry();
            if (uiHandler != null) {
                uiHandler.postDelayed(this, 1000);
            }
        }
    };

    private void setupTelemetryLoop() {
        uiHandler.post(telemetryRunnable);
    }

    private void updateTelemetry() {
        int level = 0;
        if (wifiAudioServer != null && wifiAudioServer.isStreaming()) {
            level = wifiAudioServer.getCurrentAudioLevel();
        } else if (airPlayServer != null && airPlayServer.isStreaming()) {
            vuSimStep = (vuSimStep + 1) % VU_PULSE.length;
            level = VU_PULSE[vuSimStep];
        }

        if (level != lastReportedLevel) {
            lastReportedLevel = level;
            if (pbGlobalVuMeter != null) pbGlobalVuMeter.setProgress(level);
            if (tvVuVal != null) tvVuVal.setText(level + "%");
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
