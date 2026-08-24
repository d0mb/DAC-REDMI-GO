# Guia de Instalação e Uso • DAC Hub Pro

Este guia fornece instruções para instalar, configurar e operar a Central DAC no celular e em qualquer computador Windows.

---

## 📱 1. Configuração do Celular (Receptor)

### Pré-requisitos:
* Celular Android (Android 8.1+ ou qualquer dispositivo com saída P2/USB-C).
* Conexão na mesma rede Wi-Fi que os computadores/transmissores.

### Instalação:
1. Copie o arquivo `dist/DAC_Hub_Pro.apk` para o celular e instale.
2. Abra o aplicativo **DAC Hub Pro**.
3. Conecte o cabo P2 (3.5mm) na saída de fone do celular e na entrada auxiliar da sua **caixa de som**.
4. O app mostrará na tela o status **ONLINE** e o endereço IP (ex: `http://192.168.15.12:8080`).

---

## 💻 2. Transmissão de Áudio pelo Computador (Windows)

### Opção A: Executável Portátil (Recomendado para Qualquer PC)
1. Baixe ou copie o arquivo `DAC_Hub_Transmitter_Portable.exe` (localizado na pasta `dist/` ou na raiz).
2. Dê dois cliques para executar.
3. O transmissor **descobre o IP do seu celular na rede automaticamente** e inicia a transmissão de todo o som do Windows em tempo real.

### Opção B: Execução via Python (Desenvolvedores)
1. Instale as dependências:
   ```bash
   pip install -r windows-transmitter/requirements.txt
   ```
2. Execute o script:
   ```bash
   python windows-transmitter/src/DACHubStreamer.py
   ```

---

## 🌐 3. Painel Web de Controle

Em qualquer navegador (PC, Mac, iPhone ou outro celular conectado no mesmo Wi-Fi), acesse:
```
http://[IP-DO-CELULAR]:8080
```
* **Recursos do Painel Web:**
  - Status da transmissão ao vivo.
  - VU Meter animado mostrando o nível do sinal de áudio.
  - Botão para disparar tom de teste de 440Hz / 880Hz nas caixas.
