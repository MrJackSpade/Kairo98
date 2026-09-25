$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$compilerDbs = @(Get-ChildItem -LiteralPath (Join-Path $root 'app/.cxx') -Recurse -Filter 'compile_commands.json' -File)
$apkPaths = @(
    'app/build/outputs/apk/withImages/debug/app-withImages-debug.apk',
    'app/build/outputs/apk/withoutImages/debug/app-withoutImages-debug.apk'
)
$bundlePaths = @(
    'app/build/outputs/bundle/withImagesRelease/app-withImages-release.aab',
    'app/build/outputs/bundle/withoutImagesRelease/app-withoutImages-release.aab'
)

function Sha256([System.IO.Stream]$stream) {
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $hasher.Dispose() }
}

$commands = $null
foreach ($db in $compilerDbs) {
    $candidate = Get-Content -LiteralPath $db.FullName -Raw | ConvertFrom-Json
    if ($candidate.Count -ge 200 -and @($candidate | Where-Object { $_.command -match '-DSUPPORT_YMFM' }).Count -gt 0) {
        $commands = $candidate
        break
    }
}
if ($null -eq $commands) { throw 'No ymfm native compile database found' }
if ($commands.Count -lt 200) { throw "Only $($commands.Count) native compile inputs" }
$sourcePaths = @($commands | ForEach-Object { $_.file.Replace('\', '/') })
$blockedSource = @($sourcePaths | Where-Object { $_ -match '(?i)/sound/(fmgen|mame)/|fpemul_dosbox|/np2tool/' })
if ($blockedSource.Count) { throw "Blocked compile inputs: $($blockedSource -join ', ')" }
$blockedFlags = @($commands | Where-Object { $_.command -match '(?i)-D(SUPPORT_FMGEN|USE_MAME|USE_MAME_BSD|FPU_DOSBOX)(\s|=)' })
if ($blockedFlags.Count) { throw 'Blocked native build definition found' }
$np21w = @($sourcePaths | Where-Object { $_ -match '/third_party/np21w/' })
$ymfm = @($sourcePaths | Where-Object { $_ -match '/third_party/ymfm/' })
$hostSources = @($sourcePaths | Where-Object { $_ -match '/app/src/main/cpp/' })
if ($ymfm.Count -ne 3 -or @($hostSources | Where-Object { $_ -match '/ymfm_bridge.cpp$' }).Count -ne 1) {
    throw 'Expected ymfm bridge and three pinned ymfm sources'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$noticeFile = [System.IO.File]::OpenRead((Join-Path $root 'app/src/main/assets/THIRD_PARTY_NOTICES.txt'))
try { $expectedNoticeHash = Sha256 $noticeFile } finally { $noticeFile.Dispose() }
$noticeText = [System.IO.File]::ReadAllText((Join-Path $root 'app/src/main/assets/THIRD_PARTY_NOTICES.txt'))
if ($noticeText -notmatch 'Android NDK 28\.2\.13676358 LLVM' -or $noticeText -notmatch 'libc\+\+abi') {
    throw 'Static C++ runtime notice missing'
}
$packages = @()
$sharedAssets = @{}
$nativeHashes = @()
foreach ($relativePath in @($apkPaths) + @($bundlePaths)) {
    $prefix = if ($relativePath -like '*.aab') { 'base/' } else { '' }
    $path = Join-Path $root $relativePath
    $file = [System.IO.File]::OpenRead($path)
    try { $apkHash = Sha256 $file } finally { $file.Dispose() }
    $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
    try {
        $entries = @($zip.Entries)
        $blockedEntries = @($entries | Where-Object {
            $_.FullName -match '(?i)fmgen|dosbox|(^|/)bios[0-9]*\.(rom|bin)$|(^|/)font\.bmp$|(^|/)ym2608_adpcm_rom\.bin$|\.(hdi|fdi|d88|iso|chd)$|(^|/)mame/'
        })
        if ($blockedEntries.Count) { throw "Blocked packaged entries in $relativePath" }
        $native = @($entries | Where-Object { $_.FullName -like "${prefix}lib/*" })
        if ($native.Count -ne 1 -or $native[0].FullName -ne "${prefix}lib/arm64-v8a/libkairo98.so") {
            throw "Unexpected native libraries in $relativePath"
        }
        $notices = @($entries | Where-Object { $_.FullName -eq "${prefix}assets/THIRD_PARTY_NOTICES.txt" })
        if ($notices.Count -ne 1) { throw "Notices missing from $relativePath" }
        $libStream = $native[0].Open()
        try { $nativeHashes += Sha256 $libStream } finally { $libStream.Dispose() }
        $assets = @($entries | Where-Object { $_.FullName -like "${prefix}assets/*" })
        $assetHashes = @{}
        foreach ($entry in $assets) {
            if ($entry.FullName -like "${prefix}assets/art/*") { continue }
            $stream = $entry.Open()
            try { $assetHashes[$entry.FullName.Substring($prefix.Length)] = Sha256 $stream } finally { $stream.Dispose() }
        }
        $sharedAssets[$relativePath] = $assetHashes
        if ($assetHashes['assets/THIRD_PARTY_NOTICES.txt'] -ne $expectedNoticeHash) {
            throw "Packaged notices differ from source in $relativePath"
        }
        $packages += [pscustomobject]@{
            path = $relativePath
            sha256 = $apkHash
            bytes = (Get-Item -LiteralPath $path).Length
            entries = $entries.Count
            assets = $assets.Count
            libSha256 = $nativeHashes[-1]
            noticeSha256 = $assetHashes['assets/THIRD_PARTY_NOTICES.txt']
        }
    } finally { $zip.Dispose() }
}
foreach ($pair in @(@($apkPaths), @($bundlePaths))) {
    $left = $sharedAssets[$pair[0]]
    $right = $sharedAssets[$pair[1]]
    if ($left.Count -ne $right.Count) { throw 'Shared asset counts differ' }
    foreach ($key in $left.Keys) {
        if ($left[$key] -ne $right[$key]) { throw "Shared asset differs: $key" }
    }
}
if ($nativeHashes[0] -ne $nativeHashes[1] -or $nativeHashes[2] -ne $nativeHashes[3]) { throw 'Native libraries differ between variants' }
if ($sharedAssets[$apkPaths[0]]['assets/THIRD_PARTY_NOTICES.txt'] -ne $sharedAssets[$bundlePaths[0]]['assets/THIRD_PARTY_NOTICES.txt']) { throw 'APK and AAB notices differ' }

[pscustomobject]@{
    nativeCompileInputs = $commands.Count
    np21wInputs = $np21w.Count
    ymfmInputs = $ymfm.Count
    projectInputs = $hostSources.Count
    supportYmfm = (@($commands | Where-Object { $_.command -match '-DSUPPORT_YMFM' }).Count -gt 0)
    sharedAssets = $left.Count
    packages = $packages
} | ConvertTo-Json -Depth 5
