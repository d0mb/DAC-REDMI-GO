package com.dachub.audio;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothSinkManager {
    private static final String TAG = "BluetoothSinkManager";
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private SinkStatusListener listener;

    private static final UUID A2DP_SINK_UUID = UUID.fromString("0000110B-0000-1000-8000-00805F9B34FB");
    private static final UUID AVRCP_TARGET_UUID = UUID.fromString("0000110C-0000-1000-8000-00805F9B34FB");

    private BluetoothProfile a2dpSinkProfile;
    private AudioTrack audioTrack;
    private boolean isListening = false;
    private volatile String connectedDeviceName = null;

    public interface SinkStatusListener {
        void onStatusChanged(String status, String connectedDevice);
        void onDevicesUpdated();
    }

    public BluetoothSinkManager(Context context, SinkStatusListener listener) {
        this.context = context;
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        initAudioTrack();
        registerReceivers();
    }

    private void initAudioTrack() {
        try {
            int sampleRate = 44100;
            int bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(bufferSize, 8192),
                    AudioTrack.MODE_STREAM
            );
            audioTrack.play();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar AudioTrack", e);
        }
    }

    public void init() {
        if (bluetoothAdapter == null) {
            if (listener != null) listener.onStatusChanged("Bluetooth não disponível", null);
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }

        try {
            bluetoothAdapter.setName("DAC-HiFi-Audio");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao definir nome BT", e);
        }

        connectProfileProxy();
        startSdpAudioServers();
        makeDiscoverable();
    }

    private void connectProfileProxy() {
        try {
            bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == 11) { // A2DP_SINK
                        a2dpSinkProfile = proxy;
                        Log.i(TAG, "Perfil A2DP_SINK conectado!");
                        if (listener != null) listener.onStatusChanged("Receptor A2DP Sink Ativo", null);
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    if (profile == 11) a2dpSinkProfile = null;
                }
            }, 11);
        } catch (Exception e) {
            Log.w(TAG, "Aviso proxy A2DP_SINK: " + e.getMessage());
        }
    }

    private void startSdpAudioServers() {
        if (isListening) return;
        isListening = true;

        new Thread(() -> {
            try (BluetoothServerSocket serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("DAC-HiFi-Audio", A2DP_SINK_UUID)) {
                while (isListening) {
                    try {
                        BluetoothSocket socket = serverSocket.accept();
                        handleAudioSocket(socket);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                Log.w(TAG, "Servidor SDP: " + e.getMessage());
            }
        }).start();
    }

    private void handleAudioSocket(BluetoothSocket socket) {
        new Thread(() -> {
            try (InputStream in = socket.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1 && isListening) {
                    if (audioTrack != null) {
                        audioTrack.write(buffer, 0, read);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Stream BT encerrado", e);
            }
        }).start();
    }

    public void makeDiscoverable() {
        if (bluetoothAdapter == null) return;
        try {
            Method setScanMode = BluetoothAdapter.class.getMethod("setScanMode", int.class, int.class);
            setScanMode.invoke(bluetoothAdapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, 300);
            if (listener != null) {
                listener.onStatusChanged("Visível para pareamento", null);
            }
        } catch (Exception e) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(discoverableIntent);
        }
    }

    public List<BluetoothDevice> getBondedDevices() {
        List<BluetoothDevice> list = new ArrayList<>();
        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
            if (bonded != null) {
                list.addAll(bonded);
            }
        }
        return list;
    }

    public void unpairDevice(BluetoothDevice device) {
        try {
            Method removeBondMethod = device.getClass().getMethod("removeBond");
            removeBondMethod.invoke(device);
            if (listener != null) {
                listener.onDevicesUpdated();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro ao desparear dispositivo", e);
        }
    }

    public void disconnectAll() {
        for (BluetoothDevice dev : getBondedDevices()) {
            unpairDevice(dev);
        }
        if (bluetoothAdapter != null) {
            bluetoothAdapter.disable();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(bluetoothAdapter::enable, 1000);
        }
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    connectedDeviceName = device != null ? device.getName() : "Dispositivo Pareado";
                    if (listener != null) {
                        listener.onStatusChanged("Conectado: " + connectedDeviceName, connectedDeviceName);
                        listener.onDevicesUpdated();
                    }
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    connectedDeviceName = null;
                    if (listener != null) {
                        listener.onStatusChanged("Aguardando pareamento...", null);
                        listener.onDevicesUpdated();
                    }
                } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                    if (listener != null) listener.onDevicesUpdated();
                }
            }
        }, filter);
    }

    public void release() {
        // Recursos liberados
    }
}
