@echo off
title DAC Hub Pro - Transmissor de Audio PC
chcp 65001 >nul
cls
echo ========================================================
echo       INICIANDO TRANSMISSÃO DE ÁUDIO PC -> CAIXAS DE SOM
echo ========================================================
python tools\stream_pc_audio.py
pause
