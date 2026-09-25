$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$notices = @(
    @('Neko Project 21/W rev104, base license', 'third_party/np21w/LICENSES/LICENSE.TXT', 932),
    @('Neko Project 21/W IA-32 core', 'third_party/np21w/LICENSES/LICENSE-I386C.TXT', 932),
    @('Neko Project 21/W IA-32 FPU stubs', 'third_party/np21w/LICENSES/LICENSE-I386C-FPU.TXT', 932),
    @('Neko Project 21/W SIMD stubs', 'third_party/np21w/LICENSES/LICENSE-I386C-SIMD.TXT', 932),
    @('Neko Project 21/W LIO', 'third_party/np21w/LICENSES/LICENSE-LIO.TXT', 932),
    @('ymfm', 'third_party/ymfm/LICENSE', 65001),
    @('Spleen 8x16 bitmap font', 'third_party/spleen/LICENSE', 65001),
    @('Android NDK 28.2.13676358 LLVM toolchain and statically linked C++ runtime', 'third_party/android-ndk-llvm-28.2.13676358/NOTICE', 65001)
)
$content = [System.Text.StringBuilder]::new()
[void]$content.AppendLine('Kairo98 third-party notices')
[void]$content.AppendLine('These notices apply to the emulator code, generated font, and statically linked C++ runtime in this package.')
foreach ($notice in $notices) {
    $title, $relativePath, $codePage = $notice
    $path = Join-Path $root $relativePath
    $source = [System.Text.Encoding]::GetEncoding($codePage).GetString([System.IO.File]::ReadAllBytes($path)).TrimEnd()
    $source = [System.Text.RegularExpressions.Regex]::Replace($source, '(?m)[ \t]+$', '')
    [void]$content.AppendLine()
    [void]$content.AppendLine(('=' * 72))
    [void]$content.AppendLine($title)
    [void]$content.AppendLine("Source: $relativePath")
    [void]$content.AppendLine(('=' * 72))
    [void]$content.AppendLine($source)
}
$destination = Join-Path $root 'app/src/main/assets/THIRD_PARTY_NOTICES.txt'
[System.IO.File]::WriteAllText($destination, $content.ToString(), [System.Text.UTF8Encoding]::new($false))
