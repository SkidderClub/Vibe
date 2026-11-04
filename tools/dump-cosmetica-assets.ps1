<#
Build an offline Cosmetica catalogue for Vibe.

The upstream 1.16.5 source contains the Cosmetica client/UI assets. This script
merges new accessories and 1.8.9-compatible capes from the public catalogue,
then downloads their models, textures and thumbnails into
extensions/cosmetica/src/main/resources/assets/vibe/cosmetica so Gradle packages them inside the Cosmetica extension JAR. It is resumable:
already downloaded files are never requested again.
#>
param(
    [int]$Threads = 8,
    [switch]$RefreshCatalog,
    [int]$StartPage = 1,
    [int]$EndPage = 0,
    [switch]$MetadataOnly,
    [int]$StartAsset = 1,
    [int]$EndAsset = 0
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $projectRoot 'extensions\cosmetica\src\main\resources\assets\vibe\cosmetica'
$modelDirectory = Join-Path $destination 'models'
$textureDirectory = Join-Path $destination 'textures'
$thumbnailDirectory = Join-Path $destination 'thumbnails'
$skinDirectory = Join-Path $destination 'skins'
$catalogFile = Join-Path $destination 'catalog.json'

New-Item -ItemType Directory -Force -Path $modelDirectory, $textureDirectory, $thumbnailDirectory, $skinDirectory | Out-Null

function Get-Extension([string]$Url, [string]$Fallback) {
    try {
        $extension = [System.IO.Path]::GetExtension(([uri]$Url).AbsolutePath)
        if ($extension -match '^\.(png|webp|jpg|jpeg|json)$') { return $extension.ToLowerInvariant() }
    } catch { }
    return $Fallback
}

function Invoke-CosmeticaRequest([string]$Uri, [string]$Body) {
    $lastError = $null
    for ($attempt = 1; $attempt -le 6; $attempt++) {
        try {
            if ($Body) { return Invoke-RestMethod -Uri $Uri -Method Post -ContentType 'application/json' -Body $Body -TimeoutSec 45 }
            return Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 60
        } catch {
            $lastError = $_
            $wait = [Math]::Min(20, 2 * $attempt)
            Write-Warning ("[Cosmetica] Request failed ({0}/6): {1}. Retrying in {2}s." -f $attempt, $_.Exception.Message, $wait)
            Start-Sleep -Seconds $wait
        }
    }
    throw $lastError
}

if (-not (Test-Path -LiteralPath $catalogFile) -or $RefreshCatalog -or $StartPage -gt 1 -or $EndPage -gt 0) {
    Write-Host '[Cosmetica] Reading public catalogue metadata...'
    $all = New-Object System.Collections.Generic.List[object]
    $known = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    if (Test-Path -LiteralPath $catalogFile) {
        foreach ($saved in @((Get-Content -Raw -LiteralPath $catalogFile | ConvertFrom-Json).accessories)) {
            if ($known.Add($saved.id)) { [void]$all.Add($saved) }
        }
    }
    # Search caps each query at 1,000 results. Fetch by attachment so capes
    # cannot push recent accessories out of the result window.
    $expectedPages = 0
    foreach ($attachment in @('head', 'body', 'arm', 'leg', 'cloak')) {
        $page = [Math]::Max(1, $StartPage)
        do {
            $request = @{ query = ''; pageSize = 30; page = $page; attachments = @($attachment); sortBy = 'newest' } | ConvertTo-Json -Compress
            $response = Invoke-CosmeticaRequest 'https://api.cloaks.gg/search/cosmetics' $request
            $pages = [int]$response.estimatedPages
            $expectedPages = [Math]::Max($expectedPages, $pages)
            if (!$response.results) { break }
            foreach ($result in $response.results) {
                $entry = $result.accessory ?? $result.animatedTextureCosmetic
                if ($null -eq $entry -or $entry.id -cnotmatch '^[A-Za-z0-9]+$' -or !$entry.texture -or !$entry.thumbnail) { continue }
                if (!$entry.model -and ($entry.type -ne 'cape' -or !($entry.flags -band 1))) { continue }
                if ($known.Add($entry.id)) { [void]$all.Add($entry) }
            }
            Write-Host ("[Cosmetica] {0} page {1}/{2} ({3} cosmetics)" -f $attachment, $page, $pages, $all.Count)
            $page++
        } while ($page -le $pages -and ($EndPage -le 0 -or $page -le $EndPage))
    }
    @{ schema = 1; downloadedAt = [DateTime]::UtcNow.ToString('o'); expectedPages = $expectedPages; accessories = @($all | Sort-Object dateUploaded -Descending) } | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $catalogFile -Encoding utf8
} else {
    Write-Host '[Cosmetica] Reusing existing catalogue metadata. Use -RefreshCatalog to fetch it again.'
}

$catalog = Get-Content -Raw -LiteralPath $catalogFile | ConvertFrom-Json
$expectedPages = if ($catalog.expectedPages) { [int]$catalog.expectedPages } else { 0 }
if ($MetadataOnly -or ($EndPage -gt 0 -and $EndPage -lt $expectedPages)) {
    Write-Host ("[Cosmetica] Metadata checkpoint saved ({0} accessories)." -f @($catalog.accessories).Count)
    exit 0
}
$accessories = @($catalog.accessories)
$firstAsset = [Math]::Max(1, $StartAsset)
$lastAsset = if ($EndAsset -gt 0) { [Math]::Min($accessories.Count, $EndAsset) } else { $accessories.Count }
$selectedAccessories = if ($lastAsset -ge $firstAsset) { @($accessories[($firstAsset - 1)..($lastAsset - 1)]) } else { @() }
$jobs = New-Object System.Collections.Generic.List[object]
foreach ($accessory in $selectedAccessories) {
    $id = [string]$accessory.id
    if ($id -cnotmatch '^[A-Za-z0-9]+$') { continue }
    if ($accessory.model) { [void]$jobs.Add([pscustomobject]@{ Url = [string]$accessory.model; Destination = Join-Path $modelDirectory ($id + '.json') }) }
    [void]$jobs.Add([pscustomobject]@{ Url = [string]$accessory.texture; Destination = Join-Path $textureDirectory ($id + (Get-Extension $accessory.texture '.png')) })
    if ($accessory.thumbnail -and !(Test-Path -LiteralPath (Join-Path $thumbnailDirectory ($id + '.png')))) {
        [void]$jobs.Add([pscustomobject]@{ Url = [string]$accessory.thumbnail; Destination = Join-Path $thumbnailDirectory ($id + (Get-Extension $accessory.thumbnail '.png')) })
    }
}

# Vibe's default cosmetic skin is local too; arbitrary user-entered skins still
# download on demand and safely fall back to vanilla Steve when unavailable.
if ($firstAsset -eq 1) { [void]$jobs.Add([pscustomobject]@{ Url = 'https://minotar.net/skin/xHeist_'; Destination = Join-Path $skinDirectory 'xheist_.png' }) }

$missing = @($jobs | Where-Object { $_.Url -and -not (Test-Path -LiteralPath $_.Destination) })
Write-Host ("[Cosmetica] Asset slice {0}-{1}/{2}: {3} local assets present; downloading {4} remaining assets with {5} workers..." -f $firstAsset, $lastAsset, $accessories.Count, ($jobs.Count - $missing.Count), $missing.Count, $Threads)

$result = $missing | ForEach-Object -Parallel {
    $target = $_.Destination
    try {
        $source = [uri]$_.Url
        if ($source.Scheme -ne 'https' -or $source.Host -ne 'cdn.cosmetica.cc' -and $source.Host -ne 'minotar.net') {
            throw 'Unexpected Cosmetica asset host'
        }
        $folder = Split-Path -Parent $target
        if (-not (Test-Path -LiteralPath $folder)) { New-Item -ItemType Directory -Force -Path $folder | Out-Null }
        $temporary = $target + '.part'
        Invoke-WebRequest -UseBasicParsing -Uri $_.Url -OutFile $temporary -TimeoutSec 60
        Move-Item -LiteralPath $temporary -Destination $target -Force
        [pscustomobject]@{ Ok = $true; Url = $_.Url; Error = '' }
    } catch {
        Remove-Item -LiteralPath ($target + '.part') -Force -ErrorAction SilentlyContinue
        [pscustomobject]@{ Ok = $false; Url = $_.Url; Error = $_.Exception.Message }
    }
} -ThrottleLimit ([Math]::Max(1, [Math]::Min(16, $Threads)))

$failed = @($result | Where-Object { -not $_.Ok })
if ($failed.Count -gt 0) {
    $failed | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $destination 'download-failures.json') -Encoding utf8
    Write-Warning ("[Cosmetica] {0} assets failed; rerun the script to resume them. See extensions/cosmetica/src/main/resources/assets/vibe/cosmetica/download-failures.json" -f $failed.Count)
} else {
    Remove-Item -LiteralPath (Join-Path $destination 'download-failures.json') -Force -ErrorAction SilentlyContinue
    Write-Host '[Cosmetica] Offline catalogue dump completed.'
}

# The public API serves thumbnail previews as WebP. Minecraft 1.8.9 cannot
# load WebP through its TextureManager, so create PNG siblings after dumping.
# The original downloads are retained for provenance and complete local assets.
$converter = Join-Path $projectRoot 'tools\convert-cosmetica-thumbnails.py'
$pythonCandidates = @(
    (Get-Command python -ErrorAction SilentlyContinue | ForEach-Object Source),
    (Get-Command py -ErrorAction SilentlyContinue | ForEach-Object Source)
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
if ((Test-Path -LiteralPath $converter) -and $pythonCandidates.Count -gt 0) {
    & $pythonCandidates[0] $converter $thumbnailDirectory
    if ($LASTEXITCODE -ne 0) { Write-Warning '[Cosmetica] Thumbnail PNG conversion failed; run tools/convert-cosmetica-thumbnails.py with Pillow.' }
    if (!$failed.Count -and $firstAsset -eq 1 -and $lastAsset -eq $accessories.Count) {
        & $pythonCandidates[0] $converter $thumbnailDirectory --render-textures
        if ($LASTEXITCODE -ne 0) { Write-Warning '[Cosmetica] Render texture preparation failed.' }
    }
} else {
    Write-Warning '[Cosmetica] WebP previews were downloaded, but no Python/Pillow runtime was found to create their required PNG GUI copies.'
}

# Preserve the UI textures that do ship with the provided upstream source as
# local reference assets too. They are separate from the creator catalogue.
$upstreamUi = Join-Path $projectRoot 'context\Cosmetica-2-1.16.5\common\src\main\resources\assets\cosmetica\textures'
if (Test-Path -LiteralPath $upstreamUi) {
    Copy-Item -LiteralPath $upstreamUi -Destination (Join-Path $destination 'upstream-ui') -Recurse -Force
}
