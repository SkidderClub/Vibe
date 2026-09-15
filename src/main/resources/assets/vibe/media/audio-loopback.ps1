param([int]$ParentId)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$capture = $null
try {
    Add-Type -Path (Join-Path $PSScriptRoot 'AudioLoopback.cs')
    $parentProcess = [Diagnostics.Process]::GetProcessById($ParentId)
    $checkAt = [DateTime]::UtcNow
    while (-not $parentProcess.HasExited) {
        try {
            if ($null -eq $capture) { $capture = New-Object VibeAudio.Loopback }
            if ([DateTime]::UtcNow -ge $checkAt) {
                if (-not $capture.IsDefaultDevice()) { $capture.Dispose(); $capture = New-Object VibeAudio.Loopback }
                $checkAt = [DateTime]::UtcNow.AddSeconds(2)
            }
            $bytes = $capture.Read()
            if ($bytes.Length -gt 0) { [Console]::WriteLine('PCM:' + $capture.SampleRate + ':' + [Convert]::ToBase64String($bytes)) }
        } catch {
            @{error='System audio unavailable: ' + $_.Exception.Message} | ConvertTo-Json -Compress | ForEach-Object { [Console]::WriteLine($_) }
            if ($null -ne $capture) { $capture.Dispose(); $capture = $null }
            Start-Sleep -Milliseconds 2000
        }
        [Console]::Out.Flush()
        Start-Sleep -Milliseconds 33
        $parentProcess.Refresh()
    }
} catch {
    @{error='System audio unavailable: ' + $_.Exception.Message} | ConvertTo-Json -Compress | ForEach-Object { [Console]::WriteLine($_) }
} finally { if ($null -ne $capture) { $capture.Dispose() } }
