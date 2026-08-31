@echo off
setlocal enabledelayedexpansion
title Karaoke IA - Android TV Emulador
color 0E

echo ===================================================
echo     INICIANDO KARAOKE IA (ANDROID TV 1080p)
echo ===================================================
echo.

set SDK_DIR=C:\Users\admin.igor\AppData\Local\Android\Sdk
set EMULATOR=%SDK_DIR%\emulator\emulator.exe
set ADB=%SDK_DIR%\platform-tools\adb.exe
set APK=%~dp0app\build\outputs\apk\debug\app-debug.apk
set AVD_DIR=%USERPROFILE%\.android\avd\AndroidTV_1080p.avd

:: 1. Remove possiveis travas de travamentos anteriores
if exist "%AVD_DIR%\*.lock" (
    echo [*] Limpando travas de inicializacoes anteriores...
    del /f /q "%AVD_DIR%\*.lock" >nul 2>&1
    del /f /q "%AVD_DIR%\hardware-qemu.ini.lock" >nul 2>&1
)

:: 2. Verifica se o emulador ja esta rodando
"%ADB%" devices | findstr /i "emulator" >nul 2>&1
if errorlevel 1 (
    echo [1/4] Abrindo janela da TV...
    start "" "%EMULATOR%" -avd AndroidTV_1080p -gpu auto
) else (
    echo [1/4] Janela da TV ja esta aberta!
)

:: 3. Aguarda o Android TV iniciar completamente
echo [2/4] Conectando a TV e carregando sistema Android...
echo     (Isso leva cerca de 20 a 30 segundos, aguarde...)
"%ADB%" wait-for-device

:waitboot
set BOOT_STATUS=0
for /f "tokens=*" %%a in ('"%ADB%" shell getprop sys.boot_completed 2^>nul') do set BOOT_STATUS=%%a
set BOOT_STATUS=!BOOT_STATUS:~0,1!
if not "!BOOT_STATUS!"=="1" (
    ping -n 3 127.0.0.1 >nul
    goto waitboot
)

:: Aguarda servicos de interface grafica
ping -n 3 127.0.0.1 >nul

:: 4. Instala e garante permissoes e musicas
echo [3/4] Atualizando o aplicativo e carregando catalogo de musicas...
if exist "%APK%" (
    "%ADB%" install -r "%APK%" >nul 2>&1
)
"%ADB%" shell appops set com.araujo.karaoke_ia MANAGE_EXTERNAL_STORAGE allow >nul 2>&1
"%ADB%" shell mkdir -p /sdcard/Download/MUSICAS >nul 2>&1
if exist "%~dp0music\01007.mp4" (
    "%ADB%" push "%~dp0music\01007.mp4" /sdcard/Download/MUSICAS/ >nul 2>&1
    "%ADB%" push "%~dp0music\01007.txt" /sdcard/Download/MUSICAS/ >nul 2>&1
)

:: Fecha popup de sistema da TV se houver
"%ADB%" shell input keyevent KEYCODE_BACK >nul 2>&1

:: 5. Inicia o aplicativo Karaoke IA na tela
echo [4/4] Abrindo o aplicativo Karaoke IA na tela!
"%ADB%" shell am start -n com.araujo.karaoke_ia/.MainActivity

echo.
echo ===================================================
echo   KARAOKE IA PRONTO NA SUA TELA!
echo   - Use as setas do teclado para navegar
echo   - Enter para selecionar / Cantar
echo   - Backspace / ESC para voltar
echo   - Digite os numeros do codigo da musica (ex: 01007)
echo ===================================================
echo.
echo Pressione qualquer tecla para fechar esta janela do terminal...
pause >nul
