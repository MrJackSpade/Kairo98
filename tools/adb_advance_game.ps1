param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [ValidateRange(1, 3600)][int]$DurationSeconds = 240,
    [ValidateRange(75, 10000)][int]$IntervalMilliseconds = 100,
    [ValidateRange(0, 32)][int]$DisplayId = 2,
    [ValidateRange(1, 20)][int]$MaxAttempts = 3,
    [string]$Package = 'com.mrjackspade.kairo98',
    [string]$Activity = 'com.mrjackspade.kairo98/.MainActivity',
    [string]$Game = 'Night',
    [string]$StartupOption = 'regular',
    [string]$OutputDirectory = '.downloads/performance/night-slave'
)

$ErrorActionPreference = 'Stop'
$adb = (Get-Command adb -ErrorAction Stop).Source
$output = Join-Path (Get-Location) $OutputDirectory
New-Item -ItemType Directory -Force -Path $output | Out-Null
$expected = [int][Math]::Floor($DurationSeconds * 1000.0 / $IntervalMilliseconds)

function Device([string[]]$arguments) {
    # adb prints successful pull and simpleperf progress to stderr on Windows.
    $ErrorActionPreference = 'Continue'
    $result = & $adb -s $Serial @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed ($LASTEXITCODE): $($arguments -join ' ')`n$($result -join "`n")"
    }
    return $result
}

function Read-Metrics {
    $result = & $adb -s $Serial exec-out run-as $Package cat files/performance-auto.txt 2>$null
    if ($LASTEXITCODE -ne 0) { return @() }
    return @($result)
}

for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    $run = Join-Path $output ((Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmmss') +
        "-attempt$attempt")
    New-Item -ItemType Directory -Force -Path $run | Out-Null
    try {
        Write-Output "Attempt $attempt/${MaxAttempts}: cold launch $Game"
        [void](Device @('shell', 'am', 'force-stop', $Package))
        [void](Device @('exec-out', 'run-as', $Package, 'rm', '-f',
            'files/performance-auto.txt'))
        [void](Device @('shell', 'am', 'start', '-S', '--display', "$DisplayId",
            '-f', '0x10008000', '-n', $Activity,
            '--es', 'kairo98.launchGame', $Game,
            '--es', 'kairo98.startupOption', $StartupOption,
            '--ei', 'kairo98.autoAdvanceSeconds', "$DurationSeconds",
            '--ei', 'kairo98.autoAdvanceIntervalMs', "$IntervalMilliseconds"))
        $deadline = [Diagnostics.Stopwatch]::StartNew()
        $timeout = 180 + $DurationSeconds * 1.6
        $lastCount = -1
        $lastProgressSeconds = 0
        while ($deadline.Elapsed.TotalSeconds -lt $timeout) {
            Start-Sleep -Seconds 5
            $metrics = @(Read-Metrics)
            $sample = [string]($metrics | Where-Object { $_ -like 'sample*' } | Select-Object -Last 1)
            if ($sample -match '^sample\s+(\d+)\s+') {
                $count = [int]$Matches[1]
                if ($count -ne $lastCount) {
                    Write-Output "Space: $count/$expected"
                    $lastCount = $count
                    $lastProgressSeconds = $deadline.Elapsed.TotalSeconds
                }
            }
            if (@($metrics | Where-Object { $_ -like 'state*error*' }).Count -gt 0) {
                throw "App runner error: $($metrics | Select-Object -Last 1)"
            }
            if (@($metrics | Where-Object { $_ -like 'state*cancelled*' }).Count -gt 0) {
                throw 'App runner was cancelled before reaching combat'
            }
            $complete = [string]($metrics | Where-Object { $_ -like 'state*complete*' } |
                Select-Object -Last 1)
            if ($complete) {
                if ($complete -notmatch "^state\s+complete\s+$expected\s+") {
                    throw "Incomplete key sequence: $complete"
                }
                [IO.File]::WriteAllLines((Join-Path $run 'performance-auto.txt'), $metrics)
                $pidText = [string](Device @('shell', 'pidof', $Package))
                if ($pidText -notmatch '^\d+') { throw 'App exited before profiling' }
                $stat = @(Device @('shell', 'simpleperf', 'stat', '--app', $Package,
                    '--duration', '10', '--csv'))
                [IO.File]::WriteAllLines((Join-Path $run 'simpleperf-stat.csv'), $stat)
                try {
                    [void](Device @('shell', 'simpleperf', 'record', '--app', $Package,
                        '-e', 'cpu-cycles', '-f', '400', '--duration', '10',
                        '-o', '/data/local/tmp/kairo98-perf.data'))
                    $report = @(Device @('shell', 'simpleperf', 'report',
                        '-i', '/data/local/tmp/kairo98-perf.data', '--sort', 'symbol'))
                    [IO.File]::WriteAllLines((Join-Path $run 'simpleperf-report.txt'), $report)
                } catch {
                    [IO.File]::WriteAllText((Join-Path $run 'simpleperf-error.txt'), "$_")
                }
                [void](Device @('shell', 'screencap', '-d', '1', '-p',
                    '/sdcard/Download/kairo98-perf-frame.png'))
                [void](Device @('pull', '/sdcard/Download/kairo98-perf-frame.png',
                    (Join-Path $run 'final-frame.png')))
                Write-Output "Metrics saved: $run"
                Write-Output ($metrics | Select-Object -Last 2)
                exit 0
            }
            if ($lastCount -ge 0 -and
                $deadline.Elapsed.TotalSeconds - $lastProgressSeconds -gt 45) {
                throw "Key progress stalled at $lastCount/$expected"
            }
            if ($lastCount -lt 0 -and $deadline.Elapsed.TotalSeconds -gt 150) {
                throw 'Startup did not reach the repeat-input stage'
            }
        }
        throw "Timed out after $([int]$deadline.Elapsed.TotalSeconds) seconds"
    } catch {
        Write-Warning "Attempt $attempt failed: $_"
        [void](& $adb -s $Serial shell am force-stop $Package 2>&1)
        if ($attempt -eq $MaxAttempts) { throw }
    }
}
