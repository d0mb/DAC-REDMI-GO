$shell = New-Object -ComObject Shell.Application
$thisPC = $shell.Namespace(17)
foreach ($item in $thisPC.Items()) {
    if ($item.Name -like "*Redmi*") {
        $storage = $item.GetFolder.Items() | Select-Object -First 1
        foreach ($f in $storage.GetFolder.Items()) {
            if ($f.Name -match "Download") {
                $f.GetFolder.CopyHere("C:\Projetos\DAC\dist\DAC_Hub_Pro.apk", 16)
                Write-Host "[+] APK copiado direto para a pasta Download do Redmi Go!"
                break
            }
        }
    }
}
