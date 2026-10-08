param([switch]$Restart)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
try {
    if (-not (Test-Path -LiteralPath $adb) -or -not (Test-Path -LiteralPath $emulator)) { throw 'Android SDK nao encontrado.' }
    $ErrorActionPreference = 'Continue'
    $state = & $adb -e get-state 2>&1
    $connected = $LASTEXITCODE -eq 0 -and "$state" -eq 'device'
    $ErrorActionPreference = 'Stop'
    if ($Restart -and $connected) {
        $name = & $adb -e emu avd name 2>&1
        if ($name -notcontains 'Television_1080p') { throw 'Outro emulador esta aberto. Feche-o antes de iniciar o perfil leve.' }
        Write-Host 'Reiniciando Television_1080p com o perfil leve...'
        & $adb -e emu kill | Out-Host
        $shutdownDeadline = [DateTime]::UtcNow.AddSeconds(45)
        do {
            Start-Sleep -Seconds 3
            $emulatorStillRunning = Get-Process -Name 'qemu-system-x86_64', 'emulator' -ErrorAction SilentlyContinue
        } while ($emulatorStillRunning -and [DateTime]::UtcNow -lt $shutdownDeadline)
        if ($emulatorStillRunning) { throw 'O emulador ainda esta encerrando. Aguarde e abra o perfil leve novamente.' }
        $connected = $false
    }
    if (-not $connected) {
        if (Get-Process -Name 'qemu-system-x86_64', 'emulator' -ErrorAction SilentlyContinue) {
            Write-Host 'O emulador ja esta iniciando; aguardando a conexao ADB.'
        } else {
            Write-Host 'Iniciando Android TV leve: 960x540, 2 nucleos, 1536 MB de RAM.'
            Start-Process -FilePath $emulator -ArgumentList '-avd Television_1080p -cores 2 -memory 1536 -skin 960x540 -dpi-device 240 -gpu swiftshader_indirect -no-boot-anim -no-snapshot' -WindowStyle Normal -RedirectStandardOutput (Join-Path $root 'emulador-leve.log') -RedirectStandardError (Join-Path $root 'emulador-leve-error.log')
        }
    } else { Write-Host 'Usando o emulador ja aberto.' }
    Write-Host 'Aguardando o Android. Nao sera feita compilacao nem copia de musicas.'
    $deadline = [DateTime]::UtcNow.AddMinutes(5)
    do {
        $ErrorActionPreference = 'Continue'
        $boot = & $adb -e shell getprop sys.boot_completed 2>&1
        $bootReady = $LASTEXITCODE -eq 0 -and "$boot".Trim() -eq '1'
        $ErrorActionPreference = 'Stop'
        if ($bootReady) { break }
        Start-Sleep -Seconds 3
    } while ([DateTime]::UtcNow -lt $deadline)
    if ("$boot".Trim() -ne '1') { throw 'O Android nao terminou o boot. Consulte emulador-leve.log.' }
    Start-Sleep -Seconds 15
    foreach ($animation in @('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale')) {
        & $adb -e shell settings put global $animation 0 | Out-Null
    }
    & $adb -e shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
    & $adb -e shell input keyevent 224 | Out-Null
    & $adb -e shell am start -W -n com.araujo.karaoke_ia/.MainActivity | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel abrir o app. Execute testar_capas_karaoke.bat para instalar o APK.' }
    Write-Host 'Karaoke aberto. Use as setas e Enter para navegar.'
} catch {
    Write-Host "[ERRO] $($_.Exception.Message)"
    exit 1
}
