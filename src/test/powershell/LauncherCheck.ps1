$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$fixture = Join-Path $projectRoot ('build/launcher check ' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $fixture 'tools') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'run.bat') -Destination $fixture
Copy-Item -LiteralPath (Join-Path $projectRoot 'tools/run-client.ps1') -Destination (Join-Path $fixture 'tools')
Copy-Item -LiteralPath (Join-Path $projectRoot 'tools/run-client-worker.ps1') -Destination (Join-Path $fixture 'tools')

function Assert-Contains([string]$Text, [string]$Expected) {
    if (-not $Text.Contains($Expected)) { throw "Missing expected output: $Expected" }
}

# Exercise the real batch/PowerShell boundary without opening Minecraft.
@'
@echo off
echo LAUNCHER_STDOUT
echo LAUNCHER_STDERR 1>&2
echo NESTED_PAUSE=%VIBE_NO_PAUSE%
echo FORWARDED=%*
:arguments
if "%~1"=="" goto done
echo ARG=[%~1]
shift
goto arguments
:done
exit /b %VIBE_TEST_EXIT%
'@ | Set-Content -LiteralPath (Join-Path $fixture 'build.bat') -Encoding ASCII

$originalNoPause = $env:VIBE_NO_PAUSE
$originalExit = $env:VIBE_TEST_EXIT
$originalAppData = $env:APPDATA
$originalRelease = $env:VIBE_TEST_RELEASE
try {
    $env:VIBE_NO_PAUSE = '1'
    $env:APPDATA = Join-Path $fixture 'fake appdata'
    $sourceProfile = Join-Path $env:APPDATA '.minecraft'
    New-Item -ItemType Directory -Path (Join-Path $sourceProfile 'resourcepacks') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $sourceProfile 'options.txt') -Value 'DO_NOT_IMPORT'
    Set-Content -LiteralPath (Join-Path $sourceProfile 'resourcepacks/test.zip') -Value 'DO_NOT_IMPORT'

    foreach ($expectedExit in @(0, 37)) {
        $env:VIBE_TEST_EXIT = [string]$expectedExit
        $output = (& (Join-Path $fixture 'run.bat') --debug '-PlaunchCheck=with spaces' 2>&1 | Out-String)
        if ($LASTEXITCODE -ne $expectedExit) { throw "Exit code: expected $expectedExit, got $LASTEXITCODE. $output" }
        $logFile = Get-ChildItem -LiteralPath (Join-Path $fixture 'logs') -File | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
        $log = Get-Content -LiteralPath $logFile.FullName -Raw -Encoding UTF8
        foreach ($expected in @('LAUNCHER_STDOUT', 'LAUNCHER_STDERR', 'NESTED_PAUSE=1', 'ARG=[runClient]',
            'ARG=[-PvibeOptifine]', 'ARG=[-PvibePersistentRun]', '--console=plain',
            'ARG=[--info]', 'ARG=[--stacktrace]', 'ARG=[--debug]', 'ARG=[-PlaunchCheck=with spaces]',
            "Client task exited with code $expectedExit.")) {
            Assert-Contains $output $expected
            Assert-Contains $log $expected
        }
    }
    if (Test-Path -LiteralPath (Join-Path $fixture 'run')) { throw 'Launcher unexpectedly created/imported a game profile.' }
    Assert-Contains (Get-Content -LiteralPath (Join-Path $sourceProfile 'options.txt') -Raw) 'DO_NOT_IMPORT'
    # Simulate the rendered menu, then hold the game open until the foreground
    # launcher has exited. It must keep logging without its original console.
    @'
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0simulate-client.ps1"
exit /b %ERRORLEVEL%
'@ | Set-Content -LiteralPath (Join-Path $fixture 'build.bat') -Encoding ASCII
    @'
Write-Output 'BEFORE_MENU'
Start-Sleep -Milliseconds 350
[IO.File]::WriteAllText($env:VIBE_LAUNCH_READY_FILE, 'ok')
$deadline = [DateTime]::UtcNow.AddSeconds(15)
while (-not (Test-Path -LiteralPath $env:VIBE_TEST_RELEASE)) {
    if ([DateTime]::UtcNow -gt $deadline) { throw 'Test did not release the simulated game.' }
    Start-Sleep -Milliseconds 100
}
Write-Output 'AFTER_CONSOLE_CLOSED'
exit 0
'@ | Set-Content -LiteralPath (Join-Path $fixture 'simulate-client.ps1') -Encoding ASCII
    $env:VIBE_TEST_RELEASE = Join-Path $fixture 'release-game'
    $legacyReady = Join-Path $fixture 'run/client/vibe/launcher'
    New-Item -ItemType Directory -Path $legacyReady -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $legacyReady 'game-visible') -Value 'stale marker'
    $output = (& (Join-Path $fixture 'run.bat') 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "Handoff failed: $output" }
    Assert-Contains $output 'BEFORE_MENU'
    Assert-Contains $output 'Minecraft is ready. Logging continues'
    if ($output.Contains('AFTER_CONSOLE_CLOSED')) { throw 'Launcher waited for the game to exit.' }
    Set-Content -LiteralPath $env:VIBE_TEST_RELEASE -Value 'continue'
    $logFile = Get-ChildItem -LiteralPath (Join-Path $fixture 'logs') -Filter '*.log' | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $log = Get-Content -LiteralPath $logFile.FullName -Raw -Encoding UTF8
        if ($log.Contains('Client task exited with code 0.')) { break }
        if ([DateTime]::UtcNow -gt $deadline) { throw 'Detached worker did not finish logging.' }
        Start-Sleep -Milliseconds 100
    } while ($true)
    Assert-Contains $log 'AFTER_CONSOLE_CLOSED'

    $output = (& (Join-Path $fixture 'run.bat') --keep-console 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "Keep-console failed: $output" }
    Assert-Contains $output 'AFTER_CONSOLE_CLOSED'
    Assert-Contains $output 'Client task exited with code 0.'
    if ($output.Contains('Minecraft is ready.')) { throw 'Keep-console detached unexpectedly.' }
    Write-Output 'Launcher checks passed: arguments, startup errors, no import, menu handoff, continued logging and keep-console.'
} finally {
    $env:VIBE_NO_PAUSE = $originalNoPause
    $env:VIBE_TEST_EXIT = $originalExit
    $env:APPDATA = $originalAppData
    $env:VIBE_TEST_RELEASE = $originalRelease
}
