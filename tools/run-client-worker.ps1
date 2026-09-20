# Detached owner of Gradle/Minecraft. The visible launcher only tails this log.
$ErrorActionPreference = 'Continue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logFile = $env:VIBE_LAUNCH_LOG_FILE
$readyFile = $env:VIBE_LAUNCH_READY_FILE
$exitCode = 1
$writer = $null
try {
    $GradleArguments = [string[]](ConvertFrom-Json -InputObject $env:VIBE_LAUNCH_ARGUMENTS -ErrorAction Stop)
    $env:VIBE_LAUNCH_ARGUMENTS = $null
    $writer = New-Object System.IO.StreamWriter($logFile, $true, (New-Object System.Text.UTF8Encoding($false)))
    $writer.AutoFlush = $true
    $env:VIBE_NO_PAUSE = '1'
    & (Join-Path $projectRoot 'build.bat') runClient -PvibeOptifine -PvibePersistentRun --console=plain --info --stacktrace @GradleArguments 2>&1 |
        ForEach-Object { $writer.WriteLine($_.ToString()) }
    $exitCode = $LASTEXITCODE
    $writer.WriteLine("[Vibe] Client task exited with code $exitCode. Log: $logFile")
} catch {
    if ($null -ne $writer) { $writer.WriteLine("[Vibe] Launch failed: $($_.Exception.Message)") }
    $exitCode = 1
} finally {
    if ($null -ne $writer) { $writer.Dispose() }
    if ($readyFile -and (Test-Path -LiteralPath $readyFile)) {
        Remove-Item -LiteralPath $readyFile -ErrorAction SilentlyContinue
    }
}
exit $exitCode
