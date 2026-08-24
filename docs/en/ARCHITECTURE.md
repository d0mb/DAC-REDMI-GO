# System Architecture • DAC Hub Pro

This document describes the technical architecture, signal pipeline, and low-level engineering applied to convert the **Xiaomi Redmi Go (Snapdragon 425)** into a dedicated Hi-Fi Audio DAC Receiver.

---

## 1. High-Level Architecture

The system consists of two primary components communicating over a local high-speed network:

```
+--------------------------------------------------------------------+
|                        WINDOWS TRANSMITTER                         |
|                                                                    |
|  [ Media Player ] -> [ Windows Core Audio / WASAPI Loopback ]      |
|                                     │                              |
|                          [ Ring Buffer (1024 samples) ]            |
|                                     │                              |
|                          [ DACHubStreamer (Python/C) ]             |
+─────────────────────────────────────┼──────────────────────────────+
                                      │ TCP Socket (Port 8080)
                                      │ Raw PCM 44.1kHz 16-bit Stereo
                                      ▼
+--------------------------------------------------------------------+
|                    ANDROID DAC RECEIVER (REDMI GO)                 |
|                                                                    |
|  [ WifiAudioServer (ServerSocket 8080) ]                           |
|                    │                                               |
|                    ├──> [ REST API Telemetry / Web Dashboard ]     |
|                    │                                               |
|                    └──> [ AudioTrack (Stream Music • Low Latency) ]|
|                                     │                              |
|                          [ Qualcomm Snapdragon Audio DSP ]         |
|                                     │                              |
|                          [ Internal 24-bit / 192kHz DAC ]          |
|                                     │                              |
|                          [ 3.5mm (P2) Analog Output ]              |
|                                     ▼                              |
|                          [ POWERED SPEAKERS / AMPLIFIER ]          |
+--------------------------------------------------------------------+
```

---

## 2. Windows Audio Pipeline (Transmitter)

1. **WASAPI Loopback Capture:**
   - The transmitter utilizes the Windows Audio Session API (WASAPI) in loopback mode to tap into the master system audio stream directly before local rendering.
2. **Audio Stream Specifications:**
   - Sample Rate: **44,100 Hz (44.1 kHz)**.
   - Bit Depth: **16-bit Signed PCM** (Little-Endian).
   - Channels: **2 (Stereo)**.
   - Bitrate: **1,411,200 bps (~1.41 Mbps / Uncompressed Lossless)**.
3. **Resilience & Continuous Auto-Reconnect:**
   - Employs an exponential auto-reconnect loop to resume streaming in milliseconds if Wi-Fi fluctuates or if the PC enters/wakes from sleep mode.

---

## 3. Android Audio Pipeline (Receiver)

1. **DacAudioService (Foreground Service):**
   - Runs with partial `WAKE_LOCK` (`PowerManager.PARTIAL_WAKE_LOCK`) and high-performance `WifiLock` (`WIFI_MODE_FULL_HIGH_PERF`) to prevent Qualcomm CPU cores and the Wi-Fi radio from entering Doze mode.
2. **Low-Latency AudioTrack in Streaming Mode:**
   - Directly configured on `AudioManager.STREAM_MUSIC` with adaptive buffer sizing to eliminate buffer underruns, popping, and noticeable latency.
3. **Qualcomm Snapdragon 425 Hardware DAC (WCD9326 / PMIC):**
   - The Snapdragon SoC features an integrated hardware audio DAC with >100 dB Signal-to-Noise Ratio (SNR) and ultra-low Total Harmonic Distortion (THD+N < -85 dB).

---

## 4. System Debloat & Resource Optimizations

To ensure 100% of CPU and memory are dedicated to audio playback:
- **Zero Background Bloatware:** Over 35 OEM packages removed (Xiaomi Analytics, Spock, Facebook Services, heavy Google services).
- **RAM Optimization:** Free RAM increased from ~40 MB to **> 230 MB free** + 725 MB ZRAM swap.
- **Window Animations Disabled:** `window_animation_scale = 0`.
