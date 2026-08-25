package com.dachub.audio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AirPlayServer {
    private static final String TAG = "AirPlayServer";
    private static final int RTSP_PORT = 5000;
    private static final int AIRPLAY_PORT = 7000;
    private static final int AUDIO_PORT = 6000;
    private static final int CONTROL_PORT = 6001;
    private static final int TIMING_PORT = 6002;

    public interface AirPlayStatusListener {
        void onStatusChanged(boolean isStreaming, String clientIp, String deviceName);
    }

    private final Context context;
    private final AirPlayStatusListener listener;
    private NsdManager nsdManager;
    private NsdManager.RegistrationListener raopRegistrationListener;
    private NsdManager.RegistrationListener airplayRegistrationListener;
    
    private ServerSocket rtspServer;
    private ServerSocket airplayHttpServer;
    private DatagramSocket audioSocket;
    private AudioTrack audioTrack;
    private boolean isRunning = false;
    private volatile boolean isStreaming = false;
    private volatile String connectedClientIp = "Nenhum";
    private volatile String connectedDeviceName = "Apple Device (AirPlay)";
    private String macAddress = "CE41F2467D16";

    public AirPlayServer(Context context, AirPlayStatusListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        initMacAddress();
        initAudioTrack();
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

    public void disconnect() {
        isStreaming = false;
        connectedClientIp = "Nenhum";
        if (listener != null) {
            listener.onStatusChanged(false, "Nenhum", "");
        }
        initAudioTrack();
    }

    private void initMacAddress() {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && wifi.getConnectionInfo() != null) {
                String mac = wifi.getConnectionInfo().getMacAddress();
                if (mac != null && !mac.equals("02:00:00:00:00:00")) {
                    macAddress = mac.replace(":", "").toUpperCase();
                }
            }
        } catch (Exception ignored) {}
    }

    private void initAudioTrack() {
        try {
            if (audioTrack != null) {
                try { audioTrack.stop(); audioTrack.release(); } catch (Exception ignored) {}
            }
            int minBufferSize = AudioTrack.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int bufferSize = Math.max(minBufferSize, 32768);

            audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    44100,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
            );
            audioTrack.play();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao inicializar AudioTrack: " + e.getMessage());
        }
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;

        startRtspServer();
        startAirPlayHttpServer();
        startUdpAudioReceiver();
        registerNsdServices();
        Log.i(TAG, "Receptor AirPlay iniciado para iPhone/iPad!");
    }

    public synchronized void stop() {
        isRunning = false;
        isStreaming = false;
        unregisterNsdServices();
        try {
            if (rtspServer != null) rtspServer.close();
            if (airplayHttpServer != null) airplayHttpServer.close();
            if (audioSocket != null) audioSocket.close();
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
            }
        } catch (Exception ignored) {}
    }

    private void registerNsdServices() {
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) return;

        // 1. Serviço RAOP (Áudio AirPlay) -> Formato: MAC@Nome
        NsdServiceInfo raopService = new NsdServiceInfo();
        raopService.setServiceName(macAddress + "@DAC-HiFi-Audio");
        raopService.setServiceType("_raop._tcp");
        raopService.setPort(RTSP_PORT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            raopService.setAttribute("txtvers", "1");
            raopService.setAttribute("ch", "2");
            raopService.setAttribute("cn", "0,1");
            raopService.setAttribute("et", "0");
            raopService.setAttribute("sv", "false");
            raopService.setAttribute("da", "true");
            raopService.setAttribute("sr", "44100");
            raopService.setAttribute("ss", "16");
            raopService.setAttribute("pw", "false");
            raopService.setAttribute("vn", "65537");
            raopService.setAttribute("tp", "UDP");
            raopService.setAttribute("md", "0,1,2");
            raopService.setAttribute("am", "AirPort4,107");
            raopService.setAttribute("sf", "0x4");
        }

        raopRegistrationListener = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo info) { Log.i(TAG, "RAOP registrado: " + info.getServiceName()); }
            @Override public void onRegistrationFailed(NsdServiceInfo info, int errorCode) { Log.w(TAG, "Falha RAOP: " + errorCode); }
            @Override public void onServiceUnregistered(NsdServiceInfo info) {}
            @Override public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {}
        };

        try {
            nsdManager.registerService(raopService, NsdManager.PROTOCOL_DNS_SD, raopRegistrationListener);
        } catch (Exception e) {
            Log.e(TAG, "Erro registrando RAOP mDNS", e);
        }

        // 2. Serviço AirPlay
        NsdServiceInfo airplayService = new NsdServiceInfo();
        airplayService.setServiceName("DAC-HiFi-Audio");
        airplayService.setServiceType("_airplay._tcp");
        airplayService.setPort(AIRPLAY_PORT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            airplayService.setAttribute("features", "0x5A7FFFF7,0x1E");
            airplayService.setAttribute("flags", "0x4");
            airplayService.setAttribute("model", "AppleTV3,2");
            airplayService.setAttribute("srcvers", "220.68");
            airplayService.setAttribute("deviceid", macAddress);
        }

        airplayRegistrationListener = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo info) { Log.i(TAG, "AirPlay registrado: " + info.getServiceName()); }
            @Override public void onRegistrationFailed(NsdServiceInfo info, int errorCode) { Log.w(TAG, "Falha AirPlay: " + errorCode); }
            @Override public void onServiceUnregistered(NsdServiceInfo info) {}
            @Override public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {}
        };

        try {
            nsdManager.registerService(airplayService, NsdManager.PROTOCOL_DNS_SD, airplayRegistrationListener);
        } catch (Exception e) {
            Log.e(TAG, "Erro registrando AirPlay mDNS", e);
        }
    }

    private void unregisterNsdServices() {
        if (nsdManager != null) {
            try {
                if (raopRegistrationListener != null) nsdManager.unregisterService(raopRegistrationListener);
                if (airplayRegistrationListener != null) nsdManager.unregisterService(airplayRegistrationListener);
            } catch (Exception ignored) {}
        }
    }

    private void startAirPlayHttpServer() {
        new Thread(() -> {
            try {
                airplayHttpServer = new ServerSocket(AIRPLAY_PORT);
                while (isRunning) {
                    Socket client = airplayHttpServer.accept();
                    handleHttpClient(client);
                }
            } catch (Exception e) {
                if (isRunning) Log.e(TAG, "Erro no servidor HTTP AirPlay", e);
            }
        }).start();
    }

    private void handleHttpClient(Socket client) {
        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 OutputStream out = client.getOutputStream()) {

                String line = in.readLine();
                if (line == null) return;
                String[] parts = line.split(" ");
                String method = parts[0];
                String path = parts.length > 1 ? parts[1] : "/";

                while ((line = in.readLine()) != null && !line.isEmpty()) {}

                Log.d(TAG, "HTTP AirPlay " + method + " " + path);

                if ("/info".equalsIgnoreCase(path) || "/server-info".equalsIgnoreCase(path)) {
                    String xmlInfo = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\r\n" +
                            "<plist version=\"1.0\">\r\n<dict>\r\n" +
                            "<key>deviceid</key><string>" + macAddress + "</string>\r\n" +
                            "<key>features</key><integer>14847</integer>\r\n" +
                            "<key>model</key><string>AppleTV3,2</string>\r\n" +
                            "<key>protovers</key><string>1.0</string>\r\n" +
                            "<key>srcvers</key><string>220.68</string>\r\n" +
                            "<key>statusFlags</key><integer>4</integer>\r\n" +
                            "</dict>\r\n</plist>\r\n";
                    byte[] data = xmlInfo.getBytes(StandardCharsets.UTF_8);

                    String resp = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/x-apple-plist+xml\r\n" +
                            "Content-Length: " + data.length + "\r\n" +
                            "Server: AirTunes/220.68\r\n\r\n";
                    out.write(resp.getBytes(StandardCharsets.UTF_8));
                    out.write(data);
                } else if ("/pair-setup".equalsIgnoreCase(path) || "/pair-verify".equalsIgnoreCase(path)) {
                    byte[] dummyKey = new byte[32];
                    String resp = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/octet-stream\r\n" +
                            "Content-Length: " + dummyKey.length + "\r\n" +
                            "Server: AirTunes/220.68\r\n\r\n";
                    out.write(resp.getBytes(StandardCharsets.UTF_8));
                    out.write(dummyKey);
                } else {
                    String resp = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n";
                    out.write(resp.getBytes(StandardCharsets.UTF_8));
                }
                out.flush();
            } catch (Exception ignored) {}
        }).start();
    }

    private void startRtspServer() {
        new Thread(() -> {
            try {
                rtspServer = new ServerSocket(RTSP_PORT);
                while (isRunning) {
                    Socket client = rtspServer.accept();
                    handleRtspClient(client);
                }
            } catch (Exception e) {
                if (isRunning) Log.e(TAG, "Erro no servidor RTSP", e);
            }
        }).start();
    }

    private void handleRtspClient(Socket client) {
        new Thread(() -> {
            String clientIp = client.getInetAddress().getHostAddress();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 OutputStream out = client.getOutputStream()) {

                String line;
                String cseq = "1";
                while ((line = in.readLine()) != null && isRunning) {
                    if (line.isEmpty()) continue;
                    String[] parts = line.split(" ");
                    String method = parts[0];

                    Map<String, String> headers = new HashMap<>();
                    String headerLine;
                    int contentLength = 0;
                    while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                        int idx = headerLine.indexOf(":");
                        if (idx > 0) {
                            String k = headerLine.substring(0, idx).trim().toLowerCase();
                            String v = headerLine.substring(idx + 1).trim();
                            headers.put(k, v);
                            if (k.equals("cseq")) cseq = v;
                            if (k.equals("user-agent")) {
                                connectedDeviceName = v.contains("AirPlay") ? "Apple iPhone / iPad" : v;
                            }
                            if (k.equals("content-length")) {
                                try { contentLength = Integer.parseInt(v); } catch (Exception ignored) {}
                            }
                        }
                    }

                    if (contentLength > 0) {
                        char[] body = new char[contentLength];
                        int read = 0;
                        while (read < contentLength) {
                            int r = in.read(body, read, contentLength - read);
                            if (r == -1) break;
                            read += r;
                        }
                    }

                    Log.d(TAG, "RTSP Método recebido do iPhone: " + method);

                    StringBuilder resp = new StringBuilder();
                    resp.append("RTSP/1.0 200 OK\r\n");
                    resp.append("CSeq: ").append(cseq).append("\r\n");
                    resp.append("Server: AirTunes/220.68\r\n");

                    if ("OPTIONS".equalsIgnoreCase(method)) {
                        resp.append("Public: ANNOUNCE, SETUP, RECORD, PAUSE, FLUSH, TEARDOWN, OPTIONS, SET_PARAMETER, GET_PARAMETER\r\n\r\n");
                    } else if ("ANNOUNCE".equalsIgnoreCase(method)) {
                        connectedClientIp = clientIp;
                        isStreaming = true;
                        if (listener != null) listener.onStatusChanged(true, clientIp, connectedDeviceName);
                        resp.append("\r\n");
                    } else if ("SETUP".equalsIgnoreCase(method)) {
                        connectedClientIp = clientIp;
                        isStreaming = true;
                        if (listener != null) listener.onStatusChanged(true, clientIp, connectedDeviceName);
                        resp.append("Transport: RTP/AVP/UDP;unicast;mode=record;server_port=").append(AUDIO_PORT)
                            .append(";control_port=").append(CONTROL_PORT)
                            .append(";timing_port=").append(TIMING_PORT).append("\r\n");
                        resp.append("Session: 1\r\n");
                        resp.append("Audio-Jack-Status: connected\r\n\r\n");
                    } else if ("RECORD".equalsIgnoreCase(method)) {
                        isStreaming = true;
                        if (listener != null) listener.onStatusChanged(true, clientIp, connectedDeviceName);
                        resp.append("Audio-Latency: 2205\r\n\r\n");
                    } else if ("GET_PARAMETER".equalsIgnoreCase(method)) {
                        resp.append("Content-Type: text/parameters\r\nContent-Length: 0\r\n\r\n");
                    } else if ("SET_PARAMETER".equalsIgnoreCase(method)) {
                        resp.append("\r\n");
                    } else if ("FLUSH".equalsIgnoreCase(method)) {
                        resp.append("RTP-Info: seq=0;rtptime=0\r\n\r\n");
                    } else if ("TEARDOWN".equalsIgnoreCase(method)) {
                        resp.append("Connection: close\r\n\r\n");
                        out.write(resp.toString().getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        isStreaming = false;
                        connectedClientIp = "Nenhum";
                        if (listener != null) listener.onStatusChanged(false, "Nenhum", "");
                        break;
                    } else {
                        resp.append("\r\n");
                    }

                    out.write(resp.toString().getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (Exception ignored) {
                isStreaming = false;
                connectedClientIp = "Nenhum";
                if (listener != null) listener.onStatusChanged(false, "Nenhum", "");
            }
        }).start();
    }

    private void startUdpAudioReceiver() {
        new Thread(() -> {
            try {
                audioSocket = new DatagramSocket(AUDIO_PORT);
                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (isRunning) {
                    audioSocket.receive(packet);
                    int len = packet.getLength();
                    if (len > 12) {
                        if (audioTrack != null) {
                            audioTrack.write(packet.getData(), 12, len - 12);
                        }
                    }
                }
            } catch (Exception e) {
                if (isRunning) Log.e(TAG, "Erro no socket UDP de áudio", e);
            }
        }).start();
    }
}
