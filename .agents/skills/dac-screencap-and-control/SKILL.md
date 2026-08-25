---
name: dac-screencap-and-control
description: Captura screenshots em tempo real da tela do Xiaomi Redmi Go e controla eventos de toque, volume e atividades
when_to_use: "Use quando precisar inspecionar a interface do celular, ajustar volume do sistema ou automatizar testes de tela."
allowed-tools: Read, Write, Edit, Bash
version: 1.0.0
---

# Skill: DAC Screencap and Remote Control

Esta skill fornece comandos canônicos para visualização e controle remoto do Xiaomi Redmi Go conectado via USB/ADB.

---

## 📸 Captura de Tela em Tempo Real

1. **Capturar e puxar para o PC:**
   ```powershell
   & .\tools\platform-tools\adb.exe shell screencap -p /sdcard/live_screen.png
   & .\tools\platform-tools\adb.exe pull /sdcard/live_screen.png tools/live_screen.png
   ```

2. **Copiar para a pasta de artefatos da conversa:**
   ```powershell
   powershell.exe -Command "Copy-Item tools\live_screen.png -Destination 'C:\Users\helde\.gemini\antigravity-ide\brain\<conversation-id>\live_screen.png' -Force"
   ```

---

## 🔊 Controle de Volume e Áudio

- **Definir Volume Máximo (100%):**
  ```powershell
  & .\tools\platform-tools\adb.exe shell "media volume --set 15"
  ```
- **Consultar Volume Atual:**
  ```powershell
  & .\tools\platform-tools\adb.exe shell "media volume --get"
  ```
- **Desbloquear Tela:**
  ```powershell
  & .\tools\platform-tools\adb.exe shell "input keyevent 82; input swipe 360 1000 360 200"
  ```
