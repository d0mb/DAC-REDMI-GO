# 🛠️ Manual de Configuração de ROM, Root e Bluetooth A2DP Sink

Este manual técnico detalha o passo a passo completo para desbloquear, instalar o sistema limpo, obter acesso Root (Magisk), executar o debloat radical e configurar o Bluetooth para funcionar como **Receptor de Áudio (A2DP Sink)** no **Xiaomi Redmi Go (`tiare`)** ou aparelhos equivalentes.

---

## 📑 Sumário
1. [Desbloqueio de Bootloader e Instalação de Recovery (TWRP/PBRP)](#1-desbloqueio-de-bootloader-e-recovery)
2. [Correção da Criptografia e Bootloop (FSTAB)](#2-correção-de-criptografia-e-bootloop-fstab)
3. [Instalação do Magisk Root e Debloat Extremo](#3-instalação-do-magisk-root-e-debloat)
4. [Como Funciona o Bluetooth A2DP Sink no Android](#4-como-funciona-o-bluetooth-a2dp-sink)
5. [Configuração do Bluetooth para Receber Áudio](#5-configuração-do-bluetooth-para-receber-áudio)

---

## 1. Desbloqueio de Bootloader e Recovery

### Passo 1.1: Ativar Opções de Desenvolvedor
1. No celular, vá em **Configurações > Sistema > Sobre o telefone**.
2. Toque 7 vezes em **Número da versão** até aparecer *"Você agora é um desenvolvedor"*.
3. Volte para **Opções do desenvolvedor** e ative:
   - **Desbloqueio de OEM** (*OEM Unlocking*).
   - **Depuração USB** (*USB Debugging*).

### Passo 1.2: Desbloquear o Bootloader via Fastboot
1. Conecte o celular ao computador via cabo USB.
2. Reinicie no modo Fastboot pelo terminal:
   ```bash
   adb reboot bootloader
   ```
3. Execute o comando de desbloqueio:
   ```bash
   fastboot oem unlock
   # ou para desbloqueio completo:
   fastboot oem unlock-go
   ```
4. Verifique se o bootloader está desbloqueado:
   ```bash
   fastboot getvar unlocked
   # Deve retornar: unlocked: yes
   ```

### Passo 1.3: Gravar o Custom Recovery (PitchBlack / TWRP)
1. Grave a imagem do recovery na partição `recovery` e inicialize-a:
   ```bash
   fastboot flash recovery recovery_pitchblack.img
   fastboot boot recovery_pitchblack.img
   ```

---

## 2. Correção de Criptografia e Bootloop (FSTAB)

A ROM original de fábrica do Redmi Go possui a diretiva `forceencrypt=footer` na partição `/data`, o que faz o aparelho travar em loop na inicialização caso a partição de dados seja formatada em EXT4 ou F2FS sem a chave de criptografia proprietária.

### Como Corrigir:
1. No TWRP / PitchBlack Recovery, monte a partição `/vendor`.
2. Edite o arquivo `/vendor/etc/fstab.qcom`:
   - Localize a linha que monta a partição `userdata`.
   - Substitua `forceencrypt=footer` por `encryptable=footer`.
3. No TWRP, formate a partição `/data`:
   ```bash
   mke2fs -t ext4 -b 4096 /dev/block/bootdevice/by-name/userdata
   ```
4. Reinicie o sistema normalmente (`fastboot reboot` ou pelo menu do recovery). O aparelho inicializará em menos de 45 segundos.

---

## 3. Instalação do Magisk Root e Debloat

### Passo 3.1: Instalação do Magisk 23.0
1. No TWRP, instale o arquivo `Magisk-v23.0.zip` (ou patcheie o `boot.img` com o app do Magisk).
2. Ao iniciar o sistema, instale o aplicativo `Magisk.apk`.
3. Conceda permissão de Superusuário permanente para o Shell (`su`).

### Passo 3.2: Execução do Debloat Radical
Para liberar o máximo de memória RAM e deixar a CPU 100% livre para o processamento de áudio sem interrupções:

1. Envie o script de debloat para o aparelho:
   ```bash
   adb push scripts/debloat.sh /data/local/tmp/
   ```
2. Execute como Root no terminal:
   ```bash
   adb shell "su -c 'sh /data/local/tmp/debloat.sh'"
   ```
3. O script removerá mais de 35 pacotes desnecessários (Facebook, Xiaomi Analytics, Google Play Services pesados), subindo a memória RAM livre de ~40 MB para **mais de 230 MB livres**.

---

## 4. Como Funciona o Bluetooth A2DP Sink

Por padrão, a grande maioria dos smartphones Android utiliza a stack Bluetooth (*Fluoride/BlueDroid*) configurada apenas no papel de **A2DP Source** (ou seja, o celular envia áudio para caixas/fones de ouvido Bluetooth, mas não aceita receber áudio de outro celular ou computador).

Para transformar o smartphone em um **Receptor Bluetooth (A2DP Sink)**:
- O perfil de Bluetooth `A2DP_SINK` (Perfil 11 na API do Android) precisa ser inicializado.
- Um registro SDP (*Service Discovery Protocol*) com o UUID oficial `0000110B-0000-1000-8000-00805F9B34FB` precisa ser publicado pelo rádio Bluetooth.
- O fluxo de áudio recebido deve ser capturado e decodificado diretamente para a camada `AudioTrack` da Qualcomm.

---

## 5. Configuração do Bluetooth para Receber Áudio

O nosso aplicativo **DAC Hub Pro** já implementa nativamente essa arquitetura em segundo plano através do `BluetoothSinkManager.java`.

### Passo a Passo para Operar o Receptor:

1. **Abra o DAC Hub Pro no celular:**
   * O rádio Bluetooth será inicializado automaticamente com o nome **`DAC-HiFi-Audio`**.
2. **Coloque o dispositivo em modo visível:**
   * Toque no botão **`TORNAR VISÍVEL PARA PAREAR`** no aplicativo.
   * O celular ficará visível para qualquer outro dispositivo por 300 segundos.
3. **No seu celular emissor (ou notebook / tablet):**
   * Vá em **Configurações > Bluetooth > Parear novo dispositivo**.
   * Selecione **`DAC-HiFi-Audio`** (ou *Redmi Go*).
   * Confirme o código de pareamento na tela.
4. **Pronto!**
   * Ao dar play em qualquer música no seu smartphone emissor, o sinal é enviado via Bluetooth para o Redmi Go, que converte o áudio digitalmente e entrega som de alta fidelidade na **saída analógica P2 (3.5mm)** conectada nas caixas de som!

---

### 📊 Tabela de Propriedades do Sistema (Opcional para ROMs Customizadas)

Se estiver compilando ou customizando uma ROM AOSP/LineageOS para o aparelho, certifique-se de incluir no `build.prop` / `system.prop`:

```properties
# Habilitar Perfil de Receptor Bluetooth A2DP Sink
bluetooth.profile.a2dp.sink.enabled=true
persist.bluetooth.a2dp_sink=true
persist.service.btui.sink=true

# Desabilitar perfis desnecessários para economizar RAM
bluetooth.profile.pan.nap.enabled=false
bluetooth.profile.pan.panu.enabled=false
bluetooth.profile.pbap.server.enabled=false
```
