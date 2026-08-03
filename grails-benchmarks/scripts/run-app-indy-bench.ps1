# Experimental: app-level indy vs non-indy HTTP benches against grails-test-examples.
# Run from repo root. Rebuilds framework+app with -PgrailsIndy, runs gated AppBench* specs,
# then compares JMH-shaped JSON with :grails-benchmarks:jmhCompare.
#
# Usage:
#   pwsh grails-benchmarks/scripts/run-app-indy-bench.ps1
#   pwsh grails-benchmarks/scripts/run-app-indy-bench.ps1 -Samples 500 -Warmup 100

param(
    [int]$Warmup = 200,
    [int]$Samples = 1000,
    [int]$Forks = 2
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location -LiteralPath $root

$outDir = Join-Path $root 'build\app-bench'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$apps = @(
    @{
        Name = 'latency'
        Project = ':grails-test-examples-latency'
        Tests = 'latencyapp.AppBenchFastPingSpec'
        OutName = 'latency'
    },
    @{
        Name = 'app1'
        Project = ':grails-test-examples-app1'
        Tests = 'functionaltests.AppBenchInterceptorDemoSpec'
        OutName = 'app1'
    },
    @{
        Name = 'gsp-layout'
        Project = ':grails-test-examples-gsp-layout'
        Tests = 'org.example.grails.layout.AppBenchDemoRenderTextSpec'
        OutName = 'gsp-layout'
    }
)

function Invoke-AppBenchMode {
    param(
        [string]$Indy,
        [hashtable]$App
    )
    $label = if ($Indy -eq 'true') { 'indy' } else { 'noindy' }
    $outFile = Join-Path $outDir "$($App.OutName)-$label.json"
    if (Test-Path -LiteralPath $outFile) {
        Remove-Item -LiteralPath $outFile -Force
    }

    Write-Host "=== $($App.Name) grailsIndy=$Indy -> $outFile ==="
    & "$root\gradlew.bat" 'clean' "$($App.Project):integrationTest" `
        "-PgrailsIndy=$Indy" `
        '-PappBench=true' `
        "-PappBenchWarmup=$Warmup" `
        "-PappBenchSamples=$Samples" `
        "-PappBenchForks=$Forks" `
        "-PappBenchOut=$($outFile -replace '\\','/')" `
        '--tests' $App.Tests
    if ($LASTEXITCODE -ne 0) {
        throw "App bench failed for $($App.Name) indy=$Indy (exit $LASTEXITCODE)"
    }
    if (-not (Test-Path -LiteralPath $outFile)) {
        throw "Missing result file: $outFile"
    }
}

foreach ($app in $apps) {
    Invoke-AppBenchMode -Indy 'false' -App $app
    Invoke-AppBenchMode -Indy 'true' -App $app

    $base = Join-Path $outDir "$($app.OutName)-noindy.json"
    $head = Join-Path $outDir "$($app.OutName)-indy.json"
    $report = Join-Path $outDir "$($app.OutName)-indy-vs-noindy.md"
    Write-Host "=== compare $($app.Name) ==="
    & "$root\gradlew.bat" '-q' ':grails-benchmarks:jmhCompare' `
        "--args=--base $($base -replace '\\','/') --head $($head -replace '\\','/') --output $($report -replace '\\','/')"
    if ($LASTEXITCODE -ne 0) {
        throw "jmhCompare failed for $($app.Name)"
    }
}

# Merged all-app compare
$mergedNo = Join-Path $outDir 'all-apps-noindy.json'
$mergedYes = Join-Path $outDir 'all-apps-indy.json'
$allNo = @()
$allYes = @()
foreach ($app in $apps) {
    $allNo += (Get-Content -LiteralPath (Join-Path $outDir "$($app.OutName)-noindy.json") -Raw | ConvertFrom-Json)
    $allYes += (Get-Content -LiteralPath (Join-Path $outDir "$($app.OutName)-indy.json") -Raw | ConvertFrom-Json)
}
($allNo | ConvertTo-Json -Depth 20) | Set-Content -LiteralPath $mergedNo -Encoding utf8
($allYes | ConvertTo-Json -Depth 20) | Set-Content -LiteralPath $mergedYes -Encoding utf8

& "$root\gradlew.bat" '-q' ':grails-benchmarks:jmhCompare' `
    "--args=--base $($mergedNo -replace '\\','/') --head $($mergedYes -replace '\\','/') --output $((Join-Path $outDir 'all-apps-indy-vs-noindy.md') -replace '\\','/')"

Write-Host "Done. Reports under $outDir"
