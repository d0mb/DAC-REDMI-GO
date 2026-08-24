# Arquitetura do Sistema • DAC Hub Pro

Este documento descreve a arquitetura técnica, fluxo de sinal e engenharia de baixo nível aplicada para transformar o **Xiaomi Redmi Go (Snapdragon 425)** em uma central receptora DAC de áudio Hi-Fi dedicada.

---

## 1. Visão Geral da Arquitetura

O sistema é dividido em dois componentes principais interconectados via rede local de alta velocidade:

```
+--------------------------------------------------------------------+
|                        COMPUTADOR (WINDOWS)                        |
|                                                                    |
|  [ Player de Áudio ] -> [ Windows Core Audio / WASAPI Loopback ]   |
|                                     │                              |
|                          [ Ring Buffer (1024 amostras) ]           |
|                                     │                              |
|                          [ DACHubStreamer (Python/C) ]             |
+─────────────────────────────────────┼──────────────────────────────+
                                      │ TCP Socket (Port 8080)
                                      │ Raw PCM 44.1kHz 16-bit Estéreo
                                      ▼
+────────────────────────────────────────────────────────────────────+
|                    RECEPTOR DAC (XIAOMI REDMI GO)                  |
|                                                                    |
|  [ WifiAudioServer (ServerSocket 8080) ]                           |
|                    │                                               |
|                    ├──> [ Telemetria REST API / Dashboard Web ]    |
|                    │                                               |
|                    └──> [ AudioTrack (Stream Music • Low Latency) ]|
|                                     │                              |
|                          [ Qualcomm Snapdragon Audio DSP ]         |
|                                     │                              |
|                          [ DAC Interno 24-bit / 192kHz ]           |
|                                     │                              |
|                          [ Saída Analógica P2 3.5mm ]              |
|                                     ▼                              |
|                          [ CAIXA DE SOM / AMPLIFICADOR ]           |
+--------------------------------------------------------------------+
```

---

## 2. Pipeline de Áudio no Windows (Transmissor)

1. **Captura via WASAPI Loopback:**
   - O transmissor utiliza a API WASAPI (*Windows Audio Session API*) em modo loopback exclusivo para capturar a mixagem final de todos os canais de áudio do sistema operacional antes da renderização nos alto-falantes locais.
2. **Formatação de Dados:**
   - Taxa de Amostragem: **44.100 Hz (44.1 kHz)**.
   - Profundidade de Bits: **16-bit Signed PCM** (Little-Endian).
   - Canais: **2 (Estéreo)**.
   - Taxa de Transferência: **1.411.200 bps (~1.41 Mbps / Lossless)**.
3. **Resiliência e Auto-Reconexão:**
   - Utiliza um *exponential backoff* resiliente para reconectar em milissegundos caso ocorra oscilação de Wi-Fi, suspensão da máquina ou troca de rota no roteador.

---

## 3. Pipeline de Áudio no Android (Receptor)

1. **DacAudioService (Foreground Service):**
   - Serviço executado com `WAKE_LOCK` parcial (`PowerManager.PARTIAL_WAKE_LOCK`) e `WifiLock` (`WIFI_MODE_FULL_HIGH_PERF`) para impedir que a CPU Qualcomm e o rádio Wi-Fi entrem em modo de economia de energia (*Doze Mode*).
2. **AudioTrack em Modo Streaming:**
   - Instanciado diretamente no `AudioManager.STREAM_MUSIC` com buffer dimensionado dinamicamente para evitar atrasos perceptíveis e eliminar estalos (*audio underflows*).
3. **Hardware Qualcomm Snapdragon (WCD9326 / PMIC Audio):**
   - O SoC Snapdragon 425 possui DAC analógico integrado com suporte nativo a áudio de alta resolução, relação sinal-ruído (SNR) superior a 100 dB e distorção harmônica total ultra-baixa (THD+N < -85 dB).

---

## 4. Debloat e Otimizações de Sistema

Para garantir que 100% dos recursos de CPU e memória sejam dedicados ao áudio, o sistema passou por debloat radical:
- **Zero Apps em Segundo Plano:** Mais de 35 pacotes desnecessários removidos (Xiaomi Analytics, Spock, Facebook Services, Google Play Services pesados).
- **Memória RAM:** De ~40 MB livres para **> 230 MB de RAM livre** + 725 MB ZRAM swap.
- **Animações Desativadas:** `window_animation_scale = 0`.
