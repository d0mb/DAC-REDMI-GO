# ⚡ DAC Hub Pro • Transforme seu Smartphone em um Receptor DAC Hi-Fi Dedicado

<p align="center">
  <img src="https://img.shields.io/badge/Audio-Lossless%20PCM%2044.1kHz%2016--bit-00E5FF?style=for-the-badge&logo=soundcharts" />
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20Windows-blue?style=for-the-badge&logo=windows" />
  <img src="https://img.shields.io/badge/Latency-Ultra--Low%20(~15ms)-success?style=for-the-badge" />
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" />
</p>

O **DAC Hub Pro** é um ecossistema completo para transformar qualquer smartphone Android antigo ou dedicado em uma **Central Receptora DAC Hi-Fi de Alta Fidelidade**, transmitindo o áudio de qualquer computador Windows (ou outros dispositivos) para caixas de som amplificadas ou fones via cabo P2 (3.5mm), Wi-Fi e Bluetooth.

---

## 🌟 Principais Recursos

- 🎵 **Áudio Lossless sem Compressão:** Transmissão PCM raw a 44.1 kHz / 16-bit estéreo (1411 kbps) diretamente para o DAC do Snapdragon.
- ⚡ **Latência Ultra-Baixa (~15ms):** Ideal para músicas, vídeos no YouTube, filmes e jogos.
- 🔄 **Auto-Reconexão Contínua & Zero-Pop:** Se o computador transmissor suspender ou o Wi-Fi oscilar, a reconexão ocorre automaticamente sem estalos ou ruídos na caixa de som.
- 📊 **Monitoramento e VU Meter em Tempo Real:** Tela do celular e Dashboard Web exibem o IP conectado, bitrate e visualizador dinâmico de sinal sonoro.
- 📦 **Cliente Windows 100% Portátil:** Executável único (`.exe`) sem instaladores, que descobre o receptor na rede sozinho.
- 🌐 **Dashboard Web Integrado:** Controle e teste a caixa de som a partir de qualquer navegador na rede local (`http://[IP]:8080`).

---

## 🏗️ Arquitetura do Sistema

```
+---------------------------+                     +-------------------------------+
|     WINDOWS TRANSMITTER   |  TCP Stream (8080)  |        ANDROID DAC RECEIVER   |
|                           | ──────────────────> |                               |
| • WASAPI Loopback Capture |   Lossless 1411kbps | • AudioTrack Stream           |
| • Auto-Discovery na Rede  |                     | • Qualcomm Snapdragon DAC     |
| • Auto-Reconnect Engine   |                     | • Saída P2 Analógica 3.5mm    |
+---------------------------+                     +-------------------------------+
                                                                 │
                                                                 ▼
                                                    [ CAIXAS DE SOM / AMPLIFICADOR ]
```

---

## 📁 Estrutura do Projeto

```
DAC/
├── android-app/                   # Código-fonte do aplicativo Android (DAC Hub Pro)
│   ├── src/main/java/             # Backend de áudio, servidor HTTP e Bluetooth
│   └── src/main/res/              # Telas, temas e layouts em Dark Mode
├── windows-transmitter/           # Código-fonte do Transmissor de áudio Windows
│   ├── src/DACHubStreamer.py      # Captura WASAPI Loopback e streaming resiliente
│   ├── requirements.txt           # Dependências Python (sounddevice, numpy)
│   └── build_exe.ps1              # Script de compilação do executável portátil
├── scripts/                       # Scripts de automação e compilação
│   ├── build_apk.ps1              # Build rápido do APK sem necessidade de IDE
│   ├── debloat.sh                 # Script de remoção de bloatware do Android
│   └── iniciar_transmissor_pc.bat # Atalho para iniciar o transmissor
├── docs/                          # Documentação detalhada
│   ├── ARQUITETURA.md             # Engenharia de baixo nível do DAC
│   └── GUIA_INSTALACAO.md         # Manual de instalação e uso
├── dist/                          # Binários prontos para uso
│   ├── DAC_Hub_Pro.apk            # APK assinado pronto para o celular
│   ├── DAC_Hub_Transmitter_Portable.exe # Executável portátil para qualquer PC
│   └── LEIA-ME.txt
└── README.md                      # Este arquivo
```

---

## 🚀 Como Usar Rapidamente

### 1. No Celular (Receptor DAC)
1. Instale o aplicativo **[dist/DAC_Hub_Pro.apk](dist/DAC_Hub_Pro.apk)** no celular.
2. Conecte o cabo P2 (3.5mm) na saída de fone do celular e na entrada da sua **caixa de som**.
3. Abra o app: ele mostrará o status **ONLINE** e o endereço IP da rede local.

### 2. Em Qualquer Computador Windows
1. Execute o arquivo portátil **[dist/DAC_Hub_Transmitter_Portable.exe](dist/DAC_Hub_Transmitter_Portable.exe)**.
2. O aplicativo encontra o celular na rede e começa a transmitir todo o áudio do Windows instantaneamente!

---

## 🛠️ Como Compilar o Projeto do Zero

### Compilar o APK Android (sem Android Studio):
```powershell
powershell -ExecutionPolicy Bypass -File scripts/build_apk.ps1
```

### Compilar o Executável Windows (EXE portátil):
```powershell
powershell -ExecutionPolicy Bypass -File windows-transmitter/build_exe.ps1
```

---

## 📄 Licença

Este projeto é disponibilizado sob a licença [MIT](LICENSE).
