package com.dachub.audio;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothSinkManager {
    private static final String TAG = "BluetoothSinkManager";
    private static final String ACTION_PAIRING_REQUEST = "android.bluetooth.device.action.PAIRING_REQUEST";

    public interface SinkStatusListener {
        void onStatusChanged(String status, String connectedDevice);
        void onDevicesUpdated();
    }

    private final Context context;
    private final SinkStatusListener listener;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothProfile a2dpSinkProfile;
    private AudioTrack audioTrack;
    private boolean isReceiverRegistered = false;

    public BluetoothSinkManager(Context context, SinkStatusListener listener) {
        this.context = context;
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        registerReceivers();
    }

    private synchronized AudioTrack getOrCreateAudioTrack() {
        if (audioTrack == null) {
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
                Log.i(TAG, "AudioTrack Bluetooth Lazy inicializado em 44.1kHz Stereo.");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao inicializar AudioTrack Bluetooth", e);
            }
        }
        return audioTrack;
    }

    public synchronized void releaseAudioTrack() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {}
            audioTrack = null;
            Log.i(TAG, "AudioTrack Bluetooth liberado.");
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
            Log.w(TAG, "Não foi possível definir nome do Bluetooth", e);
        }

        connectA2dpSinkProfile();
    }

    private void connectA2dpSinkProfile() {
        try {
            bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == 11) { // A2DP_SINK
                        a2dpSinkProfile = proxy;
                        Log.i(TAG, "Perfil A2DP Sink conectado com sucesso!");
                        if (listener != null) {
                            listener.onStatusChanged("Pronto (Sink Ativo)", null);
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    if (profile == 11) {
                        a2dpSinkProfile = null;
                        Log.i(TAG, "Perfil A2DP Sink desconectado.");
                        releaseAudioTrack();
                    }
                }
            }, 11);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter proxy A2DP Sink", e);
        }
    }

    public void makeDiscoverable() {
        if (bluetoothAdapter == null) return;
        try {
            if (!bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.enable();
            }
            bluetoothAdapter.setName("DAC-HiFi-Audio");
            Method setScanMode = BluetoothAdapter.class.getMethod("setScanMode", int.class, int.class);
            setScanMode.invoke(bluetoothAdapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, 300);
            Log.i(TAG, "Dispositivo configurado como visível por 300 segundos com nome DAC-HiFi-Audio.");
            if (listener != null) {
                listener.onStatusChanged("Visível (300s)", null);
            }
        } catch (Exception e) {
            Log.w(TAG, "Falha ao definir ScanMode via reflexão, enviando Intent", e);
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(discoverableIntent);
        }
    }

    public List<BluetoothDevice> getBondedDevices() {
        List<BluetoothDevice> list = new ArrayList<>();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            if (paired != null) {
                list.addAll(paired);
            }
        }
        return list;
    }

    public void unpairDevice(BluetoothDevice device) {
        if (device == null) return;
        try {
            Method removeBond = device.getClass().getMethod("removeBond");
            removeBond.invoke(device);
            Log.i(TAG, "Dispositivo desemparelhado: " + device.getName());
        } catch (Exception e) {
            Log.e(TAG, "Erro ao desemparelhar dispositivo", e);
        }
    }

    private void registerReceivers() {
        if (isReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(ACTION_PAIRING_REQUEST);
        filter.addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED");

        context.registerReceiver(bluetoothReceiver, filter);
        isReceiverRegistered = true;
    }

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (ACTION_PAIRING_REQUEST.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    Log.i(TAG, "Solicitação de pareamento automático aceita para: " + device.getName() + " (" + device.getAddress() + ")");
                    try {
                        Method setPairingConfirmation = device.getClass().getMethod("setPairingConfirmation", boolean.class);
                        setPairingConfirmation.invoke(device, true);
                    } catch (Exception ignored) {}
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String devName = device != null ? device.getName() : "Dispositivo";
                Log.i(TAG, "Estado de pareamento alterado: " + bondState + " para " + devName);

                if (bondState == BluetoothDevice.BOND_BONDED) {
                    if (listener != null) listener.onStatusChanged("Pareado com sucesso", devName);
                }
                if (listener != null) listener.onDevicesUpdated();
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    init();
                }
            } else if (action.contains("CONNECTION_STATE_CHANGED")) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String devName = device != null ? device.getName() : "Dispositivo";

                if (state == BluetoothProfile.STATE_CONNECTED) {
                    if (listener != null) listener.onStatusChanged("Conectado", devName);
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    if (listener != null) listener.onStatusChanged("Desconectado", null);
                    releaseAudioTrack();
                }
                if (listener != null) listener.onDevicesUpdated();
            } else if (action.contains("PLAYING_STATE_CHANGED")) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 11);
                if (state == 10) { // PLAYING
                    getOrCreateAudioTrack();
                    if (listener != null) listener.onStatusChanged("Reproduzindo", null);
                } else {
                    releaseAudioTrack();
                    if (listener != null) listener.onStatusChanged("Conectado", null);
                }
            }
        }
    };

    public void release() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver);
            } catch (Exception ignored) {}
            isReceiverRegistered = false;
        }
        if (a2dpSinkProfile != null && bluetoothAdapter != null) {
            bluetoothAdapter.closeProfileProxy(11, a2dpSinkProfile);
        }
        releaseAudioTrack();
    }
}
