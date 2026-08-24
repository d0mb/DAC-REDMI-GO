#!/system/bin/sh
# Ativar propriedades do receptor Bluetooth A2DP Sink no boot
setprop bluetooth.profile.a2dp.sink.enabled true
setprop persist.bluetooth.a2dp_sink true
setprop persist.service.btui.sink true
setprop persist.bluetooth.sink true
settings put global bluetooth_a2dp_sink_enabled 1
