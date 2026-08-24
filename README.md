# ⚡ DAC Hub Pro • Transforme seu Xiaomi Redmi Go em uma Central DAC Hi-Fi Dedicada
### *Turn your Xiaomi Redmi Go into a Dedicated Hi-Fi Audio DAC Receiver*

<p align="center">
  <img src="https://img.shields.io/badge/Audio-Lossless%20PCM%2044.1kHz%2016--bit-00E5FF?style=for-the-badge&logo=soundcharts" alt="Lossless Audio" />
  <img src="https://img.shields.io/badge/Latency-Ultra--Low%20(~15ms)-success?style=for-the-badge" alt="Ultra Low Latency" />
  <img src="https://img.shields.io/badge/Platform-Android%208.1%20%7C%20Windows%2010%2F11-blue?style=for-the-badge&logo=windows" alt="Platforms" />
  <img src="https://img.shields.io/badge/Hardware-Snapdragon%20425%20DAC%20(tiare)-orange?style=for-the-badge&logo=qualcomm" alt="Snapdragon Hardware" />
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="MIT License" />
</p>

<p align="center">
  <a href="#-português-brasil"><b>🇧🇷 Português (Brasil)</b></a> •
  <a href="#-english"><b>🇺🇸 English</b></a>
</p>

---

<a name="-português-brasil"></a>
## 🇧🇷 Português (Brasil)

### 📌 Visão Geral do Projeto

O **DAC Hub Pro** é uma solução completa de engenharia reversa e desenvolvimento de software criada para revitalizar o smartphone **Xiaomi Redmi Go (`tiare`)**, convertendo-o em um **Receptor DAC Analógico Hi-Fi de Alta Fidelidade** dedicado para alimentar caixas de som amplificadas, soundbars e sistemas de som vintage via saída P2 (3.5mm), Wi-Fi e Bluetooth.

Ao eliminar todo o bloatware de fábrica e rotear o áudio diretamente pela API de baixo nível do **Qualcomm Snapdragon 425**, o sistema atinge transmissão de áudio sem perdas (lossless) com latência imperceptível e consumo ultra-baixo de recursos.

---

### ✨ Destaques & Recursos Principais

- 🎵 **Áudio Lossless sem Compressão:** Transmissão PCM raw a 44.1 kHz / 16-bit estéreo (1411 kbps) diretamente para o DAC do Snapdragon.
- ⚡ **Latência Ultra-Baixa (~15ms):** Sincronização perfeita para filmes, vídeos no YouTube e jogos.
- 🔄 **Auto-Reconexão Contínua & Zero-Pop:** Se o computador transmissor suspender ou o Wi-Fi oscilar, o receptor limpa o buffer suavemente sem estalos na caixa de som e reconecta sozinho.
- 📊 **Telemetria ao Vivo & VU Meter Dinâmico:** Monitoramento na tela do celular e no Painel Web exibindo o IP do transmissor ativo, bitrate e barra de nível de som em tempo real.
- 📦 **Transmissor Windows 100% Portátil:** Executável único (`.exe`) sem instaladores, que descobre o receptor na rede sozinho.
- 🌐 **Dashboard Web Integrado:** Acesse `http://[IP-DO-CELULAR]:8080` de qualquer navegador para controle de som e diagnósticos.

---

### 🏗️ Arquitetura do Sistema

```
+------------------------------------+                     +---------------------------------------+
|        TRANSMISSOR WINDOWS         |  TCP Socket (8080)  |          RECEPTOR ANDROID (DAC)       |
|                                    | ──────────────────> |                                       |
| • Captura WASAPI Loopback          |   Lossless 1411kbps | • DacAudioService (Foreground/WakeLock)|
| • Auto-Discovery na Subrede        |   Raw PCM Estéreo   | • WifiAudioServer (Zero-Pop Buffer)   |
| • Reconexão Resiliente Exponencial |                     | • Qualcomm Snapdragon 425 Audio DSP   |
+------------------------------------+                     +---------------------------------------+
                                                                               │
                                                                               ▼
                                                                  [ SAÍDA ANALÓGICA P2 3.5mm ]
                                                                               │
                                                                               ▼
                                                                  [ CAIXAS DE SOM / AMPLIFICADOR ]
```

---

### 🚀 Como Usar em 2 Passos

#### 1. No Celular (Receptor)
1. Instale o APK: **[`dist/DAC_Hub_Pro.apk`](dist/DAC_Hub_Pro.apk)**.
2. Conecte o cabo P2 (3.5mm) na saída de fone do celular e a outra ponta na sua caixa de som.
3. Abra o app: ele exibirá **ONLINE** e o endereço IP da rede local.

#### 2. No Computador (Transmissor)
1. Execute o arquivo portátil: **[`dist/DAC_Hub_Transmitter_Portable.exe`](dist/DAC_Hub_Transmitter_Portable.exe)**.
2. O aplicativo encontra o celular na rede e começa a transmitir todo o áudio do Windows instantaneamente!

#### 3. Conectar Celulares e Tablets via Bluetooth (Modo Receptor A2DP)
1. Na tela do aplicativo **DAC Hub Pro** no celular, toque no botão **`TORNAR VISÍVEL PARA PAREAR`** (ou acesse as configurações de Bluetooth do Android).
2. No seu outro smartphone, tablet ou notebook, abra o Bluetooth e procure pelo dispositivo:
   👉 **`DAC-HiFi-Audio`** (ou *Redmi Go*).
3. Toque para parear. Assim que conectado, todo o som do seu celular pessoal sairá diretamente nas caixas de som!

> 💡 **Wi-Fi vs Bluetooth:** Recomendamos o **Transmissor Wi-Fi** para PCs (áudio 100% puro PCM sem compressão e sem limite de distância) e o **Bluetooth** para pareamento rápido de smartphones e visitas.

---

<br>

---

<a name="-english"></a>
## 🇺🇸 English

### 📌 Project Overview

**DAC Hub Pro** is an open-source hardware re-purposing and audio streaming ecosystem designed to convert a **Xiaomi Redmi Go (`tiare`)** into a **Dedicated High-Fidelity DAC Audio Receiver** to drive active monitors, studio speakers, soundbars, or vintage amplifiers through its analog 3.5mm (P2) jack over Wi-Fi and Bluetooth.

By completely stripping Android OEM bloatware and streaming raw PCM audio directly to the **Qualcomm Snapdragon 425 SoC Audio DSP**, this project achieves zero-compression lossless reproduction with imperceptible latency and minimal power consumption.

---

### ✨ Key Features

- 🎵 **Uncompressed Lossless Audio:** 44.1 kHz / 16-bit Stereo PCM bitstream (1411 kbps) streamed directly to the Qualcomm hardware DAC.
- ⚡ **Ultra-Low Latency (~15ms):** Real-time synchronization suitable for watching movies, YouTube, and gaming.
- 🔄 **Continuous Auto-Reconnect & Zero-Pop Filtering:** Graceful buffer decay and zero-crossing mute on disconnect prevents popping sounds on speaker cones, resuming playback instantly upon network return.
- 📊 **Real-Time Telemetry & Dynamic VU Meter:** Live on-screen UI and Web Dashboard showing active client IP, sample rate, bit depth, and responsive audio visualizer bars.
- 📦 **Standalone Windows Client:** Zero-install single-file portable `.exe` with automatic LAN subnet discovery.
- 🌐 **Embedded Web Dashboard:** Point any browser on your local network to `http://[PHONE-IP]:8080` for diagnostics and remote control.

---

### 🏗️ Technical Architecture & Flow

```
+------------------------------------+                     +---------------------------------------+
|         WINDOWS TRANSMITTER        |  TCP Socket (8080)  |          ANDROID DAC RECEIVER         |
|                                    | ──────────────────> |                                       |
| • Windows WASAPI Loopback Capture  |   Lossless 1411kbps | • DacAudioService (WakeLock Protected) |
| • UDP/TCP LAN Auto-Discovery       |   Raw PCM Bitstream | • WifiAudioServer (Ring Buffered)     |
| • Exponential Auto-Reconnect Engine|                     | • Qualcomm Snapdragon 425 Audio DSP   |
+------------------------------------+                     +---------------------------------------+
                                                                               │
                                                                               ▼
                                                                  [ 3.5mm ANALOG STEREO JACK ]
                                                                               │
                                                                               ▼
                                                                  [ POWERED SPEAKERS / AMPLIFIER ]
```

---

### 🚀 Quick Start Guide

#### 1. Setup the DAC Receiver (Phone)
1. Install the application: **[`dist/DAC_Hub_Pro.apk`](dist/DAC_Hub_Pro.apk)**.
2. Plug a 3.5mm AUX cable from the phone's headphone jack into your amplifier or speaker system.
3. Open the app: the screen will display **ONLINE** along with its local IP address.

#### 2. Stream from Windows (PC)
1. Launch the standalone portable client: **[`dist/DAC_Hub_Transmitter_Portable.exe`](dist/DAC_Hub_Transmitter_Portable.exe)**.
2. The client will automatically discover the phone receiver on the local network and stream all PC audio in real time!

#### 3. Connect Mobile Devices via Bluetooth (A2DP Sink Mode)
1. In the **DAC Hub Pro** app on the phone, tap **`TORNAR VISÍVEL PARA PAREAR`** (*Make Discoverable*).
2. On your iPhone, Android smartphone, or tablet, search for available Bluetooth devices and select:
   👉 **`DAC-HiFi-Audio`** (or *Redmi Go*).
3. Pair the device. Audio played on your mobile device will now route directly through the wired 3.5mm speaker output!

> 💡 **Wi-Fi vs Bluetooth:** We recommend **Wi-Fi Streaming** for PCs (uncompressed 1411 kbps lossless PCM with unlimited range) and **Bluetooth** for quick pairing of guest smartphones.

---

## 📂 Repository Structure

```text
DAC/
├── android-app/                   # Android Receiver application (Java)
│   ├── src/main/java/             # Low-latency AudioTrack streaming & WebServer
│   └── src/main/res/              # Dark Mode UI & VU Meter layouts
├── windows-transmitter/           # Windows Audio Streamer (Python)
│   ├── src/DACHubStreamer.py      # WASAPI loopback capture & resilient streaming
│   ├── requirements.txt           # Python dependencies
│   └── build_exe.ps1              # Single-file portable EXE compiler
├── scripts/                       # Automation and helper scripts
│   ├── build_apk.ps1              # Lightweight APK build toolchain
│   ├── debloat.sh                 # Android extreme debloat script (230MB+ RAM free)
│   └── iniciar_transmissor_pc.bat # Quick launcher batch file
├── docs/                          # In-depth technical documentation
│   ├── pt-br/                     # 🇧🇷 Documentação em Português
│   │   ├── ARQUITETURA.md
│   │   ├── GUIA_INSTALACAO.md
│   │   └── MANUAL_ROM_E_BLUETOOTH.md
│   └── en/                        # 🇺🇸 Documentation in English
│       ├── ARCHITECTURE.md
│       ├── INSTALLATION_GUIDE.md
│       └── ROM_AND_BLUETOOTH_MANUAL.md
├── dist/                          # Pre-compiled ready-to-use binaries
│   ├── DAC_Hub_Pro.apk            # Signed production APK
│   ├── DAC_Hub_Transmitter_Portable.exe # Standalone portable Windows binary
│   └── LEIA-ME.txt
└── README.md                      # Bilingual Project Documentation
```

---

## 🛠️ Build from Source

### Build the Android APK (No Android Studio required):
```powershell
powershell -ExecutionPolicy Bypass -File scripts/build_apk.ps1
```

### Build the Windows Portable Executable:
```powershell
powershell -ExecutionPolicy Bypass -File windows-transmitter/build_exe.ps1
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
