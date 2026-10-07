param([string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
$ErrorActionPreference = 'Stop'
$proxyScript = Join-Path $PSScriptRoot 'controle-qr-proxy.cjs'
$nodeExe = (Get-Command node -ErrorAction Stop).Source
$lanAddress = Get-NetIPConfiguration | Where-Object {
    $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' -and
    $_.InterfaceAlias -notmatch 'vEthernet|Virtual|VPN'
} | Sort-Object @{Expression={if ($_.InterfaceAlias -match 'Wi-Fi|Wireless') {0} else {1}}} |
    ForEach-Object {$_.IPv4Address.IPAddress} | Select-Object -First 1
if (!$lanAddress) { throw 'Nenhum IP de rede local encontrado. Conecte o PC ao Wi-Fi ou Ethernet.' }
& $Adb -e forward tcp:18765 tcp:8765
if ($LASTEXITCODE -ne 0) { throw 'Falha ao encaminhar a porta do emulador.' }
# Stop only earlier instances of this project's proxy, allowing safe repeated launches.
Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Where-Object {
    $_.CommandLine -and $_.CommandLine.Contains($proxyScript)
} | ForEach-Object { Stop-Process -Id $_.ProcessId -ErrorAction SilentlyContinue }
$logDirectory = Split-Path $PSScriptRoot
Start-Process -FilePath $nodeExe -ArgumentList @(('"' + $proxyScript + '"'), $lanAddress) -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $logDirectory 'controle-qr.log') `
    -RedirectStandardError (Join-Path $logDirectory 'controle-qr-error.log')
Start-Sleep -Seconds 2
$remoteAddress = "http://${lanAddress}:8765"
& $Adb -e shell am force-stop com.araujo.karaoke_ia
& $Adb -e shell am start -W -f 0x10008000 -n com.araujo.karaoke_ia/.MainActivity --es remote_url $remoteAddress
if ($LASTEXITCODE -ne 0) { throw 'Falha ao abrir o app com o endereço do controle.' }
$ready = $false
for ($attempt = 1; $attempt -le 30; $attempt++) {
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "$remoteAddress/api/queue" -TimeoutSec 3 | Out-Null
        $ready = $true
        break
    } catch {
        if ($attempt % 5 -eq 0) { Write-Host "Aguardando o controle do app... tentativa $attempt/30" }
        Start-Sleep -Seconds 2
    }
}
if (!$ready) { throw 'O controle do app não respondeu. Verifique karaoke-logcat.txt e controle-qr-error.log.' }
Write-Host "QR Code do controle: $remoteAddress"
Write-Host 'Use o celular na mesma rede do PC. No Firewall do Windows, permita o Node.js na rede usada para o teste.'
