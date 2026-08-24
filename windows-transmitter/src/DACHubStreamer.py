import sys
import time
import socket
import threading
import urllib.request
import numpy as np
import sounddevice as sd

DEFAULT_IP = "192.168.15.12"
DEFAULT_PORT = 8080

def test_dac_ip(ip, port):
    try:
        req = urllib.request.Request(f"http://{ip}:{port}/api/status", method='GET')
        with urllib.request.urlopen(req, timeout=1.5) as resp:
            if resp.status == 200:
                return True
    except Exception:
        pass
    return False

def discover_dac_ip():
    print("[*] Verificando conexão rápida com o DAC...")
    if test_dac_ip(DEFAULT_IP, DEFAULT_PORT):
        return DEFAULT_IP
    
    print("[*] Procurando o DAC na rede local...")
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        
        subnet_prefix = ".".join(local_ip.split(".")[:3])
        print(f"[*] Escaneando faixa de rede {subnet_prefix}.1 até {subnet_prefix}.254...")
        
        found_ip = None
        def check_host(host_ip):
            nonlocal found_ip
            if found_ip: return
            if test_dac_ip(host_ip, DEFAULT_PORT):
                found_ip = host_ip

        threads = []
        for i in range(1, 255):
            t = threading.Thread(target=check_host, args=(f"{subnet_prefix}.{i}",))
            threads.append(t)
            t.start()
            if len(threads) >= 50:
                for th in threads: th.join()
                threads = []
                if found_ip: break
                
        for th in threads: th.join()
        if found_ip:
            return found_ip
    except Exception as e:
        print(f"[-] Erro no scan: {e}")
        
    return None

def get_wasapi_loopback():
    wasapi_info = sd.query_hostapis()
    wasapi_api_index = None
    for i, api in enumerate(wasapi_info):
        if 'WASAPI' in api['name']:
            wasapi_api_index = i
            break

    if wasapi_api_index is None:
        return None

    default_speakers = wasapi_info[wasapi_api_index]['default_output']
    speakers_info = sd.query_devices(default_speakers)
    return default_speakers, speakers_info['name']

def main():
    print("\n" + "=" * 62)
    print("      DAC HUB PRO • TRANSMISSOR DE ÁUDIO RESILIENTE (PC)")
    print("=" * 62)
    
    target_ip = discover_dac_ip()
    if not target_ip:
        print(f"\n[!] Não foi possível localizar o DAC automaticamente.")
        custom_ip = input(f"[?] Digite o IP do celular DAC [padrão: {DEFAULT_IP}]: ").strip()
        target_ip = custom_ip if custom_ip else DEFAULT_IP
    else:
        print(f"[+] DAC localizado com sucesso no IP: {target_ip}")

    loopback_info = get_wasapi_loopback()
    if not loopback_info:
        print("[-] Erro: Nenhum dispositivo de áudio WASAPI encontrado no Windows.")
        input("Pressione Enter para sair...")
        return
        
    dev_id, dev_name = loopback_info
    print(f"[*] Dispositivo de saída de áudio capturado: {dev_name}")
    print(f"[*] Modo de transmissão: PCM 44.1kHz • 16-Bit Lossless (1411 kbps)")

    sample_rate = 44100
    channels = 2
    block_size = 1024
    retry_delay = 1

    while True:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(4.0)
        try:
            print(f"\n[*] Conectando ao DAC em http://{target_ip}:{DEFAULT_PORT}...")
            s.connect((target_ip, DEFAULT_PORT))
            header = f"POST /stream HTTP/1.1\r\nHost: {target_ip}:{DEFAULT_PORT}\r\nTransfer-Encoding: chunked\r\n\r\n"
            s.sendall(header.encode())
            s.settimeout(None)
            retry_delay = 1 # Reset delay on success
            
            print("\n" + "#" * 62)
            print("  🟢 TRANSMISSÃO ATIVA E EM TEMPO REAL!")
            print(f"  🔊 Todo o som deste computador está saindo nas caixas de som!")
            print(f"  📡 Conectado a: {target_ip}:{DEFAULT_PORT}")
            print("  ⚡ Auto-reconexão inteligente e silenciosa ativa.")
            print("  ⌨️  Pressione Ctrl+C a qualquer momento para encerrar.")
            print("#" * 62 + "\n")

            def audio_callback(indata, frames, time_info, status):
                pcm16 = (indata * 32767.0).astype(np.int16)
                try:
                    s.sendall(pcm16.tobytes())
                except Exception:
                    pass

            with sd.InputStream(device=dev_id,
                                channels=channels,
                                samplerate=sample_rate,
                                blocksize=block_size,
                                extra_settings=sd.WasapiSettings(exclusive=False, loopback=True),
                                callback=audio_callback):
                while True:
                    time.sleep(0.5)

        except KeyboardInterrupt:
            print("\n[*] Transmissão encerrada pelo usuário.")
            try: s.close()
            except Exception: pass
            break
        except Exception as e:
            print(f"[-] Conexão com o DAC perdida ({e}).")
            print(f"[*] Reconectando automaticamente em {retry_delay}s... (Pressione Ctrl+C para sair)")
            try: s.close()
            except Exception: pass
            
            # Se a conexão falhar repetidas vezes, tentar redescobrir o IP caso o roteador tenha mudado
            if retry_delay >= 8:
                new_ip = discover_dac_ip()
                if new_ip: target_ip = new_ip
                
            time.sleep(retry_delay)
            retry_delay = min(10, retry_delay + 2)

if __name__ == '__main__':
    main()
