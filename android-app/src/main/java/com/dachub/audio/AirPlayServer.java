package com.dachub.audio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import io.github.jqssun.airplay.bridge.NativeBridge;
import io.github.jqssun.airplay.bridge.RaopCallbackHandler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AirPlayServer implements RaopCallbackHandler {
    private static final String TAG = "AirPlayServer";
    private static final int DEFAULT_PORT = 5000;

    public interface AirPlayEventListener {
        void onStatusChanged(boolean isStreaming, String clientIp, String deviceName);
        void onMetadataReceived(String title, String artist, String album);
        void onCoverArtReceived(Bitmap coverArt);
        void onVolumeChanged(int volumePercent);
    }

    private final Context context;
    private final AirPlayEventListener listener;
    private AudioManager audioManager;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener raopRegListener;
    private NsdManager.RegistrationListener airplayRegListener;

    private long serverHandle = 0;
    private int boundPort = 5000;
    private boolean isRunning = false;
    private volatile boolean isStreaming = false;
    private volatile String connectedClientIp = "Nenhum";
    private volatile String connectedDeviceName = "Apple iPhone (AirPlay)";
    private volatile String currentTitle = "Aguardando faixa...";
    private volatile String currentArtist = "Apple AirPlay 2";
    private volatile String currentAlbum = "";
    private byte[] macBytes = new byte[]{(byte) 0xCE, (byte) 0x41, (byte) 0xF2, (byte) 0x46, (byte) 0x7D, (byte) 0x16};

    public AirPlayServer(Context context, AirPlayEventListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
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

    public String getCurrentTitle() {
        return currentTitle;
    }

    public String getCurrentArtist() {
        return currentArtist;
    }

    public synchronized void start() {
        if (isRunning) return;

        try {
            Log.i(TAG, "Configurando HAL de áudio Oboe C++ (Qualcomm 192 burst / 48000 Hz)...");
            int sampleRate = 48000;
            int framesPerBurst = 192;
            if (audioManager != null) {
                try {
                    String sr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
                    if (sr != null) sampleRate = Integer.parseInt(sr);
                    String fpb = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
                    if (fpb != null) framesPerBurst = Integer.parseInt(fpb);
                } catch (Exception ignored) {}

                int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0);
            }
            NativeBridge.nativeSetDefaultStreamValues(sampleRate, framesPerBurst);

            Log.i(TAG, "Iniciando receptor nativo C++ AirPlay 2 (libairplay_native.so)...");
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

            // Ativação explícita de Codecs ALAC e AAC
            NativeBridge.nativeSetH265Enabled(serverHandle, true);
            NativeBridge.nativeSetCodecs(serverHandle, true, true);
            NativeBridge.nativeSetHlsEnabled(serverHandle, true);
            NativeBridge.nativeSetLang(serverHandle, "pt-BR", "BR", "pt");
            NativeBridge.nativeSetAudioEnabled(serverHandle, true);
            NativeBridge.nativeSetPlist(serverHandle, "maxFPS", 60);
            NativeBridge.nativeSetPlist(serverHandle, "overscanned", 0);
            NativeBridge.nativeSetPlist(serverHandle, "audio_delay_micros", 0);
            NativeBridge.nativeSetDisplaySize(serverHandle, 720, 1280, 60);

            boundPort = NativeBridge.nativeStart(serverHandle, DEFAULT_PORT);
            if (boundPort <= 0) boundPort = DEFAULT_PORT;

            isRunning = true;
            registerMdnsServices();
            Log.i(TAG, "Motor nativo AirPlay 2 C++ pronto com ALAC/AAC ativos na porta: " + boundPort);
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

        if (audioManager != null) {
            try {
                audioManager.abandonAudioFocus(null);
            } catch (Exception ignored) {}
        }
    }

    public void disconnect() {
        if (serverHandle != 0) {
            try {
                NativeBridge.nativeServerAudioStop(serverHandle);
            } catch (Throwable ignored) {}
        }
        if (audioManager != null) {
            try {
                audioManager.abandonAudioFocus(null);
            } catch (Exception ignored) {}
        }
        isStreaming = false;
        connectedClientIp = "Nenhum";
        currentTitle = "Aguardando Áudio...";
        currentArtist = "AirPlay 2 • ALAC Hi-Fi Lossless";
        if (listener != null) {
            listener.onStatusChanged(false, "Nenhum", "");
            listener.onMetadataReceived(currentTitle, currentArtist, "");
            listener.onCoverArtReceived(null);
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
    public void onAudioFormat(int contentType, int samplesPerFrame, boolean isScreen) {
        String formatName = (contentType == 0) ? "ALAC Lossless" : (contentType == 1 ? "AAC-LC" : "AAC-ELD");
        Log.i(TAG, "Iniciando áudio nativo Oboe: " + formatName + " (spf=" + samplesPerFrame + ")");
        
        if (audioManager != null) {
            try {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            } catch (Exception ignored) {}
        }

        if (serverHandle != 0) {
            try {
                NativeBridge.nativeServerAudioStart(serverHandle);
                NativeBridge.nativeServerAudioFormat(serverHandle, contentType, samplesPerFrame);
            } catch (Throwable t) {
                Log.e(TAG, "Erro iniciando áudio nativo C++", t);
            }
        }

        isStreaming = true;
        connectedClientIp = "Apple iPhone";
        if (listener != null) {
            listener.onStatusChanged(true, connectedClientIp, connectedDeviceName);
        }
    }

    @Override
    public void onAudioTeardown() {
        Log.i(TAG, "Áudio AirPlay finalizado pelo iPhone (Teardown)");
        if (serverHandle != 0) {
            try {
                NativeBridge.nativeServerAudioStop(serverHandle);
            } catch (Throwable ignored) {}
        }
        disconnect();
    }

    @Override
    public float onClientVolume() {
        return 0.0f; // 0.0 dB = Volume máximo
    }

    @Override
    public void onConnectionInit() {
        Log.d(TAG, "Conexão AirPlay iniciada pelo iPhone!");
        isStreaming = true;
        if (listener != null) {
            listener.onStatusChanged(true, "Apple iPhone", "Apple iPhone (Spotify)");
        }
    }

    @Override
    public void onConnectionDestroy() {
        Log.d(TAG, "Conexão AirPlay finalizada pelo iPhone.");
        if (serverHandle != 0) {
            try {
                NativeBridge.nativeServerAudioStop(serverHandle);
            } catch (Throwable ignored) {}
        }
        disconnect();
    }

    @Override
    public void onConnectionReset(int code) {
        Log.d(TAG, "Conexão AirPlay reset: " + code);
        if (serverHandle != 0) {
            try {
                NativeBridge.nativeServerAudioStop(serverHandle);
            } catch (Throwable ignored) {}
        }
        disconnect();
    }

    @Override
    public void onCoverArt(byte[] data) {
        if (data != null && data.length > 0) {
            try {
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bmp != null && listener != null) {
                    listener.onCoverArtReceived(bmp);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Erro decodificando capa do álbum: " + t.getMessage());
            }
        }
    }

    @Override
    public void onMetadata(byte[] data) {
        if (data == null || data.length < 8) return;
        try {
            Map<String, String> tags = parseDmapMetadata(data);
            if (tags.containsKey("minm")) currentTitle = tags.get("minm");
            else if (tags.containsKey("title")) currentTitle = tags.get("title");

            if (tags.containsKey("asar")) currentArtist = tags.get("asar");
            else if (tags.containsKey("artist")) currentArtist = tags.get("artist");

            if (tags.containsKey("asal")) currentAlbum = tags.get("asal");
            else if (tags.containsKey("album")) currentAlbum = tags.get("album");

            if (listener != null) {
                listener.onMetadataReceived(currentTitle, currentArtist, currentAlbum);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Erro ao parsear metadados DMAP: " + t.getMessage());
        }
    }

    private Map<String, String> parseDmapMetadata(byte[] data) {
        Map<String, String> map = new HashMap<>();
        ByteBuffer buf = ByteBuffer.wrap(data);
        while (buf.remaining() >= 8) {
            byte[] codeBytes = new byte[4];
            buf.get(codeBytes);
            String code = new String(codeBytes, StandardCharsets.US_ASCII);
            int len = buf.getInt();
            if (len < 0 || len > buf.remaining()) break;
            byte[] valBytes = new byte[len];
            buf.get(valBytes);
            String val = new String(valBytes, StandardCharsets.UTF_8).trim();
            map.put(code, val);
        }
        return map;
    }

    @Override public void onDacpId(String dacpId, String activeRemote) {}
    @Override public void onDisplayPin(String pin) {}
    @Override public void onMirrorRunning(boolean running) {}
    @Override public void onProgress(long start, long current, long end) {}
    @Override public void onVideoData(byte[] data, long timestamp, boolean isKeyFrame) {}
    @Override public void onVideoPlay(String location, float position) {}
    @Override public void onVideoRate(float rate) {}
    @Override public void onVideoScrub(float position) {}
    @Override public void onVideoSessionPoll() {}
    @Override public void onVideoSize(float width, float height, float aspect1, float aspect2) {}
    @Override public void onVideoStop() {}

    @Override
    public void onVolumeChange(float volume) {
        Log.d(TAG, "Volume AirPlay recebido (dB): " + volume);
        int percent;
        if (volume <= -30.0f || volume <= -144.0f) {
            percent = 0;
        } else if (volume >= 0.0f) {
            percent = 100;
        } else {
            percent = (int) ((volume + 30.0f) / 30.0f * 100.0f);
        }

        if (audioManager != null) {
            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int targetVol = (percent * maxVol) / 100;
            if (percent > 0 && targetVol == 0) targetVol = 1;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0);
        }

        if (listener != null) {
            listener.onVolumeChanged(percent);
        }
    }
}
