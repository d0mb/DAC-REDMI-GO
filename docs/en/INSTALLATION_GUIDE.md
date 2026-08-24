# Installation and User Guide • DAC Hub Pro

This guide provides complete instructions to install, configure, and operate DAC Hub Pro on your Android phone and Windows PCs.

---

## 📱 1. Android Phone Setup (Receiver)

### Prerequisites:
* Android smartphone (Android 8.1+ with 3.5mm headphone jack or USB-C audio).
* Connected to the same Wi-Fi network as the transmitter PCs.

### Installation:
1. Transfer and install **`dist/DAC_Hub_Pro.apk`** on your smartphone.
2. Open the **DAC Hub Pro** app.
3. Plug a 3.5mm (P2) AUX audio cable from the phone's headphone jack into your **speakers** or amplifier.
4. The application will show **ONLINE** along with its local IP address (e.g. `http://192.168.15.12:8080`).

---

## 💻 2. Windows PC Streaming (Transmitter)

### Option A: Portable Single-File Executable (Recommended for Any PC)
1. Run **`dist/DAC_Hub_Transmitter_Portable.exe`** (or from the project root).
2. The transmitter will **automatically scan your local network**, find the DAC receiver, and start streaming all Windows system audio immediately.

### Option B: Run via Python (Developers)
1. Install dependencies:
   ```bash
   pip install -r windows-transmitter/requirements.txt
   ```
2. Launch the script:
   ```bash
   python windows-transmitter/src/DACHubStreamer.py
   ```

---

## 🌐 3. Web Dashboard

Point any web browser (PC, Mac, iPhone, or secondary device on the local network) to:
```
http://[PHONE-IP]:8080
```
* **Web Dashboard Features:**
  - Live audio streaming status and active transmitter IP.
  - Dynamic animated VU Meter bar.
  - Instant 440Hz / 880Hz audio test tone button.

---

## 🔵 4. Bluetooth Operation (A2DP Sink Mode for Mobile Devices)

To use the phone as a **traditional Bluetooth receiver** for other phones and tablets:

### Instructions:
1. In the **DAC Hub Pro** app on the phone, tap **`TORNAR VISÍVEL PARA PAREAR`** (*Make Discoverable*).
2. On your iPhone, Android smartphone, or tablet:
   * Open **Settings > Bluetooth > Pair new device**.
   * Select **`DAC-HiFi-Audio`** (or *Redmi Go*).
   * Confirm the pairing request.
3. **Done!** Any music or audio played on your phone will now stream via Bluetooth through the 3.5mm headphone output connected to your speakers.

> 📌 **Performance Tip:** For computers, always use the **Wi-Fi Portable Transmitter** (`DAC_Hub_Transmitter_Portable.exe`) for uncompressed lossless audio (1411 kbps PCM) and whole-home range. Bluetooth is ideal for guests and quick mobile pairing.
