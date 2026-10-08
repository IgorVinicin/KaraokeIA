@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Karaoke IA - Teste de capas e catalogo
color 0B

set "ROOT=%~dp0"
set "APK=%ROOT%app\build\outputs\apk\debug\app-debug.apk"
set "PACKAGE=com.araujo.karaoke_ia"
set "AVD=Television_1080p"
set "EMULATOR_RAM=1536"
set "EMULATOR_CORES=2"
set "EMULATOR_RESOLUTION=960x540"
if not defined EMULATOR_GPU set "EMULATOR_GPU=swiftshader_indirect"
set "EMULATOR_LOG=%ROOT%emulador-tv.log"
set "APP_LOG=%ROOT%karaoke-logcat.txt"
set /a BOOT_WAIT=0

rem KARAOKE_SOURCE pode ser definido antes de executar para usar outro caminho.
if defined KARAOKE_SOURCE (
    set "SOURCE_OVERRIDE=%KARAOKE_SOURCE%"
    set "KARAOKE_SOURCE="
    call :checkCandidate "!SOURCE_OVERRIDE!"
)

rem Usa um caminho configurado; tenta D:\karaoke primeiro e depois varre discos/workspace.
if not defined KARAOKE_SOURCE call :findKaraokeSource
if not defined KARAOKE_SOURCE (
    echo Nao localizei automaticamente. Digite a pasta que contem os videos e a pasta dados.
    echo Exemplo: D:\karaoke
    set /p "SOURCE_OVERRIDE=Pasta das musicas: "
    call :checkCandidate "!SOURCE_OVERRIDE!"
)
if not defined KARAOKE_SOURCE (
    echo [ERRO] O caminho informado nao tem arquivos .txt validos dentro de dados\:
    echo        Verifique se selecionou a pasta principal da colecao, por exemplo D:\karaoke.
    goto error
)

rem Localiza Android SDK/ADB sem depender de um usuario fixo.
if defined ANDROID_SDK_ROOT set "SDK_DIR=%ANDROID_SDK_ROOT%"
if not defined SDK_DIR if defined ANDROID_HOME set "SDK_DIR=%ANDROID_HOME%"
if not defined SDK_DIR set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%SDK_DIR%\platform-tools\adb.exe"
set "EMULATOR=%SDK_DIR%\emulator\emulator.exe"

rem Usa o JDK do Android Studio se JAVA_HOME nao estiver configurado.
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%ROOT%gradlew.bat" (
    echo [ERRO] gradlew.bat nao encontrado em %ROOT%
    goto error
)
if not exist "%ADB%" (
    echo [ERRO] adb nao encontrado: %ADB%
    echo Instale Android SDK Platform-Tools ou configure ANDROID_SDK_ROOT.
    goto error
)
if not exist "%EMULATOR%" (
    echo [ERRO] Emulador nao encontrado: %EMULATOR%
    echo Ajuste ANDROID_SDK_ROOT para o caminho correto do Android SDK.
    goto error
)
"%EMULATOR%" -list-avds | findstr /i /x "%AVD%" >nul 2>&1
if errorlevel 1 (
    echo [ERRO] O emulador virtual "%AVD%" nao existe neste Android SDK.
    echo Emuladores encontrados:
    "%EMULATOR%" -list-avds
    goto error
)
where java >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Java nao encontrado. Instale o Android Studio ou configure JAVA_HOME.
    goto error
)

echo ===================================================
echo       TESTE DE CATALOGO E CAPAS - KARAOKE IA
echo ===================================================
echo Pasta de musicas: %KARAOKE_SOURCE%
echo.

echo [1/5] Compilando o APK atualizado...
call "%ROOT%gradlew.bat" :app:assembleDebug "-Pkotlin.incremental=false" --no-daemon --max-workers=2
if errorlevel 1 (
    echo [ERRO] Falha na compilacao.
    goto error
)
if not exist "%APK%" (
    echo [ERRO] APK nao encontrado: %APK%
    goto error
)

"%ADB%" -e get-state >nul 2>&1
if errorlevel 1 (
    echo [2/5] Iniciando o emulador Android TV no PC...
    echo Configuracao leve: %EMULATOR_CORES% nucleos, %EMULATOR_RAM% MB RAM, %EMULATOR_RESOLUTION%.
    echo Log do emulador: %EMULATOR_LOG%
    start "Karaoke - Emulador" /b "%EMULATOR%" -avd %AVD% -cores %EMULATOR_CORES% -memory %EMULATOR_RAM% -skin %EMULATOR_RESOLUTION% -dpi-device 240 -gpu %EMULATOR_GPU% -no-boot-anim -no-snapshot >"%EMULATOR_LOG%" 2>&1
) else (
    echo [2/5] Emulador Android TV ja esta aberto no PC.
    "%ADB%" devices
)

echo [3/5] Aguardando o emulador Android TV...
set /a DEVICE_WAIT=0
:waitdevice
"%ADB%" -e get-state >nul 2>&1
if not errorlevel 1 goto waitboot
set /a DEVICE_WAIT+=1
if !DEVICE_WAIT! GEQ 90 (
    echo [ERRO] O emulador Android TV nao ficou disponivel em 3 minutos.
    echo Verifique o emulador no Android Studio e tente novamente.
    goto error
)
timeout /t 2 /nobreak >nul
goto waitdevice

:waitboot
echo Aguardando boot do Android TV... tentativa !BOOT_WAIT!/90
set "BOOT_STATUS="
for /f "tokens=*" %%a in ('"%ADB%" -e shell getprop sys.boot_completed 2^>nul') do set "BOOT_STATUS=%%a"
if "!BOOT_STATUS!"=="1" goto bootcomplete
set /a BOOT_WAIT+=1
if !BOOT_WAIT! GEQ 90 (
    echo [ERRO] O Android TV conectou, mas nao terminou de iniciar em 3 minutos.
    echo Consulte %EMULATOR_LOG%
    goto error
)
timeout /t 2 /nobreak >nul
goto waitboot

:bootcomplete
timeout /t 3 /nobreak >nul
"%ADB%" -e shell settings put secure show_ime_with_hard_keyboard 1

echo [4/5] Instalando APK e copiando as musicas para o emulador...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\instalar-apk.ps1" -Adb "%ADB%" -Apk "%APK%"
if errorlevel 1 (
    echo [ERRO] Falha ao instalar o APK.
    goto error
)
"%ADB%" -e shell appops set %PACKAGE% MANAGE_EXTERNAL_STORAGE allow
if exist "%ROOT%app\.env" (
    for /f "tokens=*" %%S in ('"%ADB%" -e get-serialno') do set "YOUTUBE_SERIAL=%%S"
    call :configureyoutube
    if errorlevel 1 goto error
)
"%ADB%" -e shell rm -rf /sdcard/Download/karaoke
"%ADB%" -e shell mkdir -p /sdcard/Download
"%ADB%" -e push "%KARAOKE_SOURCE%" /sdcard/Download/
if errorlevel 1 (
    echo [ERRO] Falha ao copiar as musicas do pendrive.
    goto error
)

"%ADB%" -e shell am force-stop %PACKAGE%
"%ADB%" -e logcat -b all -d -v threadtime >"%APP_LOG%" 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\iniciar-controle-qr.ps1" -Adb "%ADB%"
if errorlevel 1 (
    echo [ERRO] Falha ao iniciar o controle pelo QR Code. Confira controle-qr-error.log.
    echo Verifique Node.js, conexao de rede e se o aplicativo esta aberto no emulador.
    goto error
)

echo [5/5] Verificando se o aplicativo continua aberto...
timeout /t 10 /nobreak >nul
"%ADB%" -e logcat -b all -d -v threadtime >>"%APP_LOG%" 2>&1
set "APP_PID="
for /f "tokens=*" %%P in ('"%ADB%" -e shell pidof %PACKAGE% 2^>nul') do set "APP_PID=%%P"
if not defined APP_PID (
    echo [ERRO] O aplicativo nao permaneceu em execucao.
    echo Log para diagnostico: %APP_LOG%
    goto error
)
echo Aplicativo em execucao. PID: !APP_PID!
echo Log para diagnostico: %APP_LOG%
echo.
echo TESTE:
echo   - Catalogo: teste paginacao e pesquisa de musicas.
echo   - Capas: na aba Musica, use "Buscar capas das musicas".
echo   - Aguarde o progresso; as capas encontradas substituem o fallback.
echo   - Feche e reabra o app para verificar o cache local.
echo   - Offline: capas cacheadas aparecem; as demais usam o fallback.
echo.
echo As musicas em /sdcard/Download/karaoke serao atualizadas.
echo Dados do app e cache persistente de capas NAO serao apagados.
pause
exit /b 0

:findKaraokeSource
echo Procurando primeiro em D:\karaoke e depois na pasta aberta e nos discos conectados...
call :checkCandidate "D:\karaoke"
if defined KARAOKE_SOURCE exit /b 0

rem Tambem verifica a raiz de D: e os nomes convencionais no pendrive.
call :checkCandidate "D:"
if defined KARAOKE_SOURCE exit /b 0
call :checkCandidate "D:\KARAOKE"
if defined KARAOKE_SOURCE exit /b 0
call :checkCandidate "D:\Download\karaoke"
if defined KARAOKE_SOURCE exit /b 0
call :checkCandidate "D:\MUSICAS"
if defined KARAOKE_SOURCE exit /b 0
call :checkCandidate "%ROOT%"
if defined KARAOKE_SOURCE exit /b 0

for %%D in (D E F G H I J K L M N O P Q R S T U V W X Y Z) do (
    call :checkCandidate "%%D:\karaoke"
    if defined KARAOKE_SOURCE exit /b 0
    call :checkCandidate "%%D:\KARAOKE"
    if defined KARAOKE_SOURCE exit /b 0
    call :checkCandidate "%%D:\Download\karaoke"
    if defined KARAOKE_SOURCE exit /b 0
    call :checkCandidate "%%D:\MUSICAS"
    if defined KARAOKE_SOURCE exit /b 0
    call :checkCandidate "%%D:\musicas"
    if defined KARAOKE_SOURCE exit /b 0
    call :checkCandidate "%%D:"
    if defined KARAOKE_SOURCE exit /b 0
)
exit /b

:checkCandidate
if not exist "%~1\dados\" exit /b 1
for %%F in ("%~1\dados\*.txt") do (
    if exist "%%~fF" (
        set "KARAOKE_SOURCE=%~1"
        echo Colecao encontrada: !KARAOKE_SOURCE!
        exit /b 0
    )
)
exit /b 1

:configureyoutube
echo Configurando a busca YouTube somente neste emulador...
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%scripts\configurar-youtube.ps1" -Serial "%YOUTUBE_SERIAL%" -NoRestart
exit /b %ERRORLEVEL%

:error
echo.
pause
exit /b 1
