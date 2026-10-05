@echo off
setlocal enabledelayedexpansion
title Karaoke IA - Executar e Testar TV Box
color 0B

set "ROOT=%~dp0"
set "SDK_DIR=C:\Users\admin.igor\AppData\Local\Android\Sdk"
set "ADB=%SDK_DIR%\platform-tools\adb.exe"
set "EMULATOR=%SDK_DIR%\emulator\emulator.exe"
set "APK=%ROOT%app\build\outputs\apk\debug\app-debug.apk"
set "AVD=AndroidTV_1080p"
set "KARAOKE_SOURCE=D:\karaoke"
set "PACKAGE=com.araujo.karaoke_ia"
set "ACTIVITY=.MainActivity"

echo ===================================================
echo     KARAOKE TV BOX - AMBIENTE DE EXECUCAO E TESTE
echo ===================================================
echo.

:: 1. Verificacao de Ferramentas
if not exist "%ADB%" (
    echo [ERRO] ADB nao encontrado em: %ADB%
    pause
    exit /b 1
)
if not exist "%EMULATOR%" (
    echo [ERRO] Emulador nao encontrado em: %EMULATOR%
    pause
    exit /b 1
)

:: 2. Compilacao do Aplicativo
echo [1/5] Verificando compilacao do APK...
call "%ROOT%gradlew.bat" :app:assembleDebug
if errorlevel 1 (
    echo.
    echo [ERRO] Falha na compilacao do APK.
    pause
    exit /b 1
)

if not exist "%APK%" (
    echo [ERRO] APK gerado nao encontrado: %APK%
    pause
    exit /b 1
)

:: 3. Inicializacao do Emulador (se necessario)
echo [2/5] Verificando emulador Android TV...
"%ADB%" devices | findstr /i "emulator-" >nul 2>&1
if errorlevel 1 (
    echo Iniciando o emulador %AVD% com aceleracao de GPU...
    start "" "%EMULATOR%" -avd "%AVD%" -gpu host
) else (
    echo Emulador ja esta em execucao.
)

:: 4. Aguardar o dispositivo e o boot do Android
echo [3/5] Aguardando o Android TV inicializar completamente...
set /a ADB_ATTEMPTS=0

:waitdevice
set /a ADB_ATTEMPTS+=1
set "ADB_STATE="
for /f "tokens=*" %%a in ('"%ADB%" get-state 2^>nul') do set "ADB_STATE=%%a"
if /i not "!ADB_STATE!"=="device" (
    if !ADB_ATTEMPTS! GEQ 60 (
        echo [ERRO] O emulador nao respondeu ao ADB apos 2 minutos. Estado: !ADB_STATE!
        pause
        exit /b 1
    )
    timeout /t 2 /nobreak >nul
    goto waitdevice
)

:waitboot
set "BOOT_STATUS="
for /f "tokens=*" %%a in ('"%ADB%" shell getprop sys.boot_completed 2^>nul') do set "BOOT_STATUS=%%a"
if not "!BOOT_STATUS!"=="1" (
    timeout /t 2 /nobreak >nul
    goto waitboot
)

:: Confirmar se o Package Manager ja esta pronto
:waitpm
set "PM_STATUS="
for /f "tokens=*" %%a in ('"%ADB%" shell pm path android 2^>nul') do set "PM_STATUS=%%a"
if not defined PM_STATUS (
    timeout /t 1 /nobreak >nul
    goto waitpm
)

echo Android TV pronto e operacional.

:: 5. Instalacao do APK e configuracao de permissoes
echo [4/5] Instalando APK e garantindo permissoes...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo [ERRO] Falha ao instalar o APK no emulador.
    pause
    exit /b 1
)

"%ADB%" shell appops set %PACKAGE% MANAGE_EXTERNAL_STORAGE allow >nul 2>&1

:: 6. Sincronizacao de dados de midia inteligente (se existir pendrive D:)
if exist "%KARAOKE_SOURCE%\dados\" (
    echo Verificando pasta de musicas no emulador...
    "%ADB%" shell "ls /sdcard/Download/karaoke/dados >/dev/null 2>&1"
    if errorlevel 1 (
        echo Copiando biblioteca inicial de musicas de %KARAOKE_SOURCE% para o emulador...
        "%ADB%" shell mkdir -p /sdcard/Download
        "%ADB%" push "%KARAOKE_SOURCE%" /sdcard/Download/
    ) else (
        echo Biblioteca de musicas ja sincronizada no emulador.
    )
) else (
    echo [AVISO] Unidade D:\karaoke nao detectada. Usando armazenamento interno do emulador se existente.
)

:: 7. Iniciar a Activity Principal
echo [5/5] Iniciando o Karaoke TV Box...
"%ADB%" shell am force-stop %PACKAGE%
"%ADB%" shell am start -n %PACKAGE%/%ACTIVITY%
if errorlevel 1 (
    echo [ERRO] Nao foi possivel iniciar a Activity %ACTIVITY%.
    pause
    exit /b 1
)

echo.
echo ===================================================
echo   APLICATIVO INICIADO COM SUCESSO NO EMULADOR!
echo ===================================================
echo.
echo Testes recomendados na interface:
echo   - Navegacao pelas abas com controle / teclado
echo   - Pesquisa de musicas e teste de paginacao
echo   - Reproducao de video (verificar fluidez de 60fps)
echo.
pause
