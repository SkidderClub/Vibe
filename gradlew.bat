@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Every invocation uses a new empty game directory. run.bat keeps its usual data.
set "VIBE_NO_PAUSE=1"
call "%~dp0build.bat" runClient -I "%~dp0tools\fresh-client.gradle" %*
endlocal & exit /b %ERRORLEVEL%
