@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Vibe Launcher Builder

rem Builds launcher\VibeLauncher.jar. Pass --skip-tests to package without running the tests.
set "ROOT=%~dp0"
set "BUILD=%ROOT%build"
set "CLASSES=%BUILD%\classes"
set "TEST_CLASSES=%BUILD%\test-classes"
set "OUTPUT=%ROOT%VibeLauncher.jar"
set "STAGED_OUTPUT=%BUILD%\VibeLauncher.staged.jar"
set "SKIP_TESTS="
if /i "%~1"=="--skip-tests" set "SKIP_TESTS=1"

where javac >nul 2>&1
if errorlevel 1 (
    echo [ERROR] A JDK 11 or newer is required to build the launcher ^(JDK 21 recommended^).
    echo         The launcher itself still runs on Java 8.
    goto :failed
)

if exist "%BUILD%" rmdir /s /q "%BUILD%"
mkdir "%CLASSES%" "%TEST_CLASSES%" 2>nul

rem -sourcepath lets javac find every class from the entry point, without file lists.
echo [Vibe Launcher] Compiling Java 8-compatible sources...
javac --release 8 -encoding UTF-8 -Xlint:-options -sourcepath "%ROOT%src" -d "%CLASSES%" "%ROOT%src\dev\vibe\launcher\VibeLauncher.java"
if errorlevel 1 goto :failed

if defined SKIP_TESTS goto :package
echo [Vibe Launcher] Running tests...
javac --release 8 -encoding UTF-8 -Xlint:-options -cp "%CLASSES%" -sourcepath "%ROOT%test" -d "%TEST_CLASSES%" "%ROOT%test\dev\vibe\launcher\LauncherTests.java"
if errorlevel 1 goto :failed
java -Xms32m -Xmx384m -XX:+UseSerialGC -Djava.awt.headless=true -cp "%CLASSES%;%TEST_CLASSES%" dev.vibe.launcher.LauncherTests
if errorlevel 1 (
    echo [ERROR] Launcher tests failed; the JAR was not replaced.
    goto :failed
)

:package
echo [Vibe Launcher] Packaging executable JAR...
set "VERSION="
for /f "tokens=2 delims==" %%V in ('findstr /c:"String VERSION =" "%ROOT%src\dev\vibe\launcher\VibeLauncher.java"') do set "VERSION=%%V"
set "VERSION=%VERSION:"=%"
set "VERSION=%VERSION:;=%"
set "VERSION=%VERSION: =%"
> "%BUILD%\manifest.txt" (
    echo Main-Class: dev.vibe.launcher.VibeLauncher
    echo Implementation-Title: Vibe Launcher
    echo Implementation-Version: %VERSION%
)
jar cfm "%STAGED_OUTPUT%" "%BUILD%\manifest.txt" -C "%CLASSES%" .
if errorlevel 1 goto :failed

move /y "%STAGED_OUTPUT%" "%OUTPUT%" >nul
if errorlevel 1 (
    echo [ERROR] Could not replace VibeLauncher.jar because it is in use.
    echo         Close the launcher, then run this build script again.
    goto :failed
)

rem Release asset pair for the launcher's self-update: VibeLauncher.jar + VibeLauncher.jar.sha256
set "HASH="
for /f "tokens=*" %%H in ('certutil -hashfile "%OUTPUT%" SHA256 ^| findstr /v ":"') do if not defined HASH set "HASH=%%H"
set "HASH=%HASH: =%"
> "%BUILD%\VibeLauncher.jar.sha256" echo %HASH%  VibeLauncher.jar

rem Windows app image with a bundled runtime: dist\VibeLauncher\VibeLauncher.exe
where jpackage >nul 2>&1
if errorlevel 1 (
    echo [WARN] jpackage was not found ^(JDK 14 or newer^); skipping the Windows app image.
    goto :done
)
set "APP_INPUT=%BUILD%\app-input"
set "DIST=%ROOT%dist"
mkdir "%APP_INPUT%" "%DIST%" 2>nul
copy /y "%OUTPUT%" "%APP_INPUT%\VibeLauncher.jar" >nul
if exist "%DIST%\VibeLauncher" rmdir /s /q "%DIST%\VibeLauncher" 2>nul
if exist "%DIST%\VibeLauncher" (
    echo [ERROR] Could not replace "%DIST%\VibeLauncher". Close the running launcher and try again.
    goto :failed
)
echo [Vibe Launcher] Building Windows app image...
jpackage --type app-image --name VibeLauncher --app-version %VERSION% ^
    --vendor "Vibe" --description "Vibe Launcher" ^
    --input "%APP_INPUT%" --main-jar VibeLauncher.jar ^
    --main-class dev.vibe.launcher.VibeLauncher ^
    --dest "%DIST%"
if errorlevel 1 (
    echo [ERROR] jpackage could not build the Windows app image.
    goto :failed
)
echo Windows app:   %DIST%\VibeLauncher\VibeLauncher.exe

:done
echo.
echo Build complete: %OUTPUT% ^(version %VERSION%^)
echo SHA-256:        %HASH%
echo.
echo Run it with:    java -jar "%OUTPUT%"
if not defined VIBE_NO_PAUSE pause
exit /b 0

:failed
echo.
echo [ERROR] Launcher build failed. Fix the error above and run this script again.
if not defined VIBE_NO_PAUSE pause
exit /b 1
