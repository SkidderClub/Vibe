@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "VIBE_ROOT=%~dp0"
set "VIBE_GAME_DIR=%VIBE_ROOT%run\client"
set "VIBE_LOG_DIR=%VIBE_ROOT%logs"
set "VIBE_IMPORT_MARKER=%VIBE_GAME_DIR%\vibe\launcher\minecraft-imported-v1"
set "VIBE_READY_FILE=%VIBE_GAME_DIR%\vibe\launcher\game-visible"
set "VIBE_FAILED_FILE=%VIBE_GAME_DIR%\vibe\launcher\game-failed"

rem Import the user's normal Minecraft presentation settings exactly once for
rem this persistent Vibe profile. Minecraft 1.8 calls these resourcepacks;
rem older profiles may still use the texturepacks folder, so both are copied.
if not exist "%VIBE_IMPORT_MARKER%" call :importMinecraftProfile
if not exist "%VIBE_GAME_DIR%\vibe\launcher" mkdir "%VIBE_GAME_DIR%\vibe\launcher" >nul 2>&1
del /q "%VIBE_READY_FILE%" "%VIBE_FAILED_FILE%" >nul 2>&1

if not exist "%VIBE_LOG_DIR%" mkdir "%VIBE_LOG_DIR%" >nul 2>&1
for /f %%T in ('powershell.exe -NoProfile -Command "Get-Date -Format yyyy-MM-dd-HH-mm-ss"') do set "VIBE_LOG_STAMP=%%T"
set "VIBE_LOG_FILE=%VIBE_LOG_DIR%\%VIBE_LOG_STAMP%.log"

set "VIBE_RUN_ARGUMENTS=%*"
set "VIBE_LAUNCH_JOB=%VIBE_LOG_DIR%\launch-%VIBE_LOG_STAMP%.bat"
> "%VIBE_LAUNCH_JOB%" echo @echo off
>> "%VIBE_LAUNCH_JOB%" echo call "%VIBE_ROOT%build.bat" runClient -PvibeOptifine -PvibePersistentRun %%VIBE_RUN_ARGUMENTS%% ^>^> "%VIBE_LOG_FILE%" 2^>^&1
>> "%VIBE_LAUNCH_JOB%" echo if errorlevel 1 ^> "%VIBE_FAILED_FILE%" echo Vibe launch failed
>> "%VIBE_LAUNCH_JOB%" echo del "%%~f0" ^>nul 2^>^&1

rem Gradle stays alive for the Minecraft process. Its work runs in a hidden
rem job, while this console waits for the first client GUI to be visible.
rem All launcher and game output is retained in the timestamped log.
powershell.exe -NoProfile -Command "Start-Process -WindowStyle Hidden -FilePath $env:VIBE_LAUNCH_JOB" >nul 2>&1
echo [Vibe] Starting... this console closes when the game window is visible.
:waitForGame
if exist "%VIBE_READY_FILE%" endlocal & exit /b 0
if exist "%VIBE_FAILED_FILE%" (
    echo [Vibe] The launch failed. See "%VIBE_LOG_FILE%" for details.
    type "%VIBE_LOG_FILE%"
    pause
    endlocal & exit /b 1
)
powershell.exe -NoProfile -Command "Start-Sleep -Milliseconds 250" >nul 2>&1
goto waitForGame

:importMinecraftProfile
if not exist "%VIBE_GAME_DIR%" mkdir "%VIBE_GAME_DIR%" >nul 2>&1
if not exist "%VIBE_GAME_DIR%\vibe\launcher" mkdir "%VIBE_GAME_DIR%\vibe\launcher" >nul 2>&1
set "VIBE_MINECRAFT_DIR=%APPDATA%\.minecraft"
if exist "%VIBE_MINECRAFT_DIR%" (
    for %%F in ("%VIBE_MINECRAFT_DIR%\options*.txt") do if exist "%%~fF" copy /Y "%%~fF" "%VIBE_GAME_DIR%\%%~nxF" >nul
    if exist "%VIBE_MINECRAFT_DIR%\resourcepacks" robocopy "%VIBE_MINECRAFT_DIR%\resourcepacks" "%VIBE_GAME_DIR%\resourcepacks" /E /R:1 /W:1 /NFL /NDL /NJH /NJS >nul
    if exist "%VIBE_MINECRAFT_DIR%\texturepacks" robocopy "%VIBE_MINECRAFT_DIR%\texturepacks" "%VIBE_GAME_DIR%\resourcepacks" /E /R:1 /W:1 /NFL /NDL /NJH /NJS >nul
)
> "%VIBE_IMPORT_MARKER%" echo Imported on %DATE% %TIME%
exit /b 0
