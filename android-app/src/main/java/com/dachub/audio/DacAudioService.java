package com.dachub.audio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class DacAudioService extends Service {
    private static final String CHANNEL_ID = "dac_audio_channel";
    private PowerManager.WakeLock wakeLock;
    private BluetoothSinkManager bluetoothSinkManager;
    private WifiAudioServer wifiAudioServer;

    @Override
    public void onCreate() {
        super.onCreate();

        // Evitar que o processador entre em sleep profundo
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DAC::AudioServiceLock");
            wakeLock.acquire();
        }

        startForegroundNotification();

        // Inicializar serviços de áudio
        bluetoothSinkManager = new BluetoothSinkManager(this, null);
        bluetoothSinkManager.init();

        wifiAudioServer = WifiAudioServer.getInstance(this);
        wifiAudioServer.start();
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DAC Hub Audio Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }

            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("DAC Hub Ativo")
                    .setContentText("Receptor de áudio Bluetooth e Wi-Fi em execução")
                    .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                    .build();

            startForeground(101, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wifiAudioServer != null) wifiAudioServer.stop();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
