@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Vibe Launcher Builder

set "ROOT=%~dp0"
set "SOURCE=%ROOT%src\dev\vibe\launcher\VibeLauncher.java"
set "CLASSES=%ROOT%build\classes"
set "OUTPUT=%ROOT%VibeLauncher.jar"
set "STAGED_OUTPUT=%ROOT%VibeLauncher.staged.jar"

if not exist "%SOURCE%" (
    echo [ERROR] Launcher source not found: %SOURCE%
    exit /b 1
)

where javac >nul 2>&1
if errorlevel 1 (
    echo [ERROR] A JDK 8 or newer is required to build the launcher.
    echo         Install JDK 21, then restart this script.
    exit /b 1
)

if exist "%CLASSES%" rmdir /s /q "%CLASSES%"
mkdir "%CLASSES%" 2>nul

echo [Vibe Launcher] Compiling Java 8-compatible source...
javac --release 8 -encoding UTF-8 -d "%CLASSES%" "%SOURCE%"
if errorlevel 1 goto :failed

echo [Vibe Launcher] Packaging executable JAR...
if exist "%STAGED_OUTPUT%" del /q "%STAGED_OUTPUT%" >nul 2>&1
jar cfe "%STAGED_OUTPUT%" dev.vibe.launcher.VibeLauncher -C "%CLASSES%" .
if errorlevel 1 goto :failed

move /y "%STAGED_OUTPUT%" "%OUTPUT%" >nul
if errorlevel 1 (
    del /q "%STAGED_OUTPUT%" >nul 2>&1
    echo [ERROR] Could not replace VibeLauncher.jar because it is in use.
    echo         Close the launcher, then run this build script again.
    exit /b 1
)

echo.
echo Build complete:
echo   %OUTPUT%
echo.
echo Run it with: java -jar "%OUTPUT%"
exit /b 0

:failed
echo.
echo [ERROR] Launcher build failed. Fix the error above and run this script again.
exit /b 1
