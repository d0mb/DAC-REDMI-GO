import ext4
import os
import zipfile

vol = ext4.Volume(open(r"rom\lineage-raw.img", "rb"))
module_dir = r"magisk-module"
os.makedirs(os.path.join(module_dir, "system", "lib", "hw"), exist_ok=True)
os.makedirs(os.path.join(module_dir, "system", "app", "Bluetooth"), exist_ok=True)
os.makedirs(os.path.join(module_dir, "system", "etc", "bluetooth"), exist_ok=True)

files_to_extract = [
    ("/lib/hw/bluetooth.default.so", os.path.join(module_dir, "system", "lib", "hw", "bluetooth.default.so")),
    ("/lib/hw/audio.a2dp.default.so", os.path.join(module_dir, "system", "lib", "hw", "audio.a2dp.default.so")),
    ("/lib/libbluetooth_jni.so", os.path.join(module_dir, "system", "lib", "libbluetooth_jni.so")),
    ("/app/Bluetooth/Bluetooth.apk", os.path.join(module_dir, "system", "app", "Bluetooth", "Bluetooth.apk")),
]

for src_path, dst_path in files_to_extract:
    print(f"[*] Extraindo {src_path} -> {dst_path}...")
    inode = vol.inode_at(src_path)
    data = inode.open().read()
    with open(dst_path, "wb") as f:
        f.write(data)
    print(f"[+] Extraído {len(data)} bytes com sucesso!")

# Criar bt_stack.conf com Audio Loudspeaker class e A2DP Sink
bt_conf = """# Configuração de Bluetooth A2DP Sink para DAC Hub Pro
TraceConf=true
TRC_BTM=2
TRC_HCI=2
TRC_A2D=2
TRC_AVDT=2
TRC_AVRC=2
TRC_BTAPP=2
TRC_BTIF=2

# Classe de Caixa de Som Hi-Fi / Loudspeaker (0x240414)
Class={0x24, 0x04, 0x14}
"""
with open(os.path.join(module_dir, "system", "etc", "bluetooth", "bt_stack.conf"), "w", encoding="utf-8") as f:
    f.write(bt_conf)

# Criar module.prop do Magisk
module_prop = """id=dac_bluetooth_sink
name=DAC Hub Pro - Bluetooth A2DP Sink Fix
version=v1.0
versionCode=100
author=DAC Hub Team
description=Habilita o receptor Bluetooth de alta fidelidade A2DP Sink e decodificador de áudio nativo no Xiaomi Redmi Go.
"""
with open(os.path.join(module_dir, "module.prop"), "w", encoding="utf-8") as f:
    f.write(module_prop)

# Criar service.sh do Magisk para inicializar no boot
service_sh = """#!/system/bin/sh
MODDIR=${0%/*}

# Ativar propriedades A2DP Sink e Classe de Áudio
setprop bluetooth.device.class 0x240414
setprop bluetooth.device.class_of_device 0x240414
setprop persist.bluetooth.class 0x240414
setprop bluetooth.profile.a2dp.sink.enabled true
setprop persist.bluetooth.a2dp_sink true
setprop persist.service.btui.sink true
setprop persist.bluetooth.sink true
settings put global bluetooth_a2dp_sink_enabled 1
"""
with open(os.path.join(module_dir, "service.sh"), "w", encoding="utf-8", newline='\n') as f:
    f.write(service_sh)

# Criar o arquivo ZIP do Módulo Magisk
zip_path = r"dist\DAC_Bluetooth_Sink_Fix.zip"
os.makedirs("dist", exist_ok=True)
print(f"[*] Empacotando Módulo Magisk em {zip_path}...")
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(module_dir):
        for file in files:
            full_path = os.path.join(root, file)
            rel_path = os.path.relpath(full_path, module_dir)
            z.write(full_path, rel_path)

print(f"\n========================================================")
print(f"   MÓDULO MAGISK GERADO COM SUCESSO EM: {zip_path}")
print(f"========================================================")
