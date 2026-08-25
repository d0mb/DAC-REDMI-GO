package com.dachub.audio;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import io.github.jqssun.airplay.bridge.NativeBridge;
import io.github.jqssun.airplay.bridge.RaopCallbackHandler;

import java.util.Map;

public class AirPlayServer implements RaopCallbackHandler {
    private static final String TAG = "AirPlayServer";
    private static final int DEFAULT_PORT = 5000;

    public interface AirPlayStatusListener {
        void onStatusChanged(boolean isStreaming, String clientIp, String deviceName);
    }

    private final Context context;
    private final AirPlayStatusListener listener;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener raopRegListener;
    private NsdManager.RegistrationListener airplayRegListener;

    private long serverHandle = 0;
    private int boundPort = 5000;
    private boolean isRunning = false;
    private volatile boolean isStreaming = false;
    private volatile String connectedClientIp = "Nenhum";
    private volatile String connectedDeviceName = "Apple iPhone (AirPlay)";
    private byte[] macBytes = new byte[]{(byte) 0xCE, (byte) 0x41, (byte) 0xF2, (byte) 0x46, (byte) 0x7D, (byte) 0x16};

    public AirPlayServer(Context context, AirPlayStatusListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        initMacAddress();
    }

    private void initMacAddress() {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && wifi.getConnectionInfo() != null) {
                String mac = wifi.getConnectionInfo().getMacAddress();
                if (mac != null && !mac.equals("02:00:00:00:00:00")) {
                    String[] parts = mac.split(":");
                    if (parts.length == 6) {
                        for (int i = 0; i < 6; i++) {
                            macBytes[i] = (byte) Integer.parseInt(parts[i], 16);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public String getConnectedClientIp() {
        return connectedClientIp;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public synchronized void start() {
        if (isRunning) return;

        try {
            Log.i(TAG, "Iniciando motor nativo C++ AirPlay 2 (libairplay_native.so)...");
            serverHandle = NativeBridge.nativeInit(
                    this,
                    macBytes,
                    "DAC-HiFi-Audio",
                    "",
                    true,  // audioOnly = true
                    false  // allowMirroring = false
            );

            if (serverHandle == 0) {
                Log.e(TAG, "Falha ao inicializar nativeInit do AirPlay");
                return;
            }

            NativeBridge.nativeSetAudioEnabled(serverHandle, true);
            boundPort = NativeBridge.nativeStart(serverHandle, DEFAULT_PORT);
            if (boundPort <= 0) {
                boundPort = DEFAULT_PORT;
            }

            NativeBridge.nativeServerAudioStart(serverHandle);
            isRunning = true;

            registerMdnsServices();
            Log.i(TAG, "Motor nativo AirPlay 2 C++ iniciado com sucesso na porta: " + boundPort);
        } catch (Throwable t) {
            Log.e(TAG, "Exceção iniciando motor nativo AirPlay 2: " + t.getMessage(), t);
        }
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        isStreaming = false;

        unregisterMdnsServices();

        if (serverHandle != 0) {
            try {
                NativeBridge.nativeServerAudioStop(serverHandle);
                NativeBridge.nativeStop(serverHandle);
                NativeBridge.nativeDestroy(serverHandle);
            } catch (Throwable t) {
                Log.e(TAG, "Erro parando motor nativo", t);
            }
            serverHandle = 0;
        }
    }

    public void disconnect() {
        isStreaming = false;
        connectedClientIp = "Nenhum";
        if (listener != null) {
            listener.onStatusChanged(false, "Nenhum", "");
        }
    }

    private void registerMdnsServices() {
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null || serverHandle == 0) return;

        try {
            // 1. Serviço RAOP de Áudio
            String raopServiceName = NativeBridge.nativeGetRaopServiceName(serverHandle);
            if (raopServiceName == null || raopServiceName.isEmpty()) {
                raopServiceName = "CE41F2467D16@DAC-HiFi-Audio";
            }

            Map<String, String> raopTxt = NativeBridge.nativeGetRaopTxtRecords(serverHandle);

            NsdServiceInfo raopInfo = new NsdServiceInfo();
            raopInfo.setServiceName(raopServiceName);
            raopInfo.setServiceType("_raop._tcp");
            raopInfo.setPort(boundPort);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && raopTxt != null) {
                for (Map.Entry<String, String> entry : raopTxt.entrySet()) {
                    raopInfo.setAttribute(entry.getKey(), entry.getValue());
                }
            }

            raopRegListener = new NsdManager.RegistrationListener() {
                @Override public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                    Log.i(TAG, "RAOP Nativo C++ registrado: " + nsdServiceInfo.getServiceName());
                }
                @Override public void onRegistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {
                    Log.w(TAG, "Falha registrando RAOP mDNS: " + i);
                }
                @Override public void onServiceUnregistered(NsdServiceInfo nsdServiceInfo) {}
                @Override public void onUnregistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {}
            };

            nsdManager.registerService(raopInfo, NsdManager.PROTOCOL_DNS_SD, raopRegListener);

            // 2. Serviço AirPlay Principal
            String airplayServerName = NativeBridge.nativeGetServerName(serverHandle);
            if (airplayServerName == null || airplayServerName.isEmpty()) {
                airplayServerName = "DAC-HiFi-Audio";
            }

            Map<String, String> airplayTxt = NativeBridge.nativeGetAirplayTxtRecords(serverHandle);

            NsdServiceInfo airplayInfo = new NsdServiceInfo();
            airplayInfo.setServiceName(airplayServerName);
            airplayInfo.setServiceType("_airplay._tcp");
            airplayInfo.setPort(boundPort);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && airplayTxt != null) {
                for (Map.Entry<String, String> entry : airplayTxt.entrySet()) {
                    airplayInfo.setAttribute(entry.getKey(), entry.getValue());
                }
            }

            airplayRegListener = new NsdManager.RegistrationListener() {
                @Override public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                    Log.i(TAG, "AirPlay Nativo C++ registrado: " + nsdServiceInfo.getServiceName());
                }
                @Override public void onRegistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {
                    Log.w(TAG, "Falha registrando AirPlay mDNS: " + i);
                }
                @Override public void onServiceUnregistered(NsdServiceInfo nsdServiceInfo) {}
                @Override public void onUnregistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {}
            };

            nsdManager.registerService(airplayInfo, NsdManager.PROTOCOL_DNS_SD, airplayRegListener);
        } catch (Throwable t) {
            Log.e(TAG, "Erro registrando serviços mDNS", t);
        }
    }

    private void unregisterMdnsServices() {
        if (nsdManager != null) {
            try {
                if (raopRegListener != null) nsdManager.unregisterService(raopRegListener);
                if (airplayRegListener != null) nsdManager.unregisterService(airplayRegListener);
            } catch (Exception ignored) {}
        }
    }

    // ========================================================
    // CALLBACKS NATIVOS C++ (RaopCallbackHandler)
    // ========================================================

    @Override
    public void onLog(String message) {
        Log.d("AirPlayNative", message);
    }

    @Override
    public void onAudioFormat(int sampleRate, int channels, boolean isFloat) {
        Log.i(TAG, "Áudio AirPlay ALAC iniciado: " + sampleRate + " Hz, " + channels + " canais, float=" + isFloat);
        isStreaming = true;
        connectedClientIp = "Apple Device";
        if (listener != null) {
            listener.onStatusChanged(true, connectedClientIp, connectedDeviceName);
        }
    }

    @Override
    public void onAudioTeardown() {
        Log.i(TAG, "Áudio AirPlay finalizado (Teardown)");
        isStreaming = false;
        connectedClientIp = "Nenhum";
        if (listener != null) {
            listener.onStatusChanged(false, "Nenhum", "");
        }
    }

    @Override
    public float onClientVolume() {
        return 1.0f;
    }

    @Override
    public void onConnectionInit() {
        Log.d(TAG, "Conexão AirPlay iniciada pelo iPhone!");
    }

    @Override
    public void onConnectionDestroy() {
        Log.d(TAG, "Conexão AirPlay encerrada.");
        isStreaming = false;
        if (listener != null) {
            listener.onStatusChanged(false, "Nenhum", "");
        }
    }

    @Override public void onConnectionReset(int code) { Log.d(TAG, "Conexão reset: " + code); }
    @Override public void onCoverArt(byte[] data) { Log.d(TAG, "Capa do álbum recebida (" + (data != null ? data.length : 0) + " bytes)"); }
    @Override public void onDacpId(String dacpId, String activeRemote) {}
    @Override public void onDisplayPin(String pin) { Log.i(TAG, "PIN AirPlay: " + pin); }
    @Override public void onMetadata(byte[] data) { Log.d(TAG, "Metadados de faixa recebidos."); }
    @Override public void onMirrorRunning(boolean running) {}
    @Override public void onProgress(long start, long current, long end) {}
    @Override public void onVideoData(byte[] data, long timestamp, boolean isKeyFrame) {}
    @Override public void onVideoPlay(String location, float position) {}
    @Override public void onVideoRate(float rate) {}
    @Override public void onVideoScrub(float position) {}
    @Override public void onVideoSessionPoll() {}
    @Override public void onVideoSize(float width, float height, float aspect1, float aspect2) {}
    @Override public void onVideoStop() {}
    @Override public void onVolumeChange(float volume) { Log.d(TAG, "Volume alterado pelo iPhone: " + volume); }
}
