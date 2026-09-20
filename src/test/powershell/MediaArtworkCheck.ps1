$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing
. (Join-Path $PSScriptRoot '../../main/resources/assets/vibe/media/media-artwork.ps1')
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetGenericArguments().Count -eq 1 -and
    $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1
function Await($Operation, [Type]$ResultType) {
    $task = $asTask.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
    if (-not $task.Wait(3000)) { throw 'Test media query timed out' }
    return $task.Result
}
$fileType = [Windows.Storage.StorageFile, Windows.Storage, ContentType=WindowsRuntime]
$referenceType = [Windows.Storage.Streams.RandomAccessStreamReference, Windows.Storage.Streams, ContentType=WindowsRuntime]
if ((Read-VibeMediaArtwork $null) -ne '') { throw 'Absent artwork must use fallback' }
$image = New-Object Drawing.Bitmap 32,18
$image.SetPixel(3,3,[Drawing.Color]::Red)
try {
    foreach ($format in @([Drawing.Imaging.ImageFormat]::Png,[Drawing.Imaging.ImageFormat]::Jpeg)) {
        $path = [IO.Path]::GetTempFileName()
        try {
            $image.Save($path,$format)
            $file = Await ($fileType::GetFileFromPathAsync($path)) $fileType
            $thumbnail = $referenceType::CreateFromFile($file)
            $actual = Read-VibeMediaArtwork $thumbnail
            $expected = [Convert]::ToBase64String([IO.File]::ReadAllBytes($path))
            if ($actual -ne $expected) { throw "$format thumbnail corrupted or missing" }
        } finally { Remove-Item -LiteralPath $path -Force }
    }
} finally { $image.Dispose() }
Write-Output 'Windows WinRT PNG/JPEG thumbnails match their source bytes; missing artwork uses fallback.'
