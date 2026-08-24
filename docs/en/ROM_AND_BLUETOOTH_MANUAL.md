# 🛠️ ROM Flashing, Root & Bluetooth A2DP Sink Manual

This technical manual details the complete procedure for bootloader unlocking, stock ROM de-encryption, Magisk root installation, extreme debloating, and Bluetooth **A2DP Sink (Audio Receiver)** configuration on the **Xiaomi Redmi Go (`tiare`)** or similar Android devices.

---

## 📑 Table of Contents
1. [Bootloader Unlock & Custom Recovery (TWRP/PBRP)](#1-bootloader-unlock--custom-recovery)
2. [Fixing Bootloop & Encryption (FSTAB Patching)](#2-fixing-bootloop--encryption-fstab-patching)
3. [Magisk Root & Extreme System Debloat](#3-magisk-root--extreme-system-debloat)
4. [Understanding Android Bluetooth A2DP Sink](#4-understanding-android-bluetooth-a2dp-sink)
5. [Configuring Bluetooth for Audio Reception](#5-configuring-bluetooth-for-audio-reception)

---

## 1. Bootloader Unlock & Custom Recovery

### Step 1.1: Enable Developer Options
1. On the phone, navigate to **Settings > System > About phone**.
2. Tap **Build number** 7 times until you see *"You are now a developer!"*.
3. Go back to **Developer options** and enable:
   - **OEM unlocking**.
   - **USB debugging**.

### Step 1.2: Unlock Bootloader via Fastboot
1. Connect the phone to your PC via USB.
2. Reboot into Fastboot mode:
   ```bash
   adb reboot bootloader
   ```
3. Execute the unlock command:
   ```bash
   fastboot oem unlock
   # or for full unlock:
   fastboot oem unlock-go
   ```
4. Verify unlock status:
   ```bash
   fastboot getvar unlocked
   # Expected output: unlocked: yes
   ```

### Step 1.3: Flash Custom Recovery (PitchBlack / TWRP)
1. Flash and boot into the custom recovery image:
   ```bash
   fastboot flash recovery recovery_pitchblack.img
   fastboot boot recovery_pitchblack.img
   ```

---

## 2. Fixing Bootloop & Encryption (FSTAB Patching)

The factory Redmi Go stock ROM enforces `forceencrypt=footer` on `/data`, causing a permanent bootloop if userdata is wiped or formatted in EXT4/F2FS without the proprietary encryption key.

### How to Fix:
1. In TWRP / PitchBlack Recovery, mount `/vendor`.
2. Edit `/vendor/etc/fstab.qcom`:
   - Locate the mount rule for `userdata`.
   - Replace `forceencrypt=footer` with `encryptable=footer`.
3. In TWRP, format `/data` cleanly in EXT4:
   ```bash
   mke2fs -t ext4 -b 4096 /dev/block/bootdevice/by-name/userdata
   ```
4. Reboot the device. The system will boot into the welcome setup screen in under 45 seconds.

---

## 3. Magisk Root & Extreme System Debloat

### Step 3.1: Install Magisk 23.0
1. Flash `Magisk-v23.0.zip` in TWRP (or patch `boot.img` via the Magisk app).
2. Install the `Magisk.apk` manager application.
3. Grant permanent Superuser access to the Shell (`su`).

### Step 3.2: Run the Extreme Debloat Script
To maximize available RAM and eliminate background CPU load:

1. Push the debloat script:
   ```bash
   adb push scripts/debloat.sh /data/local/tmp/
   ```
2. Execute as root:
   ```bash
   adb shell "su -c 'sh /data/local/tmp/debloat.sh'"
   ```
3. This disables 35+ bloatware packages (Facebook services, Xiaomi telemetry, heavy Google sync), increasing free RAM from ~40 MB to **over 230 MB free**.

---

## 4. Understanding Android Bluetooth A2DP Sink

By default, standard Android builds only expose the **A2DP Source** profile (acting as a sender to wireless headphones), and disable the **A2DP Sink** role (acting as a wireless audio receiver).

To enable A2DP Sink audio reception:
- Initialize the hidden `A2DP_SINK` profile (Profile ID 11 in Android Oreo/Pie).
- Register an SDP (*Service Discovery Protocol*) service record with UUID `0000110B-0000-1000-8000-00805F9B34FB`.
- Pipe the incoming PCM stream directly to Qualcomm's `AudioTrack` layer.

---

## 5. Configuring Bluetooth for Audio Reception

The **DAC Hub Pro** application natively handles this architecture through `BluetoothSinkManager.java`.

### Operation Steps:
1. **Open DAC Hub Pro on the phone:**
   * The Bluetooth subsystem initializes as **`DAC-HiFi-Audio`**.
2. **Make the device discoverable:**
   * Tap **`TORNAR VISÍVEL PARA PAREAR`** in the app.
3. **Pair from your mobile device or laptop:**
   * Go to **Bluetooth Settings > Pair new device**.
   * Select **`DAC-HiFi-Audio`** (or *Redmi Go*).
   * Accept the pairing prompt.
4. **Enjoy your music!**
   * All audio from your sending device will now play through the phone's **3.5mm analog output** into your speakers!

---

### 📊 System Properties Table (Optional for Custom ROM Developers)

When compiling custom AOSP or LineageOS builds for this device, ensure the following properties are in `build.prop` / `system.prop`:

```properties
# Enable Bluetooth A2DP Sink Receiver Profile
bluetooth.profile.a2dp.sink.enabled=true
persist.bluetooth.a2dp_sink=true
persist.service.btui.sink=true

# Disable unused profiles to conserve RAM
bluetooth.profile.pan.nap.enabled=false
bluetooth.profile.pan.panu.enabled=false
bluetooth.profile.pbap.server.enabled=false
```
