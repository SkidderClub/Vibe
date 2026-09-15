@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Reuse the builder's JDK detection and short Windows project path.
rem Extra arguments are forwarded to Gradle, e.g. run.bat --info.
set "VIBE_NO_PAUSE=1"
rem Always use the persistent development game directory.  Without this flag
rem Gradle can inherit the temporary subst working path from build.bat and
rem start a second, empty config directory instead of the last active profile.
call "%~dp0build.bat" runClient -PvibeOptifine -PvibePersistentRun %*
set "VIBE_LAUNCH_EXIT=%ERRORLEVEL%"
if not "%VIBE_LAUNCH_EXIT%"=="0" pause
endlocal & exit /b %VIBE_LAUNCH_EXIT%
