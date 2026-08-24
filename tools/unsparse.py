import struct
import sys
import os

def simg2img(src_path, dst_path):
    print(f"[*] Convertendo sparse image {src_path} para {dst_path}...")
    with open(src_path, 'rb') as f_in, open(dst_path, 'wb') as f_out:
        header = f_in.read(28)
        magic, major, minor, file_hdr_sz, chunk_hdr_sz, blk_sz, total_blks, total_chunks, crc = struct.unpack('<I4H4I', header)
        
        if magic != 0xED26FF3A:
            print(f"[-] Nao e uma sparse image valida (magic: {hex(magic)}). Copiando direto...")
            f_in.seek(0)
            while chunk := f_in.read(1024*1024):
                f_out.write(chunk)
            return

        print(f"[+] Sparse Image OK: Bloco={blk_sz}B, Total Blocos={total_blks}, Total Chunks={total_chunks}")
        for i in range(total_chunks):
            chunk_header = f_in.read(chunk_hdr_sz)
            chunk_type, reserved, chunk_sz, total_sz = struct.unpack('<2H2I', chunk_header)
            data_sz = chunk_sz * blk_sz
            
            if chunk_type == 0xCAC1: # RAW
                f_out.write(f_in.read(data_sz))
            elif chunk_type == 0xCAC2: # FILL
                fill_val = f_in.read(4)
                f_out.write(fill_val * (data_sz // 4))
            elif chunk_type == 0xCAC3: # DONT CARE
                f_out.seek(f_out.tell() + data_sz)
            elif chunk_type == 0xCAC4: # CRC32
                f_in.read(4)
            else:
                print(f"[-] Tipo de chunk desconhecido: {hex(chunk_type)}")
                break
    print(f"[+] Imagem descompactada com sucesso em: {dst_path}")

if __name__ == '__main__':
    simg2img(sys.argv[1], sys.argv[2])
