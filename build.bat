@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Vibe 1.8.9 Forge Builder

rem Set VIBE_NO_PAUSE=1 when this launcher is called by another script/CI job.
rem With no arguments, build the release JAR. Otherwise forward Gradle tasks/options.
set "SCRIPT_DIR=%~dp0"
set "BUILD_EXIT=1"
set "VIBE_JAVA="
set "VIBE_RUN_CLIENT="
if /i "%~1"=="runClient" set "VIBE_RUN_CLIENT=1"
if defined VIBE_RUN_CLIENT title Vibe 1.8.9 Forge Client

echo.
echo ============================================================
if defined VIBE_RUN_CLIENT (
    echo                    Vibe Client Launcher
) else (
    echo                    Vibe Client Builder
)
echo ============================================================
echo.

rem Prefer an explicitly configured JDK 21, then look in common JDK locations,
rem and finally use a Java 21 installation exposed through PATH.  This is kept
rem inline (rather than using CALLed labels) so the launcher always exits once.
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /r /c:"21\.[0-9][0-9]*" >nul
        if not errorlevel 1 set "VIBE_JAVA=%JAVA_HOME%"
    )
)

if not defined VIBE_JAVA (
    for /d %%D in ("C:\Program Files\Java\jdk-21*") do (
        if not defined VIBE_JAVA if exist "%%~fD\bin\java.exe" (
            "%%~fD\bin\java.exe" -version 2>&1 | findstr /r /c:"21\.[0-9][0-9]*" >nul
            if not errorlevel 1 set "VIBE_JAVA=%%~fD"
        )
    )
)

if not defined VIBE_JAVA (
    for /d %%D in ("C:\Program Files\BellSoft\LibericaJDK-21*") do (
        if not defined VIBE_JAVA if exist "%%~fD\bin\java.exe" (
            "%%~fD\bin\java.exe" -version 2>&1 | findstr /r /c:"21\.[0-9][0-9]*" >nul
            if not errorlevel 1 set "VIBE_JAVA=%%~fD"
        )
    )
)

if not defined VIBE_JAVA (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do (
        if not defined VIBE_JAVA if exist "%%~fD\bin\java.exe" (
            "%%~fD\bin\java.exe" -version 2>&1 | findstr /r /c:"21\.[0-9][0-9]*" >nul
            if not errorlevel 1 set "VIBE_JAVA=%%~fD"
        )
    )
)

if not defined VIBE_JAVA (
    for /d %%D in ("C:\Program Files\Microsoft\jdk-21*") do (
        if not defined VIBE_JAVA if exist "%%~fD\bin\java.exe" (
            "%%~fD\bin\java.exe" -version 2>&1 | findstr /r /c:"21\.[0-9][0-9]*" >nul
            if not errorlevel 1 set "VIBE_JAVA=%%~fD"
        )
    )
)

if not defined VIBE_JAVA (
    for /d %%D in ("C:\Program Files\Zulu\zulu-21*") do (
        if not defined VIBE_JAVA if exist "%%~fD\bin\java.exe" (
            "%%~fD\bin\java.exe" -version 2>&1 | findstr /r /c:"21\.[0-9][0-9]*" >nul
            if not errorlevel 1 set "VIBE_JAVA=%%~fD"
        )
    )
)

if not defined VIBE_JAVA (
    for /d %%D in ("%USERPROFILE%\.jdks\*") do (
        if not defined VIBE_JAVA if exist "%%~fD\bin\javac.exe" (
            "%%~fD\bin\java.exe" -version 2>&1 | findstr /r /c:"21\.[0-9][0-9]*" >nul
            if not errorlevel 1 set "VIBE_JAVA=%%~fD"
        )
    )
)

if not defined VIBE_JAVA (
    for /f "usebackq delims=" %%J in (`where.exe java 2^>nul`) do (
        if not defined VIBE_JAVA for %%H in ("%%~dpJ..") do (
            if exist "%%~fH\bin\java.exe" (
                "%%~fH\bin\java.exe" -version 2>&1 | findstr /r /c:"21\.[0-9][0-9]*" >nul
                if not errorlevel 1 set "VIBE_JAVA=%%~fH"
            )
        )
    )
)

if not defined VIBE_JAVA (
    echo [ERROR] A JDK 21 installation could not be found.
    echo         Install JDK 21 or set JAVA_HOME to its installation directory.
    echo         JAVA_HOME currently is: %JAVA_HOME%
    goto :finish
)

if not exist "%SCRIPT_DIR%gradlew.bat" (
    echo [ERROR] Gradle wrapper not found: "%SCRIPT_DIR%gradlew.bat"
    goto :finish
)

set "JAVA_HOME=%VIBE_JAVA%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "VIBE_GRADLE_JAVA8_ARG="

rem A standalone Vibe Launcher can supply a private Java 8 runtime for the
rem remapped Forge client while this script itself continues to build on JDK 21.
if defined VIBE_JAVA8 if exist "%VIBE_JAVA8%\bin\java.exe" (
    rem Gradle expects a comma-separated toolchain list, even on Windows.
    set "VIBE_GRADLE_JAVA8_ARG="-Dorg.gradle.java.installations.paths=%JAVA_HOME%,%VIBE_JAVA8%""
    echo [Vibe] Using launcher-provided Java 8 runtime: %VIBE_JAVA8%
)

echo [Vibe] Using JDK: %JAVA_HOME%
if defined VIBE_RUN_CLIENT (
    echo [Vibe] Building and starting Minecraft with Vibe...
) else (
    echo [Vibe] Starting Gradle build...
)
echo.

rem Use the real project directory; do not allocate a drive for each launch.
rem Keep the normal user cache so provisioned toolchains remain available.
pushd "%SCRIPT_DIR%"
if errorlevel 1 goto :finish
set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
if "%~1"=="" (
    call "%SCRIPT_DIR%gradlew.bat" clean build --no-daemon %VIBE_GRADLE_JAVA8_ARG%
) else (
    call "%SCRIPT_DIR%gradlew.bat" %* --no-daemon %VIBE_GRADLE_JAVA8_ARG%
)
set "BUILD_EXIT=%ERRORLEVEL%"
popd

if not "%BUILD_EXIT%"=="0" (
    echo.
    echo [ERROR] Gradle failed with exit code %BUILD_EXIT%.
    goto :finish
)

if defined VIBE_RUN_CLIENT (
    echo.
    echo [Vibe] Client task finished.
    goto :finish
)

echo.
echo [Vibe] Build complete. Output files:
set "FOUND_ARTIFACT="
for %%F in ("%SCRIPT_DIR%build\libs\*.jar") do (
    if exist "%%~fF" (
        echo   %%~fF
        set "FOUND_ARTIFACT=1"
    )
)
if not defined FOUND_ARTIFACT echo   [WARNING] No JAR files were found in build\libs.
goto :finish

:finish
echo.
if not defined VIBE_NO_PAUSE pause
endlocal & exit /b %BUILD_EXIT%
