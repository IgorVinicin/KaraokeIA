@echo off
setlocal enabledelayedexpansion
title Karaoke IA - Teste de paginacao
color 0B

set "ROOT=%~dp0"
set "SDK_DIR=C:\Users\admin.igor\AppData\Local\Android\Sdk"
set "ADB=%SDK_DIR%\platform-tools\adb.exe"
set "EMULATOR=%SDK_DIR%\emulator\emulator.exe"
set "APK=%ROOT%app\build\outputs\apk\debug\app-debug.apk"
set "AVD=AndroidTV_1080p"
set "KARAOKE_SOURCE=D:\karaoke"

if not exist "%ADB%" (
    echo [ERRO] adb nao encontrado: %ADB%
    pause
    exit /b 1
)
if not exist "%EMULATOR%" (
    echo [ERRO] Emulador nao encontrado: %EMULATOR%
    pause
    exit /b 1
)
if not exist "%KARAOKE_SOURCE%\" (
    echo [ERRO] Pasta nao encontrada: %KARAOKE_SOURCE%
    echo Conecte o pendrive e confirme se ele esta na unidade D:.
    pause
    exit /b 1
)
if not exist "%KARAOKE_SOURCE%\dados\" (
    echo [ERRO] Subpasta dados nao encontrada em %KARAOKE_SOURCE%
    pause
    exit /b 1
)

echo ===================================================
echo       TESTE DE PAGINACAO - 24 MUSICAS POR PAGINA
echo ===================================================
echo.

echo [1/5] Compilando o APK atualizado...
call "%ROOT%gradlew.bat" :app:assembleDebug "-Dkotlin.incremental=false" --no-daemon
if errorlevel 1 (
    echo [ERRO] Falha na compilacao.
    pause
    exit /b 1
)

"%ADB%" devices | findstr /i "emulator" >nul 2>&1
if errorlevel 1 (
    echo [2/5] Iniciando o emulador Android TV...
    start "" "%EMULATOR%" -avd %AVD% -gpu auto
) else (
    echo [2/5] Emulador ja esta aberto.
)

echo [3/5] Aguardando o Android TV...
"%ADB%" wait-for-device
:waitboot
set "BOOT_STATUS="
for /f "tokens=*" %%a in ('"%ADB%" shell getprop sys.boot_completed 2^>nul') do set "BOOT_STATUS=%%a"
if not "!BOOT_STATUS!"=="1" (
    timeout /t 2 /nobreak >nul
    goto waitboot
)
timeout /t 3 /nobreak >nul

echo [4/5] Instalando APK e copiando D:\karaoke...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo [ERRO] Falha ao instalar o APK.
    pause
    exit /b 1
)
"%ADB%" shell pm clear com.araujo.karaoke_ia >nul
"%ADB%" shell appops set com.araujo.karaoke_ia MANAGE_EXTERNAL_STORAGE allow
"%ADB%" shell rm -rf /sdcard/Download/karaoke
"%ADB%" shell mkdir -p /sdcard/Download
"%ADB%" push "%KARAOKE_SOURCE%" /sdcard/Download/

"%ADB%" shell am force-stop com.araujo.karaoke_ia
"%ADB%" shell am start -n com.araujo.karaoke_ia/.MainActivity

echo [5/5] Aplicativo aberto.
echo.
echo Teste estas abas:
echo   - Musicas: use Proxima e Anterior
echo   - Pesquisar: procure uma musica entre as 100
echo   - Favoritos: confirme que a grade continua leve
echo.
pause
