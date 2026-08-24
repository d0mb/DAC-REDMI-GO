#!/system/bin/sh
mount -o remount,rw /system 2>/dev/null
mount -o remount,rw / 2>/dev/null

CONF="/system/etc/bluetooth/bt_stack.conf"
if [ -f "$CONF" ]; then
    # Remover Class anterior se existir e adicionar Class de Caixa de Som Audio/Video Loudspeaker (0x240414)
    grep -v "Class=" "$CONF" > /data/local/tmp/bt_stack.conf
    echo "" >> /data/local/tmp/bt_stack.conf
    echo "# Configurado como Caixa de Som Bluetooth (Major: Audio/Video 0x04, Minor: Loudspeaker 0x14)" >> /data/local/tmp/bt_stack.conf
    echo "Class={0x24, 0x04, 0x14}" >> /data/local/tmp/bt_stack.conf
    cp /data/local/tmp/bt_stack.conf "$CONF"
    chmod 644 "$CONF"
fi

# Setar propriedades de Bluetooth Class e A2DP Sink
setprop bluetooth.device.class 0x240414
setprop bluetooth.device.class_of_device 0x240414
setprop persist.bluetooth.class 0x240414
setprop bluetooth.profile.a2dp.sink.enabled true
setprop persist.bluetooth.a2dp_sink true
setprop persist.service.btui.sink true
setprop persist.bluetooth.sink true
settings put global bluetooth_a2dp_sink_enabled 1

# Reiniciar rádio Bluetooth para aplicar nova classe de dispositivo
service call bluetooth_manager 6 >/dev/null 2>&1
sleep 2
service call bluetooth_manager 4 >/dev/null 2>&1
