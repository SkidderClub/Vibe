# Keep raw arguments: advanced parameter binding would consume Gradle's --debug
# as PowerShell's own common Debug parameter.
$GradleArguments = [string[]]$args
$keepConsole = $GradleArguments -contains '--keep-console'
$GradleArguments = [string[]]@($GradleArguments | Where-Object { $_ -ne '--keep-console' })
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $projectRoot 'logs'
$exitCode = 1
$reader = $null
$worker = $null
$readyFile = $null

<<<<<<< HEAD
function Import-FirstRunMinecraftOptions {
    param([string]$ProjectRoot)
    $profile = Join-Path $ProjectRoot 'run\client'
    $marker = Join-Path $profile '.vibe-minecraft-settings-imported'
    if (Test-Path -LiteralPath $marker) { return }
    New-Item -ItemType Directory -Path $profile -Force -ErrorAction Stop | Out-Null
    $appData = $env:APPDATA
    if ([string]::IsNullOrWhiteSpace($appData)) { $appData = [Environment]::GetFolderPath('ApplicationData') }
    $minecraft = if ([string]::IsNullOrWhiteSpace($appData)) { $null } else { Join-Path $appData '.minecraft' }
    if ($minecraft -and (Test-Path -LiteralPath $minecraft -PathType Container)) {
        Get-ChildItem -LiteralPath $minecraft -File -Filter 'options*.txt' -ErrorAction SilentlyContinue | ForEach-Object {
            $destination = Join-Path $profile $_.Name
            if (-not (Test-Path -LiteralPath $destination)) {
                Copy-Item -LiteralPath $_.FullName -Destination $destination -Force -ErrorAction Stop
            }
        }
        foreach ($folderName in @('resourcepacks', 'texturepacks')) {
            $source = Join-Path $minecraft $folderName
            $destination = Join-Path $profile $folderName
            if (-not (Test-Path -LiteralPath $source -PathType Container)) { continue }
            New-Item -ItemType Directory -Path $destination -Force -ErrorAction Stop | Out-Null
            Get-ChildItem -LiteralPath $source -Force -ErrorAction SilentlyContinue | ForEach-Object {
                $target = Join-Path $destination $_.Name
                if (-not (Test-Path -LiteralPath $target)) {
                    Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force -ErrorAction Stop
                }
            }
        }
    }
    [System.IO.File]::WriteAllText($marker, 'completed' + [Environment]::NewLine)
}

try {
    # A shared baseline supplies video settings and selected pack files to
    # every isolated game instance. It is imported only once and never
    # overwrites Vibe's existing client profile.
    Import-FirstRunMinecraftOptions -ProjectRoot $projectRoot
=======
try {
>>>>>>> f485846c52ed7e011bd8968e6d4d06cf0ec60922
    New-Item -ItemType Directory -Path $logDirectory -Force -ErrorAction Stop | Out-Null
    $stamp = Get-Date -Format 'yyyy-MM-dd-HH-mm-ss-fff'
    $logFile = Join-Path $logDirectory "$stamp-$PID.log"
    $readyFile = Join-Path $logDirectory "$stamp-$PID.ready"
<<<<<<< HEAD
    # Propagate a per-launch directory id to the detached Gradle worker. A
    # PID plus timestamp stays unique even when two launchers start together.
    $env:VIBE_INSTANCE_ID = "$PID-$stamp"
=======
>>>>>>> f485846c52ed7e011bd8968e6d4d06cf0ec60922
    [System.IO.File]::WriteAllText($logFile, '', (New-Object System.Text.UTF8Encoding($false)))
    $message = "[Vibe] Live debug output; log: $logFile"
    [System.IO.File]::AppendAllText($logFile, $message + [Environment]::NewLine)

    # The detached worker owns Gradle and the log. Closing the startup console
    # therefore cannot close Minecraft or stop logging. Environment variables
    # carry arguments as data, without another round of command-line quoting.
    $env:VIBE_LAUNCH_ARGUMENTS = ConvertTo-Json -InputObject $GradleArguments -Compress
    $env:VIBE_LAUNCH_LOG_FILE = $logFile
    $env:VIBE_LAUNCH_READY_FILE = $readyFile
    $workerScript = Join-Path $PSScriptRoot 'run-client-worker.ps1'
    $workerCommand = "& '" + $workerScript.Replace("'", "''") + "'" + '; exit $LASTEXITCODE'
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($workerCommand))
    $worker = Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -PassThru -ArgumentList @(
        '-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $encodedCommand)
    $stream = [System.IO.File]::Open($logFile, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read,
        [System.IO.FileShare]::ReadWrite)
    $reader = New-Object System.IO.StreamReader($stream, [Text.Encoding]::UTF8)
    while ($true) {
        while (-not $reader.EndOfStream) { Write-Output $reader.ReadLine() }
        $worker.Refresh()
        if ($worker.HasExited) {
            $worker.WaitForExit()
            while (-not $reader.EndOfStream) { Write-Output $reader.ReadLine() }
            $exitCode = $worker.ExitCode
            if ($exitCode -ne 0) { Write-Output "[Vibe] Launch failed. See $logFile" }
            break
        }
        if (-not $keepConsole -and (Test-Path -LiteralPath $readyFile)) {
            Write-Output "[Vibe] Minecraft is ready. Logging continues in $logFile"
            $exitCode = 0
            break
        }
        Start-Sleep -Milliseconds 100
    }
} catch {
    Write-Output "[Vibe] Launch failed: $($_.Exception.Message)"
    $exitCode = 1
} finally {
    if ($null -ne $reader) { $reader.Dispose() }
    if ($null -ne $worker) { $worker.Dispose() }
    if ($readyFile -and (Test-Path -LiteralPath $readyFile)) {
        Remove-Item -LiteralPath $readyFile -ErrorAction SilentlyContinue
    }
}

exit $exitCode
