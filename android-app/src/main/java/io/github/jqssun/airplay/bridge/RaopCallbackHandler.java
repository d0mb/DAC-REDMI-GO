package io.github.jqssun.airplay.bridge;

public interface RaopCallbackHandler {
    void onLog(String message);
    void onAudioFormat(int sampleRate, int channels, boolean isFloat);
    void onAudioTeardown();
    float onClientVolume();
    void onConnectionDestroy();
    void onConnectionInit();
    void onConnectionReset(int code);
    void onCoverArt(byte[] data);
    void onDacpId(String dacpId, String activeRemote);
    void onDisplayPin(String pin);
    void onMetadata(byte[] data);
    void onMirrorRunning(boolean running);
    void onProgress(long start, long current, long end);
    void onVideoData(byte[] data, long timestamp, boolean isKeyFrame);
    void onVideoPlay(String location, float position);
    void onVideoRate(float rate);
    void onVideoScrub(float position);
    void onVideoSessionPoll();
    void onVideoSize(float width, float height, float aspect1, float aspect2);
    void onVideoStop();
    void onVolumeChange(float volume);
}
