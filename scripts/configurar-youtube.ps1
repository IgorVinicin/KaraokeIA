param([string]$Serial = 'emulator-5554', [switch]$NoRestart)
$ErrorActionPreference = 'Stop'
$youtubeProject = Split-Path $PSScriptRoot -Parent
$youtubeEnvPath = Join-Path $youtubeProject 'app/.env'
if (!(Test-Path -LiteralPath $youtubeEnvPath)) { throw 'Crie app/.env com YOUTUBE_KEY=SUA_CHAVE.' }
$youtubeEnvText = Get-Content -LiteralPath $youtubeEnvPath -Raw
if ($youtubeEnvText -notmatch '(?m)^\s*YOUTUBE_(?:API_)?KEY\s*=\s*["'']?(AIza[A-Za-z0-9_-]{35})["'']?\s*$') {
    throw 'A chave em app/.env está ausente ou com formato inválido.'
}
$youtubeDeviceConfig = 'YOUTUBE_KEY=' + $matches[1]
$youtubeAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
# Send through stdin: the key never appears in command arguments or a shared device file.
$youtubeDeviceConfig | & $youtubeAdb -s $Serial shell "run-as com.araujo.karaoke_ia sh -c 'umask 077; mkdir -p no_backup; cat > no_backup/youtube_config'"
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível configurar o app. Instale o APK debug e confira o dispositivo ADB.' }
if (!$NoRestart) {
    & $youtubeAdb -s $Serial shell am force-stop com.araujo.karaoke_ia
    & $youtubeAdb -s $Serial shell am start -n com.araujo.karaoke_ia/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível iniciar o app.' }
}
Write-Output 'YouTube configurado somente neste aparelho. A chave não está incluída no APK.'
