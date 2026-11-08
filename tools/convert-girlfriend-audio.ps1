param([string]$SoundFileDll = "$PSScriptRoot/../build/audio-tooling/_soundfile_data/libsndfile_x64.dll")
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath("$PSScriptRoot/..")
$nativeLibrary = [IO.Path]::GetFullPath($SoundFileDll).Replace('\','/')
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class CompanionSoundConversion {
    [StructLayout(LayoutKind.Sequential)] public struct Info { public long frames; public int samplerate, channels, format, sections, seekable; }
    [DllImport("$nativeLibrary", CallingConvention=CallingConvention.Cdecl)] public static extern IntPtr sf_open(string path,int mode,ref Info info);
    [DllImport("$nativeLibrary", CallingConvention=CallingConvention.Cdecl)] public static extern long sf_readf_float(IntPtr file,[Out]float[] data,long frames);
    [DllImport("$nativeLibrary", CallingConvention=CallingConvention.Cdecl)] public static extern long sf_writef_float(IntPtr file,float[] data,long frames);
    [DllImport("$nativeLibrary", CallingConvention=CallingConvention.Cdecl)] public static extern int sf_close(IntPtr file);
    [DllImport("$nativeLibrary", CallingConvention=CallingConvention.Cdecl)] public static extern IntPtr sf_strerror(IntPtr file);
}
"@
$sourceRoot = Join-Path $repoRoot 'src/main/resources/assets/vibe/girlfriend'
$destinationRoot = Join-Path $repoRoot 'src/main/resources/assets/vibe/sounds/girlfriend'
$groupNames = @{ 'attacking'='attacking'; 'dying'='dying'; 'gettingDamaged'='hurt'; 'eating/vegan'='eating_vegan'; 'eating/brutal'='eating_other'; 'blockBreaking/BreakingBed'='breaking_bed'; 'blockBreaking'='breaking_other' }
$soundJson = Join-Path $repoRoot 'src/main/resources/assets/vibe/sounds.json'
$events = Get-Content -LiteralPath $soundJson -Raw | ConvertFrom-Json -AsHashtable
$groups = @{}
$converted = 0
foreach ($source in Get-ChildItem -LiteralPath $sourceRoot -Filter '*.mp3' -Recurse) {
    $relative = [IO.Path]::GetRelativePath($sourceRoot,$source.FullName).Replace('\','/')
    $folder = $relative.Substring(0,$relative.LastIndexOf('/'))
    $name = $relative.Substring(0,$relative.Length-4).ToLowerInvariant()
    $destination = Join-Path $destinationRoot ($name+'.ogg')
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
    $inputInfo = New-Object CompanionSoundConversion+Info
    $inputFile = [CompanionSoundConversion]::sf_open($source.FullName,0x10,[ref]$inputInfo)
    if ($inputFile -eq [IntPtr]::Zero) { throw "Cannot decode $relative" }
    $outputFile = [IntPtr]::Zero
    try {
        $outputInfo = New-Object CompanionSoundConversion+Info
        $outputInfo.samplerate=$inputInfo.samplerate; $outputInfo.channels=1; $outputInfo.format=0x200060
        $outputFile=[CompanionSoundConversion]::sf_open($destination,0x20,[ref]$outputInfo)
        if ($outputFile -eq [IntPtr]::Zero) { throw "Cannot create mono Vorbis $destination" }
        $samples = New-Object float[] (4096*$inputInfo.channels)
        $mono = New-Object float[] 4096
        $totalFrames = 0L
        while (($frames=[CompanionSoundConversion]::sf_readf_float($inputFile,$samples,4096)) -gt 0) {
            for ($i=0;$i -lt $frames;$i++) { $value=0.0; for($channel=0;$channel -lt $inputInfo.channels;$channel++){ $value+=$samples[$i*$inputInfo.channels+$channel] }; $mono[$i]=$value/$inputInfo.channels }
            if ([CompanionSoundConversion]::sf_writef_float($outputFile,$mono,$frames) -ne $frames) { throw 'Short audio write' }
            $totalFrames+=$frames
        }
        if ($totalFrames -lt 1) { throw "Empty voice clip $relative" }
    } finally {
        [CompanionSoundConversion]::sf_close($inputFile) | Out-Null
        if($outputFile -ne [IntPtr]::Zero){[CompanionSoundConversion]::sf_close($outputFile) | Out-Null}
    }
    $checkInfo=New-Object CompanionSoundConversion+Info
    $check=[CompanionSoundConversion]::sf_open($destination,0x10,[ref]$checkInfo)
    if ($check -eq [IntPtr]::Zero -or $checkInfo.channels -ne 1 -or [Math]::Abs($checkInfo.frames-$totalFrames) -gt 1) { throw "Invalid mono conversion: $relative" }
    [CompanionSoundConversion]::sf_close($check) | Out-Null
    $key='girlfriend.'+$groupNames[$folder]
    if(!$groups.ContainsKey($key)){$groups[$key]=[Collections.Generic.List[object]]::new()}
    $groups[$key].Add(@{name='vibe:girlfriend/'+$name; stream=$true})
    $converted++
}
foreach($key in ($groups.Keys | Sort-Object)){$events[$key]=@{category='player';sounds=$groups[$key].ToArray()}}
$events | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $soundJson -Encoding utf8NoBOM
Write-Output "Converted and verified $converted mono Vorbis clips in $($groups.Count) positional voice groups."
