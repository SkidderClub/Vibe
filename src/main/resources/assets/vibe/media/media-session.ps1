param([int]$ParentId, [string]$OwnerPriority = 'Spotify,Chrome,Firefox', [switch]$Once)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    . (Join-Path $PSScriptRoot 'media-artwork.ps1')
    $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
    $propertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetGenericArguments().Count -eq 1 -and
        $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    function Await($Operation, [Type]$ResultType) {
        $task = $asTask.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
        if (-not $task.Wait(3000)) { throw 'Media query timed out' }
        return $task.Result
    }
    $parentProcess = [Diagnostics.Process]::GetProcessById($ParentId)
    $manager = Await ($managerType::RequestAsync()) $managerType
    $priorities = $OwnerPriority.Split(',')
    $cachedKey = ''; $cachedArtwork = ''; $coverAge = 0; $artworkError = ''
    while (-not $parentProcess.HasExited) {
        try {
            # An idle high-priority player must not hide the video currently playing.
            $sessions = @($manager.GetSessions()) | Sort-Object @{Expression={
                try { if ([int]$_.GetPlaybackInfo().PlaybackStatus -eq 4) { 0 } else { 1 } } catch { 2 }
            }}, @{Expression={
                $owner = $_.SourceAppUserModelId
                $rank = $priorities.Length
                for ($i = 0; $i -lt $priorities.Length; $i++) {
                    if ($priorities[$i].Trim().Length -gt 0 -and $owner.IndexOf($priorities[$i].Trim(), [StringComparison]::OrdinalIgnoreCase) -ge 0) { $rank = $i; break }
                }
                $rank
            }}
            $session = $null; $properties = $null
            foreach ($candidate in $sessions) {
                try {
                    $candidateProperties = Await ($candidate.TryGetMediaPropertiesAsync()) $propertiesType
                    if ($candidateProperties.Title -or $candidateProperties.Artist) { $session = $candidate; $properties = $candidateProperties; break }
                } catch { }
            }
            if ($null -eq $session) {
                @{title='Waiting for media';artist='';owner='';playing=$false;position=0;duration=0;artwork='';status='Open a media player'} | ConvertTo-Json -Compress | ForEach-Object { [Console]::WriteLine($_) }
                $cachedKey = ''; $cachedArtwork = ''
            } else {
                $key = $session.SourceAppUserModelId + "`n" + $properties.Title + "`n" + $properties.Artist
                if ($cachedKey -ne $key -or $cachedArtwork -eq '' -or $coverAge -ge 10) {
                    if ($cachedKey -ne $key) { $cachedArtwork = ''; $artworkError = '' }
                    $cachedKey = $key; $coverAge = 0
                    if ($null -ne $properties.Thumbnail) {
                        try {
                            $cachedArtwork = Read-VibeMediaArtwork $properties.Thumbnail
                            $artworkError = ''
                        } catch { $artworkError = $_.Exception.Message }
                    }
                }
                $coverAge++
                $timeline = $session.GetTimelineProperties()
                $playback = $session.GetPlaybackInfo()
                $playing = [int]$playback.PlaybackStatus -eq 4
                $position = ($timeline.Position - $timeline.StartTime).TotalMilliseconds
                if ($playing) { $position += [Math]::Max(0, ([DateTimeOffset]::Now - $timeline.LastUpdatedTime).TotalMilliseconds) }
                $duration = ($timeline.EndTime - $timeline.StartTime).TotalMilliseconds
                @{
                    title=$properties.Title;artist=$properties.Artist;owner=$session.SourceAppUserModelId
                    playing=$playing;position=[long][Math]::Max(0,$(if($duration -gt 0){[Math]::Min($duration,$position)}else{$position}));duration=[long]$duration
                    artwork=$cachedArtwork;artworkError=$artworkError;status=$(if($playing){'Playing'}else{'Paused'})
                } | ConvertTo-Json -Compress | ForEach-Object { [Console]::WriteLine($_) }
            }
        } catch {
            @{error='Media session unavailable: ' + $_.Exception.Message} | ConvertTo-Json -Compress | ForEach-Object { [Console]::WriteLine($_) }
        }
        [Console]::Out.Flush()
        if ($Once) { break }
        Start-Sleep -Milliseconds 1000
        $parentProcess.Refresh()
    }
} catch {
    @{error='Windows media unavailable: ' + $_.Exception.Message} | ConvertTo-Json -Compress | ForEach-Object { [Console]::WriteLine($_) }
}
