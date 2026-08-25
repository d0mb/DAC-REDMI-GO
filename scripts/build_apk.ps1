$ErrorActionPreference = "Stop"

$projectRoot = "C:\Projetos\DAC"
$appDir = Join-Path $projectRoot "android-app"
$toolsDir = Join-Path $projectRoot "tools"
$jdkBin = Join-Path $toolsDir "jdk\bin"
$java = Join-Path $jdkBin "java.exe"
$javac = Join-Path $jdkBin "javac.exe"
$jar = Join-Path $jdkBin "jar.exe"

$androidJar = "C:\Projetos\DAC\tools\android-sdk\platforms\android-28\android.jar"
$buildToolsDir = "C:\Projetos\DAC\tools\android-sdk\build-tools\30.0.3"
$d8Jar = Join-Path $buildToolsDir "lib\d8.jar"
$aapt2 = Join-Path $buildToolsDir "aapt2.exe"
$zipalign = Join-Path $buildToolsDir "zipalign.exe"
$apksignerJar = Join-Path $buildToolsDir "lib\apksigner.jar"

$outDir = Join-Path $appDir "build"
if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $outDir "compiled_res") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $outDir "gen") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $outDir "classes") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot "dist") | Out-Null

Write-Host "1/6 - Compilando recursos XML (aapt2 compile)..."
$resDir = Join-Path $appDir "src\main\res"
& $aapt2 compile --dir $resDir -o (Join-Path $outDir "compiled_res\res.zip")

Write-Host "2/6 - Vinculando recursos e gerando R.java (aapt2 link)..."
$manifest = Join-Path $appDir "src\main\AndroidManifest.xml"
$unalignedApk = Join-Path $outDir "unaligned.apk"
& $aapt2 link -I $androidJar --manifest $manifest -o $unalignedApk --java (Join-Path $outDir "gen") (Join-Path $outDir "compiled_res\res.zip") --auto-add-overlay

Write-Host "3/6 - Compilando código-fonte Java com compatibilidade Java 8 (javac)..."
$javaFiles = Get-ChildItem -Path (Join-Path $appDir "src\main\java"), (Join-Path $outDir "gen") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -source 8 -target 8 -cp "$androidJar" -d (Join-Path $outDir "classes") $javaFiles

Write-Host "4/6 - Convertendo bytecode para DEX (D8)..."
$classFiles = Get-ChildItem -Path (Join-Path $outDir "classes") -Recurse -Filter "*.class" | ForEach-Object { $_.FullName }
& $java -cp $d8Jar com.android.tools.r8.D8 --release --output $outDir --lib $androidJar $classFiles

Write-Host "5/6 - Adicionando classes.dex e bibliotecas C++ (jniLibs) ao APK..."
$classesDex = Join-Path $outDir "classes.dex"
if (-not (Test-Path $classesDex)) {
    throw "classes.dex não foi gerado!"
}

# Copiar jniLibs para outDir/lib
$jniLibsDir = Join-Path $appDir "src\main\jniLibs"
if (Test-Path $jniLibsDir) {
    Copy-Item -Path $jniLibsDir -Destination (Join-Path $outDir "lib") -Recurse -Force
}

Push-Location $outDir
& $jar -uf $unalignedApk classes.dex
if (Test-Path (Join-Path $outDir "lib")) {
    & $jar -uf $unalignedApk lib
}
Pop-Location

Write-Host "6/6 - Alinhando e assinando APK final..."
$alignedApk = Join-Path $outDir "aligned.apk"
& $zipalign -v -p 4 $unalignedApk $alignedApk

$finalApk = Join-Path $projectRoot "dist\DAC_Hub_Pro.apk"
$debugKey = Join-Path $toolsDir "debug.keystore"

if (-not (Test-Path $debugKey)) {
    $keytool = Join-Path $jdkBin "keytool.exe"
    & $keytool -genkey -v -keystore $debugKey -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
}

& $java -jar $apksignerJar sign --ks $debugKey --ks-pass pass:android --out $finalApk $alignedApk

Write-Host "`n========================================================"
Write-Host "SUCESSO! APK GERADO COM EXITO EM:"
Write-Host $finalApk
Write-Host "========================================================"
