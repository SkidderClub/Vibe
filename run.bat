@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Vibe Client - Debug Console

rem Show startup output until Minecraft has rendered its first menu.
rem Use --keep-console to keep watching output after startup.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\run-client.ps1" %*
set "VIBE_LAUNCH_EXIT=%ERRORLEVEL%"
if not "%VIBE_LAUNCH_EXIT%"=="0" if not defined VIBE_NO_PAUSE pause
endlocal & exit /b %VIBE_LAUNCH_EXIT%
