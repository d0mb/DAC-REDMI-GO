import os
import subprocess
import urllib.request
import zipfile
import io

BASE_DIR = r"C:\Projetos\DAC\tools"
SDK_DIR = os.path.join(BASE_DIR, "android-sdk")
JDK_DIR = os.path.join(BASE_DIR, "jdk")

def download_and_extract(url, target_dir, desc):
    print(f"[*] Baixando {desc}...")
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as resp:
        data = resp.read()
    print(f"[+] Extraindo {desc}...")
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        z.extractall(target_dir)
    print(f"[+] {desc} pronto!")

def main():
    os.makedirs(BASE_DIR, exist_ok=True)
    os.makedirs(SDK_DIR, exist_ok=True)
    os.makedirs(JDK_DIR, exist_ok=True)

    # 1. OpenJDK
    if not os.path.exists(os.path.join(JDK_DIR, "lib", "jvm.cfg")) and not os.path.exists(os.path.join(JDK_DIR, "lib", "amd64", "jvm.cfg")):
        import shutil
        shutil.rmtree(JDK_DIR, ignore_errors=True)
        shutil.rmtree(os.path.join(BASE_DIR, "tmp_jdk"), ignore_errors=True)
        jdk_url = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.10%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.10_7.zip"
        download_and_extract(jdk_url, os.path.join(BASE_DIR, "tmp_jdk"), "OpenJDK 17")
        root = [d for d in os.listdir(os.path.join(BASE_DIR, "tmp_jdk")) if os.path.isdir(os.path.join(BASE_DIR, "tmp_jdk", d))][0]
        src = os.path.join(BASE_DIR, "tmp_jdk", root)
        import shutil
        shutil.copytree(src, JDK_DIR, dirs_exist_ok=True)
        shutil.rmtree(os.path.join(BASE_DIR, "tmp_jdk"), ignore_errors=True)

    # 2. Command Line Tools & SDK
    cmdline_dir = os.path.join(SDK_DIR, "cmdline-tools", "latest")
    if not os.path.exists(os.path.join(cmdline_dir, "bin", "sdkmanager.bat")):
        os.makedirs(os.path.join(SDK_DIR, "cmdline-tools"), exist_ok=True)
        cmd_url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
        download_and_extract(cmd_url, os.path.join(BASE_DIR, "tmp_cmd"), "Command Line Tools")
        src_cmd = os.path.join(BASE_DIR, "tmp_cmd", "cmdline-tools")
        import shutil
        shutil.copytree(src_cmd, cmdline_dir, dirs_exist_ok=True)
        shutil.rmtree(os.path.join(BASE_DIR, "tmp_cmd"), ignore_errors=True)

    # Instalar build-tools e platforms via sdkmanager
    sdkmanager = os.path.join(cmdline_dir, "bin", "sdkmanager.bat")
    env = os.environ.copy()
    env["JAVA_HOME"] = JDK_DIR
    env["PATH"] = os.path.join(JDK_DIR, "bin") + ";" + env.get("PATH", "")

    print("[*] Aceitando licenças do Android SDK...")
    p = subprocess.Popen([sdkmanager, "--sdk_root=" + SDK_DIR, "--licenses"], stdin=subprocess.PIPE, text=True, env=env)
    p.communicate(input="y\ny\ny\ny\ny\ny\ny\n")

    print("[*] Instalando build-tools;30.0.3 e platforms;android-28...")
    subprocess.run([sdkmanager, "--sdk_root=" + SDK_DIR, "build-tools;30.0.3", "platforms;android-28"], env=env, check=True)

    print("\n[+] Ambiente Android Build configurado com sucesso!")

if __name__ == '__main__':
    main()
