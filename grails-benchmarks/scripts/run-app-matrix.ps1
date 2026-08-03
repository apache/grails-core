# Broad app-bench matrix: non-indy then indy for each gated AppBench spec.
param(
    [int]$Warmup = 100,
    [int]$Samples = 400,
    [int]$Forks = 2,
    [string]$Mode = 'both' # both | false | true
)

$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location -LiteralPath $root
$outDir = Join-Path $root 'build\app-bench-wide'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$log = Join-Path $outDir 'matrix-run.log'
"start $(Get-Date -Format o)" | Set-Content -LiteralPath $log

$jobs = @(
    @{ Name='latency'; Project=':grails-test-examples-latency'; Tests='latencyapp.AppBenchFastPingSpec' },
    @{ Name='app1-interceptor'; Project=':grails-test-examples-app1'; Tests='functionaltests.AppBenchInterceptorDemoSpec' },
    @{ Name='app1-misc'; Project=':grails-test-examples-app1'; Tests='functionaltests.AppBenchMiscSpec' },
    @{ Name='gsp-layout'; Project=':grails-test-examples-gsp-layout'; Tests='org.example.grails.layout.AppBenchDemoRenderTextSpec' },
    @{ Name='demo33'; Project=':grails-test-examples-demo33'; Tests='demo.AppBenchHelloSpec' },
    @{ Name='issue-11102'; Project=':grails-test-examples-issue-11102'; Tests='issue11102.AppBenchGet1Spec' },
    @{ Name='hyphenated'; Project=':grails-test-examples-hyphenated'; Tests='hyphenated.AppBenchFooBarSpec' },
    @{ Name='jetty'; Project=':grails-test-examples-jetty'; Tests='issue12688.AppBenchSessionIndexSpec' },
    @{ Name='undertow'; Project=':grails-test-examples-undertow'; Tests='undertowapp.AppBenchSessionIndexSpec' },
    @{ Name='cache'; Project=':grails-test-examples-cache'; Tests='com.demo.AppBenchBasicCachingServiceSpec' },
    @{ Name='namespaces'; Project=':grails-test-examples-namespaces'; Tests='namespaces.AppBenchImplicitViewSpec' },
    @{ Name='views-json'; Project=':grails-test-examples-views-functional-tests'; Tests='functional.tests.AppBenchRespondJsonSpec' },
    @{ Name='h7-app1-misc'; Project=':grails-test-examples-hibernate7-app1'; Tests='functionaltests.AppBenchMiscSpec' }
)

$modes = @()
if ($Mode -eq 'both' -or $Mode -eq 'false') { $modes += 'false' }
if ($Mode -eq 'both' -or $Mode -eq 'true') { $modes += 'true' }

$summary = @()
foreach ($job in $jobs) {
    foreach ($indy in $modes) {
        $label = if ($indy -eq 'true') { 'indy' } else { 'noindy' }
        $outFile = Join-Path $outDir "$($job.Name)-$label.json"
        if (Test-Path $outFile) { Remove-Item -LiteralPath $outFile -Force }
        $msg = "=== $($job.Name) indy=$indy ==="
        Write-Host $msg
        Add-Content -LiteralPath $log -Value $msg
        $outArg = ($outFile -replace '\\','/')
        & "$root\gradlew.bat" "$($job.Project):integrationTest" `
            "-PgrailsIndy=$indy" `
            '-PappBench=true' `
            "-PappBenchWarmup=$Warmup" `
            "-PappBenchSamples=$Samples" `
            "-PappBenchForks=$Forks" `
            "-PappBenchOut=$outArg" `
            '--tests' $job.Tests
        $code = $LASTEXITCODE
        $ok = ($code -eq 0) -and (Test-Path -LiteralPath $outFile)
        $line = "$($job.Name),$label,exit=$code,ok=$ok,out=$outFile"
        Add-Content -LiteralPath $log -Value $line
        $summary += [pscustomobject]@{ App=$job.Name; Mode=$label; Exit=$code; Ok=$ok; File=$outFile }
        if (-not $ok) {
            Write-Host "FAILED $line"
        }
    }
}

# Per-app compares where both modes ok
foreach ($job in $jobs) {
    $base = Join-Path $outDir "$($job.Name)-noindy.json"
    $head = Join-Path $outDir "$($job.Name)-indy.json"
    $report = Join-Path $outDir "$($job.Name)-indy-vs-noindy.md"
    if ((Test-Path $base) -and (Test-Path $head)) {
        & "$root\gradlew.bat" '-q' ':grails-benchmarks:jmhCompare' `
            "--args=--base $($base -replace '\\','/') --head $($head -replace '\\','/') --output $($report -replace '\\','/')"
    }
}

# Merge all successful
$allNo = [System.Collections.Generic.List[object]]::new()
$allYes = [System.Collections.Generic.List[object]]::new()
foreach ($job in $jobs) {
    $base = Join-Path $outDir "$($job.Name)-noindy.json"
    $head = Join-Path $outDir "$($job.Name)-indy.json"
    if (Test-Path $base) {
        $j = Get-Content -LiteralPath $base -Raw | ConvertFrom-Json
        if ($j -is [System.Array]) { foreach ($e in $j) { $allNo.Add($e) } } else { $allNo.Add($j) }
    }
    if (Test-Path $head) {
        $j = Get-Content -LiteralPath $head -Raw | ConvertFrom-Json
        if ($j -is [System.Array]) { foreach ($e in $j) { $allYes.Add($e) } } else { $allYes.Add($j) }
    }
}
$mergedNo = Join-Path $outDir 'ALL-noindy.json'
$mergedYes = Join-Path $outDir 'ALL-indy.json'
($allNo | ConvertTo-Json -Depth 30) | Set-Content -LiteralPath $mergedNo -Encoding utf8
($allYes | ConvertTo-Json -Depth 30) | Set-Content -LiteralPath $mergedYes -Encoding utf8
if ($allNo.Count -gt 0 -and $allYes.Count -gt 0) {
    & "$root\gradlew.bat" '-q' ':grails-benchmarks:jmhCompare' `
        "--args=--base $($mergedNo -replace '\\','/') --head $($mergedYes -replace '\\','/') --output $((Join-Path $outDir 'ALL-indy-vs-noindy.md') -replace '\\','/')"
}

$summary | Format-Table -AutoSize | Out-String | Add-Content -LiteralPath $log
"done $(Get-Date -Format o)" | Add-Content -LiteralPath $log
Write-Host "Done. See $outDir"
$summary | Format-Table -AutoSize
