param(
    [string]$Compiler = 'cl',
    [string]$OutputDirectory = ''
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (!$OutputDirectory) { $OutputDirectory = Join-Path $projectRoot '.downloads/idle-loop-test' }
$output = [IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($output) | Out-Null

# Replace only the core's include directives. The checker itself is compiled
# unchanged against the harness's minimal CPU state, with no copied algorithm.
$checker = [IO.File]::ReadAllText((Join-Path $projectRoot 'third_party/np21w/i386c/ia32/kairo98_idle.c'))
$checker = [regex]::Replace($checker, '(?m)^#include[^\r\n]*', '')
$harness = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'idle_loop_test.c'))
$source = Join-Path $output 'idle_loop_test.c'
$executable = Join-Path $output 'idle_loop_test.exe'
[IO.File]::WriteAllText($source, $harness.Replace('/* KAIRO98_IDLE_IMPLEMENTATION */', $checker))

# Run from a Visual Studio developer shell for cl, or pass a host C compiler.
if ([IO.Path]::GetFileNameWithoutExtension($Compiler) -eq 'cl') {
    & $Compiler /nologo /W3 /O2 $source "/Fe:$executable" "/Fo:$(Join-Path $output 'idle_loop_test.obj')"
} else {
    & $Compiler -std=c99 -O2 $source -o $executable
}
if ($LASTEXITCODE -ne 0) { throw 'Idle-loop test compilation failed' }
& $executable
if ($LASTEXITCODE -ne 0) { throw 'Idle-loop boundary comparison failed' }
