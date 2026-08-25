package com.dachub.audio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class WifiAudioServer {
    private static final String TAG = "WifiAudioServer";
    private static final int PORT = 8080;
    private static WifiAudioServer instance;

    private final Context context;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private AudioTrack audioTrack;
    private Socket activeClientSocket;
    private String connectedClientIp = "Em espera";
    private volatile boolean isStreaming = false;
    private int currentAudioLevel = 0;
    private long lastAudioReceiveTime = 0;

    public static synchronized WifiAudioServer getInstance(Context context) {
        if (instance == null) {
            instance = new WifiAudioServer(context.getApplicationContext());
        }
        return instance;
    }

    public WifiAudioServer(Context context) {
        this.context = context;
        startWatchdog();
    }

    private synchronized AudioTrack getOrCreateAudioTrack() {
        if (audioTrack == null) {
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
        return audioTrack;
    }

    private synchronized void releaseAudioTrack() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {}
            audioTrack = null;
        }
    }

    private void startWatchdog() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    if (isStreaming && (System.currentTimeMillis() - lastAudioReceiveTime > 2500)) {
                        isStreaming = false;
                        currentAudioLevel = 0;
                        connectedClientIp = "Em espera";
                        releaseAudioTrack();
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
                serverSocket = new ServerSocket(PORT);
                Log.i(TAG, "Servidor de áudio Wi-Fi rodando na porta " + PORT);

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    clientSocket.setReceiveBufferSize(65536);

                    synchronized (this) {
                        if (activeClientSocket != null && !activeClientSocket.isClosed()) {
                            try { activeClientSocket.close(); } catch (Exception ignored) {}
                        }
                        activeClientSocket = clientSocket;
                    }

                    connectedClientIp = clientSocket.getInetAddress().getHostAddress();
                    Log.i(TAG, "PC Conectado: " + connectedClientIp);

                    handleClient(clientSocket);
                }
            } catch (Exception e) {
                if (isRunning) Log.e(TAG, "Erro no ServerSocket", e);
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        new Thread(() -> {
            byte[] buffer = new byte[4096];
            try (InputStream in = socket.getInputStream()) {
                isStreaming = true;
                while (isRunning && !socket.isClosed()) {
                    int bytesRead = in.read(buffer);
                    if (bytesRead == -1) break;

                    AudioTrack track = getOrCreateAudioTrack();
                    if (track != null) {
                        track.write(buffer, 0, bytesRead);
                    }

                    lastAudioReceiveTime = System.currentTimeMillis();
                    calculateAudioLevel(buffer, bytesRead);
                }
            } catch (Exception e) {
                Log.w(TAG, "Cliente desconectado: " + e.getMessage());
            } finally {
                synchronized (this) {
                    if (activeClientSocket == socket) {
                        activeClientSocket = null;
                        isStreaming = false;
                        connectedClientIp = "Em espera";
                        currentAudioLevel = 0;
                        releaseAudioTrack();
                    }
                }
            }
        }).start();
    }

    private void calculateAudioLevel(byte[] buffer, int length) {
        long sum = 0;
        int count = length / 2;
        if (count == 0) return;

        ByteBuffer bb = ByteBuffer.wrap(buffer, 0, length).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            short sample = bb.getShort();
            sum += Math.abs(sample);
        }

        int avg = (int) (sum / count);
        currentAudioLevel = Math.min(100, (avg * 100) / 16384);
    }

    public void playTestTone() {
        new Thread(() -> {
            try {
                int sampleRate = 44100;
                int durationSeconds = 1;
                int numSamples = durationSeconds * sampleRate;
                short[] samples = new short[numSamples * 2];
                double freq = 1000.0;

                for (int i = 0; i < numSamples; i++) {
                    short val = (short) (Math.sin(2.0 * Math.PI * i * freq / sampleRate) * 20000);
                    samples[i * 2] = val;
                    samples[i * 2 + 1] = val;
                }

                AudioTrack track = getOrCreateAudioTrack();
                if (track != null) {
                    track.write(samples, 0, samples.length);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro no teste de tom", e);
            }
        }).start();
    }

    public void disconnectActiveStream() {
        synchronized (this) {
            if (activeClientSocket != null && !activeClientSocket.isClosed()) {
                try { activeClientSocket.close(); } catch (Exception ignored) {}
                activeClientSocket = null;
            }
            isStreaming = false;
            connectedClientIp = "Em espera";
            currentAudioLevel = 0;
            releaseAudioTrack();
        }
    }

    public void stop() {
        isRunning = false;
        disconnectActiveStream();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public String getConnectedClientIp() {
        return connectedClientIp;
    }

    public int getCurrentAudioLevel() {
        return currentAudioLevel;
    }

    public String getIpAddress() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                WifiInfo info = wm.getConnectionInfo();
                int ip = info.getIpAddress();
                if (ip != 0) {
                    return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
