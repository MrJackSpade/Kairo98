param(
    [Parameter(ParameterSetName = 'Title', Mandatory = $true)][string]$Title,
    [Parameter(ParameterSetName = 'ContentId', Mandatory = $true)][string]$ContentId,
    [string]$Adb = 'C:\bin\platform-tools\adb.exe'
)

if (-not (Test-Path -LiteralPath $Adb)) { throw "ADB not found: $Adb" }
$query = if ($PSCmdlet.ParameterSetName -eq 'ContentId') { $ContentId } else { $Title }
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($query)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
& $Adb shell am start -S -n com.mrjackspade.kairo98/.MainActivity --es kairo98.launchGame64 $encoded
if ($LASTEXITCODE -ne 0) { throw "ADB launch failed with exit code $LASTEXITCODE" }
