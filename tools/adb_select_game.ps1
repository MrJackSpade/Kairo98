param(
    [Parameter(Mandatory = $true)][string]$Title,
    [string]$Adb = 'C:\bin\platform-tools\adb.exe'
)

if (-not (Test-Path -LiteralPath $Adb)) { throw "ADB not found: $Adb" }
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Title)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
& $Adb shell am start -S -n com.mrjackspade.kairo98/.MainActivity --es kairo98.selectGame64 $encoded
if ($LASTEXITCODE -ne 0) { throw "ADB launch failed with exit code $LASTEXITCODE" }
