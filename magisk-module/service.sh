#!/system/bin/sh
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
