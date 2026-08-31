# Script PowerShell para iniciar o Karaoke IA no Emulador Android TV
$ErrorActionPreference = "SilentlyContinue"

Write-Host "===================================================" -ForegroundColor Yellow
Write-Host "     INICIANDO KARAOKE IA (ANDROID TV 1080p)" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Yellow
Write-Host ""

$SdkDir = "C:\Users\admin.igor\AppData\Local\Android\Sdk"
$Emulator = "$SdkDir\emulator\emulator.exe"
$Adb = "$SdkDir\platform-tools\adb.exe"
$Apk = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"
$AvdLock = "$env:USERPROFILE\.android\avd\AndroidTV_1080p.avd\*.lock"

# 1. Limpa travas de sessões anteriores
Remove-Item -Path $AvdLock -Force -ErrorAction SilentlyContinue

# 2. Verifica se o emulador já está aberto
$Devices = & $Adb devices 2>$null
if ($Devices -notmatch "emulator") {
    Write-Host "[1/4] Abrindo janela da TV..." -ForegroundColor Green
    Start-Process -FilePath $Emulator -ArgumentList "-avd AndroidTV_1080p -gpu auto"
} else {
    Write-Host "[1/4] Janela da TV já está aberta!" -ForegroundColor Green
}

# 3. Aguarda o Android TV inicializar
Write-Host "[2/4] Conectando a TV e aguardando inicialização do Android..." -ForegroundColor Yellow
& $Adb wait-for-device

while ((& $Adb shell getprop sys.boot_completed 2>$null).Trim() -ne "1") {
    Start-Sleep -Seconds 2
}
Start-Sleep -Seconds 3

# 4. Instala e carrega músicas
Write-Host "[3/4] Atualizando aplicativo e músicas..." -ForegroundColor Yellow
if (Test-Path $Apk) {
    & $Adb install -r $Apk 2>$null
}
& $Adb shell appops set com.araujo.karaoke_ia MANAGE_EXTERNAL_STORAGE allow 2>$null
& $Adb shell mkdir -p /sdcard/Download/MUSICAS 2>$null

$SampleMp4 = "$PSScriptRoot\music\01007.mp4"
$SampleTxt = "$PSScriptRoot\music\01007.txt"
if (Test-Path $SampleMp4) {
    & $Adb push $SampleMp4 /sdcard/Download/MUSICAS/ 2>$null
    & $Adb push $SampleTxt /sdcard/Download/MUSICAS/ 2>$null
}

& $Adb shell input keyevent KEYCODE_BACK 2>$null

# 5. Inicia o Karaokê IA
Write-Host "[4/4] Abrindo Karaokê IA na tela!" -ForegroundColor Green
& $Adb shell am start -n com.araujo.karaoke_ia/.MainActivity

Write-Host ""
Write-Host "===================================================" -ForegroundColor Green
Write-Host "  KARAOKE IA PRONTO NA SUA TELA!" -ForegroundColor White
Write-Host "  - Use as setas do teclado para navegar" -ForegroundColor Gray
Write-Host "  - Enter para selecionar / Cantar" -ForegroundColor Gray
Write-Host "  - Backspace / ESC para voltar" -ForegroundColor Gray
Write-Host "  - Digite os números do código da música" -ForegroundColor Gray
Write-Host "===================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Pressione Enter para fechar esta janela..."
Read-Host
