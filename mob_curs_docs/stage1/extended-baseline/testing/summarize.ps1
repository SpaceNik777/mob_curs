param([string]$PrivateSnapshot)
$ErrorActionPreference = 'Stop'
$culture = [Globalization.CultureInfo]::InvariantCulture
$cases = Import-Csv -LiteralPath (Join-Path $PSScriptRoot 'test-cases.csv') -Encoding UTF8
$tests = @($cases | Group-Object class,test | ForEach-Object {
    $rows = @($_.Group)
    $times = @($rows | ForEach-Object { [double]::Parse($_.seconds.Replace(',','.'), $culture) })
    $failed = @($rows | Where-Object status -eq 'failed').Count
    $passed = @($rows | Where-Object status -eq 'passed').Count
    [pscustomobject]@{class=$rows[0].class;test=$rows[0].test;executions=$rows.Count;failures=$failed;failureRate=$failed/$rows.Count;mixedPassFail=($failed -gt 0 -and $passed -gt 0);durationSeconds=$times;meanSeconds=($times | Measure-Object -Average).Average}
})
$coverageRuns = @(1..5 | ForEach-Object {
    $run = $_
    [xml]$xml = Get-Content -LiteralPath (Join-Path $PSScriptRoot "coverage-run-$run.xml") -Raw -Encoding UTF8
    $counters = @($xml.SelectNodes('/report/counter') | ForEach-Object {
        $missed = [int]$_.missed; $covered = [int]$_.covered
        [pscustomobject]@{type=$_.type;missed=$missed;covered=$covered;total=($missed+$covered);percent=[math]::Round(100*$covered/($missed+$covered),4)}
    })
    $packages = @($xml.SelectNodes('/report/package') | ForEach-Object {
        $name = $_.name
        foreach ($counter in $_.SelectNodes('counter[@type="LINE" or @type="BRANCH"]')) {
            [pscustomobject]@{package=$name;type=$counter.type;missed=[int]$counter.missed;covered=[int]$counter.covered}
        }
    })
    [pscustomobject]@{run=$run;classFilesInReport=$xml.SelectNodes('/report/package/class').Count;counters=$counters;packages=$packages}
})
[ordered]@{coverageRuns=$coverageRuns;tests=$tests;observedFlakyTests=@($tests | Where-Object mixedPassFail).Count;definition='Observed flaky test = same test has both pass and fail across the five fixed-order full-suite executions; failure rate is failures/executions, not a flake probability.'} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'summary.json') -Encoding UTF8
if ($PrivateSnapshot) {
    foreach ($run in 1..5) {
        Get-Content -LiteralPath (Join-Path $PrivateSnapshot "run-$run.log") -Encoding UTF8 | Where-Object { $_ -match '^> Task :[A-Za-z0-9:]+( (UP-TO-DATE|NO-SOURCE|SKIPPED))?$' } | Set-Content -LiteralPath (Join-Path $PSScriptRoot "tasks-run-$run.txt") -Encoding UTF8
    }
}
$coverageRuns[0].counters | Format-Table
$tests | Format-Table test,failures,mixedPassFail,meanSeconds
