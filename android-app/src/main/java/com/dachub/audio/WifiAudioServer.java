package com.dachub.audio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

public class WifiAudioServer {
    private static final String TAG = "WifiAudioServer";
    private static WifiAudioServer instance;
    private final Context context;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private AudioTrack audioTrack;
    private final int port = 8080;

    // Telemetria e Monitoramento de Conexão
    private volatile String connectedClientIp = "Nenhum";
    private volatile boolean isStreaming = false;
    private volatile int currentAudioLevel = 0; // 0 a 100%
    private volatile long lastAudioReceiveTime = 0;
    private volatile long streamStartTime = 0;
    private volatile long totalBytesReceived = 0;
    private volatile Socket currentStreamingSocket;

    public void disconnectActiveStream() {
        isStreaming = false;
        connectedClientIp = "Nenhum";
        currentAudioLevel = 0;
        try {
            if (currentStreamingSocket != null && !currentStreamingSocket.isClosed()) {
                currentStreamingSocket.close();
            }
        } catch (Exception ignored) {}
        initAudioTrack();
    }

    public static synchronized WifiAudioServer getInstance(Context context) {
        if (instance == null) {
            instance = new WifiAudioServer(context.getApplicationContext());
        }
        return instance;
    }

    public WifiAudioServer(Context context) {
        this.context = context;
        initAudioTrack();
        startWatchdog();
    }

    private synchronized void initAudioTrack() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {}
        }
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
                Math.max(bufferSize * 2, 16384),
                AudioTrack.MODE_STREAM
        );
        audioTrack.play();
    }

    private void startWatchdog() {
        // Monitor para resetar status de streaming quando não houver dados por > 2s
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    if (isStreaming && (System.currentTimeMillis() - lastAudioReceiveTime > 2500)) {
                        isStreaming = false;
                        currentAudioLevel = 0;
                        connectedClientIp = "Em espera";
                    }
                } catch (Exception ignored) {}
            }
        }).start();
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                Log.i(TAG, "Servidor de áudio Wi-Fi rodando na porta " + port);

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleClient(clientSocket);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro no servidor Wi-Fi", e);
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        new Thread(() -> {
            try (InputStream in = socket.getInputStream();
                 OutputStream out = socket.getOutputStream()) {

                byte[] headerBytes = new byte[2048];
                int read = in.read(headerBytes);
                if (read <= 0) return;
                String request = new String(headerBytes, 0, read);
                String clientIp = socket.getInetAddress() != null ? socket.getInetAddress().getHostAddress() : "Desconhecido";

                if (request.contains("POST /test-tone") || request.contains("GET /test-tone")) {
                    playTestTone();
                    String response = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nContent-Type: text/plain\r\n\r\nOK";
                    out.write(response.getBytes());
                    out.flush();
                } else if (request.contains("GET /api/status")) {
                    int durationSec = isStreaming ? (int) ((System.currentTimeMillis() - streamStartTime) / 1000) : 0;
                    String json = String.format(Locale.US,
                            "{\"status\":\"%s\",\"client_ip\":\"%s\",\"level\":%d,\"duration\":%d,\"bitrate\":\"1411 kbps\",\"format\":\"44.1kHz 16-bit\"}",
                            isStreaming ? "STREAMING" : "IDLE",
                            connectedClientIp,
                            currentAudioLevel,
                            durationSec
                    );
                    String response = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nContent-Type: application/json\r\n\r\n" + json;
                    out.write(response.getBytes());
                    out.flush();
                } else if (request.contains("POST /stream") || request.contains("PUT /stream")) {
                    String response = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\n\r\n";
                    out.write(response.getBytes());
                    out.flush();

                    connectedClientIp = clientIp;
                    currentStreamingSocket = socket;
                    isStreaming = true;
                    streamStartTime = System.currentTimeMillis();
                    lastAudioReceiveTime = System.currentTimeMillis();

                    byte[] audioBuffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(audioBuffer)) != -1 && isRunning) {
                        lastAudioReceiveTime = System.currentTimeMillis();
                        totalBytesReceived += bytesRead;

                        // Calcular nível de pico/RMS para o VU meter
                        calculateAudioLevel(audioBuffer, bytesRead);

                        if (audioTrack != null) {
                            audioTrack.write(audioBuffer, 0, bytesRead);
                        }
                    }

                    // Ao desconectar suavemente
                    isStreaming = false;
                    currentAudioLevel = 0;
                    connectedClientIp = "Em espera";
                } else {
                    sendDashboardHtml(out);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao processar cliente", e);
            }
        }).start();
    }

    private void calculateAudioLevel(byte[] buffer, int length) {
        long sum = 0;
        int count = length / 2;
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            sum += Math.abs(sample);
        }
        if (count > 0) {
            int avg = (int) (sum / count);
            int level = Math.min(100, (avg * 100) / 16000);
            currentAudioLevel = (currentAudioLevel * 3 + level * 7) / 10; // Suavização exponencial
        }
    }

    private void sendDashboardHtml(OutputStream out) {
        try {
            String ip = getIpAddress();
            String html = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n" +
                    "<!DOCTYPE html><html lang='pt-BR'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<title>DAC Hub Pro • Central de Áudio Hi-Fi</title>" +
                    "<style>" +
                    "*{box-sizing:border-box;margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;}" +
                    "body{background:#080C14;color:#F8FAFC;min-height:100vh;display:flex;align-items:center;justify-content:center;padding:20px;}" +
                    ".container{background:#0F172A;border:1px solid #1E293B;border-radius:24px;width:100%;max-width:520px;padding:32px;box-shadow:0 25px 50px -12px rgba(0,0,0,0.7);}" +
                    ".header{display:flex;align-items:center;justify-content:space-between;margin-bottom:24px;}" +
                    ".logo{font-size:20px;font-weight:900;letter-spacing:1px;background:linear-gradient(135deg,#00E5FF,#3B82F6);-webkit-background-clip:text;-webkit-text-fill-color:transparent;}" +
                    ".badge{font-size:11px;font-weight:700;padding:4px 10px;border-radius:999px;background:#052E16;color:#4ADE80;border:1px solid #15803D;display:flex;align-items:center;gap:6px;}" +
                    ".badge-dot{width:6px;height:6px;border-radius:50%;background:#4ADE80;box-shadow:0 0 8px #4ADE80;animation:pulse 2s infinite;}" +
                    "@keyframes pulse{0%,100%{opacity:1;}50%{opacity:0.4;}}" +
                    ".card{background:#1E293B;border-radius:16px;padding:20px;margin-bottom:20px;border:1px solid #334155;}" +
                    ".card-title{font-size:12px;text-transform:uppercase;color:#94A3B8;font-weight:700;margin-bottom:12px;display:flex;justify-content:space-between;}" +
                    ".device-name{font-size:18px;font-weight:700;color:#F1F5F9;margin-bottom:4px;}" +
                    ".device-info{font-size:13px;color:#64748B;}" +
                    ".vu-container{background:#0B132B;border-radius:12px;height:24px;overflow:hidden;position:relative;border:1px solid #1E293B;margin-top:12px;}" +
                    ".vu-bar{height:100%;width:0%;background:linear-gradient(90deg,#00E5FF,#3B82F6,#EC4899);transition:width 0.1s ease;border-radius:12px;box-shadow:0 0 12px rgba(0,229,255,0.5);}" +
                    ".btn{display:block;width:100%;padding:14px;background:linear-gradient(135deg,#00E5FF,#2563EB);color:#080C14;font-size:15px;font-weight:700;border:none;border-radius:14px;cursor:pointer;transition:all 0.2s;text-align:center;box-shadow:0 4px 15px rgba(0,229,255,0.3);}" +
                    ".btn:hover{transform:translateY(-1px);filter:brightness(1.1);}" +
                    ".btn:active{transform:translateY(1px);}" +
                    ".footer-stats{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:20px;}" +
                    ".stat-box{background:#141F36;padding:12px;border-radius:12px;text-align:center;}" +
                    ".stat-val{font-size:14px;font-weight:700;color:#00E5FF;}" +
                    ".stat-lbl{font-size:11px;color:#64748B;margin-top:2px;}" +
                    "</style></head><body>" +
                    "<div class='container'>" +
                    "<div class='header'>" +
                    "<div class='logo'>⚡ DAC HUB PRO</div>" +
                    "<div class='badge'><div class='badge-dot'></div>ONLINE</div>" +
                    "</div>" +
                    "<div class='card'>" +
                    "<div class='card-title'><span>FONTE DE ÁUDIO ATIVA</span><span id='status-badge' style='color:#00E5FF'>STREAMING</span></div>" +
                    "<div class='device-name' id='client-ip'>Carregando...</div>" +
                    "<div class='device-info'>Formato: <b>PCM 44.1kHz • 16-Bit Estéreo (Lossless)</b></div>" +
                    "<div class='vu-container'><div class='vu-bar' id='vu-bar'></div></div>" +
                    "</div>" +
                    "<button class='btn' onclick='testAudio()'>🔊 DISPARAR TOM DE TESTE NAS CAIXAS</button>" +
                    "<div class='footer-stats'>" +
                    "<div class='stat-box'><div class='stat-val'>" + ip + "</div><div class='stat-lbl'>IP DO RECEPTOR</div></div>" +
                    "<div class='stat-box'><div class='stat-val'>Snapdragon DAC</div><div class='stat-lbl'>HARDWARE P2 ATIVO</div></div>" +
                    "</div>" +
                    "</div>" +
                    "<script>" +
                    "function updateStatus(){" +
                    "fetch('/api/status').then(r=>r.json()).then(d=>{" +
                    "document.getElementById('client-ip').innerText = d.client_ip === 'Nenhum' || d.client_ip === 'Em espera' ? 'Aguardando Transmissor (PC/Celular)' : 'Transmitindo de: ' + d.client_ip;" +
                    "document.getElementById('status-badge').innerText = d.status === 'STREAMING' ? '🟢 AO VIVO' : '⚪ EM ESPERA';" +
                    "document.getElementById('status-badge').style.color = d.status === 'STREAMING' ? '#00E676' : '#64748B';" +
                    "document.getElementById('vu-bar').style.width = (d.status === 'STREAMING' ? Math.max(5, d.level) : 0) + '%';" +
                    "}).catch(()=>{});" +
                    "}" +
                    "setInterval(updateStatus, 300); updateStatus();" +
                    "function testAudio(){ fetch('/test-tone',{method:'POST'}); }" +
                    "</script></body></html>";

            out.write(html.getBytes("UTF-8"));
            out.flush();
        } catch (Exception ignored) {}
    }

    public synchronized void playTestTone() {
        new Thread(() -> {
            try {
                int sampleRate = 44100;
                double duration = 1.5;
                double freq1 = 440.0;
                double freq2 = 880.0;
                int numSamples = (int) (duration * sampleRate);
                short[] samples = new short[numSamples * 2];

                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / sampleRate;
                    double tone = (Math.sin(2.0 * Math.PI * freq1 * t) + 0.5 * Math.sin(2.0 * Math.PI * freq2 * t)) * 0.7;
                    short val = (short) (tone * 32767);
                    samples[i * 2] = val;
                    samples[i * 2 + 1] = val;
                }

                if (audioTrack != null) {
                    audioTrack.write(samples, 0, samples.length);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao tocar tom", e);
            }
        }).start();
    }

    public String getConnectedClientIp() {
        return connectedClientIp;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public int getCurrentAudioLevel() {
        return currentAudioLevel;
    }

    public String getIpAddress() {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ip = wifiInfo.getIpAddress();
                return String.format(Locale.getDefault(), "%d.%d.%d.%d",
                        (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
            }
        } catch (Exception ignored) {}
    }
}
