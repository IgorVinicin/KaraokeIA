@echo off
setlocal
title Karaoke IA - Emulador leve
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\abrir-emulador-leve.ps1"
if errorlevel 1 pause
exit /b %ERRORLEVEL%
