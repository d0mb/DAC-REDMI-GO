$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$distDir = Join-Path $projectRoot "dist"
$srcFile = Join-Path $scriptDir "src\DACHubStreamer.py"

Write-Host "=========================================================="
Write-Host "   COMPILANDO DAC HUB TRANSMITTER PORTABLE (EXE)"
Write-Host "=========================================================="

python -m pip install -r (Join-Path $scriptDir "requirements.txt") pyinstaller

pyinstaller --clean --onefile --distpath $distDir --name "DAC_Hub_Transmitter_Portable" $srcFile

Write-Host "`n[+] Executável compilado com sucesso em: $distDir\DAC_Hub_Transmitter_Portable.exe"
