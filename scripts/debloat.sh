#!/system/bin/sh

echo "=== INICIANDO LIMPEZA RADICAL NO DISPOSITIVO ==="

PKGS="
com.facebook.services
com.facebook.system
com.facebook.appmanager
com.facebook.lite
com.miui.bugreport
com.miui.securitycleaner
com.miui.videoplayer
com.miui.spock
com.miui.playerlite
com.mi.globalbrowser.mini
com.mi.AutoTest
com.xiaomi.midrop
com.wingtech.stabilityrecord
com.bsp.catchlog
com.wt.secret_code_manager
com.factory.mmigroup
com.google.android.apps.youtube.mango
com.google.android.apps.mapslite
com.google.android.apps.navlite
com.google.android.gm.lite
com.google.android.apps.photos
com.google.android.apps.assistant
com.google.android.apps.speechservices
com.google.android.apps.searchlite
com.google.android.calendar
com.google.android.tts
com.google.android.feedback
com.google.android.printservice.recommendation
com.google.android.apps.messaging
com.google.android.dialer
com.google.android.contacts
com.google.android.syncadapters.contacts
com.google.android.backuptransport
com.google.android.partnersetup
com.android.printspooler
com.android.bips
com.android.cellbroadcastreceiver
com.android.emergency
com.android.stk
com.caf.fmradio
com.android.camera
com.android.soundrecorder
"

for p in $PKGS; do
    pm uninstall -k --user 0 $p >/dev/null 2>&1
    pm disable-user --user 0 $p >/dev/null 2>&1
    echo "[-] Removido: $p"
done

# Otimizações de desempenho
settings put global window_animation_scale 0
settings put global transition_animation_scale 0
settings put global animator_duration_scale 0
settings put global wifi_sleep_policy 2

# Liberar cache de memória
sync
echo 3 > /proc/sys/vm/drop_caches

echo "=== CONCLUÍDO COM SUCESSO! ==="
free -m
