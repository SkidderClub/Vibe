# OpenReadAsync returns an interface-only COM object in Windows PowerShell 5.1.
# Its inherited Size/GetInputStreamAt members must be invoked on the WinRT
# interface, not through PowerShell's dynamic member lookup.
function Read-VibeMediaArtwork($Thumbnail) {
    if ($null -eq $Thumbnail) { return '' }
    $randomType = [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType=WindowsRuntime]
    $inputType = [Windows.Storage.Streams.IInputStream, Windows.Storage.Streams, ContentType=WindowsRuntime]
    $contentType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType=WindowsRuntime]
    $dataReaderType = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType=WindowsRuntime]
    $stream = $null; $reader = $null
    try {
        $stream = Await ($Thumbnail.OpenReadAsync()) $contentType
        $length = [uint64]$randomType.GetProperty('Size').GetValue($stream, $null)
        if ($length -eq 0) { return '' }
        if ($length -gt 8000000) { throw 'Media thumbnail exceeds 8 MB' }
        $input = $randomType.GetMethod('GetInputStreamAt').Invoke($stream, @([uint64]0))
        $reader = $dataReaderType.GetConstructor(@($inputType)).Invoke(@($input))
        $loaded = Await ($reader.LoadAsync([uint32]$length)) ([uint32])
        if ($loaded -ne $length) { throw 'Incomplete media thumbnail' }
        $bytes = New-Object byte[] $loaded
        $reader.ReadBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    } finally {
        if ($null -ne $reader) { try { ([IDisposable]$reader).Dispose() } catch { } }
        if ($null -ne $stream) { try { ([IDisposable]$stream).Dispose() } catch { } }
    }
}
