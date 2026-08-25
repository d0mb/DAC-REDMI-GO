---
name: dac-airplay-diagnostics
description: >-
  Diagnostica o receptor Apple AirPlay 2 (mDNS, RTSP, HTTP port 7000 e decodificador nativo ALAC/Oboe).
  Use quando o iPhone nao conectar ou quando precisar inspecionar a comunicacao AirPlay em tempo real.
---

# Skill: DAC AirPlay Diagnostics

Esta skill orienta o diagnóstico da pilha de áudio sem perdas para dispositivos Apple (iOS, iPadOS e macOS).

---

## 🔍 Comandos de Diagnóstico AirPlay

1. **Inspecionar Inicialização e Registro de Serviços mDNS:**
   ```powershell
   & .\tools\platform-tools\adb.exe logcat -d -s AirPlayServer AirPlayNative AirPlayService
   ```

2. **Verificar Métodos RTSP / HTTP recebidos do iPhone:**
   ```powershell
   & .\tools\platform-tools\adb.exe logcat -d | Select-String -Pattern "AirPlay", "RTSP", "ALAC", "Oboe" | Select-Object -Last 30
   ```

3. **Verificar Conexão de Portas de Rede:**
   - **Porta 5000 (RTSP):** Controle de sessão e handshake.
   - **Porta 7000 (AirPlay HTTP Info):** Metadados do receptor e compatibilidade com iOS 15-18.
   - **Porta 6000 (UDP RTP Audio):** Recepção dos pacotes de áudio ALAC decodificados por software.
