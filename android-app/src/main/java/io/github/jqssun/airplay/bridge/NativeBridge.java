package io.github.jqssun.airplay.bridge;

import java.nio.ByteBuffer;
import java.util.Map;

public class NativeBridge {
    static {
        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("crypto");
            System.loadLibrary("oboe");
            System.loadLibrary("airplay_native");
        } catch (Throwable t) {
            android.util.Log.e("NativeBridge", "Erro carregando bibliotecas nativas C++", t);
        }
    }

    public static native void nativeDestroy(long handle);
    public static native Map<String, String> nativeGetAirplayTxtRecords(long handle);
    public static native String nativeGetRaopServiceName(long handle);
    public static native Map<String, String> nativeGetRaopTxtRecords(long handle);
    public static native String nativeGetServerName(long handle);
    public static native long nativeInit(RaopCallbackHandler handler, byte[] hwAddr, String serverName, String password, boolean audioOnly, boolean allowMirroring);
    public static native boolean nativeServerAudioConfigure(long handle, int sampleRate, int channels, int bitsPerSample, boolean isFloat, boolean isSigned, boolean isBigEndian, boolean isInterleaved);
    public static native boolean nativeServerAudioDebug(long handle, ByteBuffer buffer);
    public static native void nativeServerAudioFormat(long handle, int format, int sampleRate);
    public static native boolean nativeServerAudioStart(long handle);
    public static native void nativeServerAudioStop(long handle);
    public static native void nativeSetAudioEnabled(long handle, boolean enabled);
    public static native void nativeSetCodecs(long handle, boolean h264, boolean hevc);
    public static native void nativeSetDefaultStreamValues(int width, int height);
    public static native void nativeSetDisplaySize(long handle, int width, int height, int refreshRate);
    public static native void nativeSetH265Enabled(long handle, boolean enabled);
    public static native void nativeSetHlsEnabled(long handle, boolean enabled);
    public static native void nativeSetLang(long handle, String lang, String country, String region);
    public static native void nativeSetPlist(long handle, String plist, int size);
    public static native int nativeStart(long handle, int port);
    public static native void nativeStop(long handle);
    public static native void nativeUpdatePlaybackInfo(long handle, float duration, float position, float rate, boolean isPlaying);
}
