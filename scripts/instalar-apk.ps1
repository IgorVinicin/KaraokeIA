param(
    [Parameter(Mandatory = $true)][string]$Adb,
    [Parameter(Mandatory = $true)][string]$Apk,
    [int]$ReadyTimeoutSeconds = 180
)
$ErrorActionPreference = 'Stop'

function Invoke-Adb([string]$Arguments, [int]$TimeoutSeconds = 15) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $Adb
    $info.Arguments = "-e $Arguments"
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            $process.Kill()
            $process.WaitForExit()
            return @{ Code = -1; Output = 'Tempo limite de comunicacao com o emulador.' }
        }
        return @{ Code = $process.ExitCode; Output = ($stdout.Result + $stderr.Result).Trim() }
    } finally { $process.Dispose() }
}

function Wait-PackageService {
    $deadline = [DateTime]::UtcNow.AddSeconds($ReadyTimeoutSeconds)
    $stableSince = $null
    Write-Host 'Aguardando o servico de instalacao do Android...'
    do {
        $boot = Invoke-Adb 'shell getprop sys.boot_completed'
        $result = Invoke-Adb 'shell pm path android'
        $storage = Invoke-Adb 'shell sm list-volumes all'
        if ($boot.Code -eq 0 -and $boot.Output -eq '1' -and
            $result.Code -eq 0 -and $result.Output -match '(?m)^package:.*framework-res\.apk\s*$' -and
            $storage.Code -eq 0 -and $storage.Output -match '(?m)^private\s+mounted\s+') {
            if ($null -eq $stableSince) { $stableSince = [DateTime]::UtcNow }
            if (([DateTime]::UtcNow - $stableSince).TotalSeconds -ge 30) { return }
        } else { $stableSince = $null }
        Start-Sleep -Seconds 3
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "O servico de pacotes nao ficou pronto. Ultima resposta: $($result.Output)"
}

try {
    if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) { throw 'ADB nao encontrado.' }
    if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw 'APK nao encontrado.' }
    Wait-PackageService
    # Evita o streamed install que apresentou Broken pipe neste emulador.
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        Write-Host "Instalando APK sem streaming (tentativa $attempt/2)..."
        $result = Invoke-Adb ('install --no-streaming -r "' + $Apk + '"') 180
        Write-Host $result.Output
        if ($result.Code -eq 0 -and $result.Output -match '(?m)^Success\s*$') { exit 0 }
        if ($result.Output -match 'INSTALL_FAILED_') { throw 'O Android rejeitou o APK; consulte o motivo acima.' }
        if ($attempt -lt 2) { Start-Sleep -Seconds 10; Wait-PackageService }
    }
    throw 'Falha de comunicacao ao instalar. Confira se o emulador continua aberto e consulte emulador-tv.log.'
} catch {
    Write-Host "[ERRO] $($_.Exception.Message)"
    if (Test-Path -LiteralPath $Adb -PathType Leaf) {
        $diagnostic = Invoke-Adb 'logcat -b crash -d -t 150'
        $log = Join-Path (Split-Path $PSScriptRoot -Parent) 'instalacao-crash.log'
        $diagnostic.Output | Set-Content -LiteralPath $log -Encoding UTF8
        Write-Host "Diagnostico do Android: $log"
    }
    exit 1
}
