---
name: dac-builder
description: >-
  Compila e empacota o APK do DAC Hub Pro com as bibliotecas C++ nativas e gera o transmissor portatil de PC.
  Use quando o usuario pedir para compilar, atualizar o APK ou gerar os binarios do projeto.
---

# Skill: DAC Builder (Compilação e Deploy Automatizado)

Esta skill orquestra a compilação de código Java, conversão DEX (D8), link de recursos (AAPT2), empacotamento das bibliotecas C++ nativas (`jniLibs`) e assinatura digital do APK para o Xiaomi Redmi Go.

---

## 🛠️ Procedimento de Compilação do APK

1. **Executar script de build:**
   ```powershell
   powershell.exe -ExecutionPolicy Bypass -File scripts\build_apk.ps1
   ```
2. **Instalar no Redmi Go via ADB:**
   ```powershell
   & .\tools\platform-tools\adb.exe install -r -d -t dist\DAC_Hub_Pro.apk
   ```
3. **Reiniciar a MainActivity:**
   ```powershell
   & .\tools\platform-tools\adb.exe shell 'am force-stop com.dachub.audio; am start -n com.dachub.audio/.MainActivity'
   ```

---

## 💻 Compilação do Transmissor PC (Windows)

Para compilar o transmissor executável portátil para Windows:
```powershell
powershell.exe -ExecutionPolicy Bypass -File scripts\build_transmitter.ps1
```
O executável gerado estará em `dist\DAC_Hub_Transmitter_Portable.exe`.
