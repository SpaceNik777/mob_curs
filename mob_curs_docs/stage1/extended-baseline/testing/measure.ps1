param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$JavaHome,
    [Parameter(Mandatory=$true)][string]$AndroidSdk,
    [string]$TempRoot = (Join-Path $env:TEMP 'kilo'),
    [int]$Repetitions = 5
)
$ErrorActionPreference = 'Stop'
if ($Repetitions -lt 5) { throw 'At least five repetitions are required.' }
if (!(Test-Path -LiteralPath $TempRoot)) { throw 'Temporary parent must already exist.' }
$out = $PSScriptRoot
$snapshot = Join-Path $TempRoot ('baseline-tests-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $snapshot | Out-Null
$archive = Join-Path $snapshot 'baseline.zip'
& git -C $Repository archive --format=zip --output=$archive HEAD
if ($LASTEXITCODE -ne 0) { throw 'git archive failed' }
Expand-Archive -LiteralPath $archive -DestinationPath $snapshot
$revision = (& git -C $Repository rev-parse HEAD).Trim()
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_SDK_ROOT = $AndroidSdk
$env:PATH = (Join-Path $JavaHome 'bin') + ';' + $env:PATH
$init = Join-Path $out 'coverage.init.gradle'
$runs = @()
$cases = @()
Push-Location $snapshot
try {
    for ($run = 1; $run -le $Repetitions; $run++) {
        $clock = [Diagnostics.Stopwatch]::StartNew()
        $ErrorActionPreference = 'Continue'
        $log = & .\gradlew.bat --console=plain --no-build-cache -I $init :app:baselineCoverage 2>&1
        $exit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        $clock.Stop()
        # Raw output stays private in the isolated temporary snapshot.
        $log | Set-Content -LiteralPath (Join-Path $snapshot "run-$run.log") -Encoding UTF8
        if ($exit -ne 0) { throw "Gradle failed in repetition $run; private log in temporary snapshot: $snapshot" }
        $files = Get-ChildItem -LiteralPath (Join-Path $snapshot 'app/build/test-results/testDebugUnitTest') -Filter 'TEST-*.xml'
        if (!$files) { throw 'No test results produced.' }
        $total = 0; $failed = 0; $skipped = 0; $seconds = 0.0
        foreach ($file in $files) {
            [xml]$suite = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
            foreach ($case in $suite.testsuite.testcase) {
                $status = if ($case.failure -or $case.error) { 'failed' } elseif ($case.skipped) { 'skipped' } else { 'passed' }
                $duration = [double]::Parse($case.time, [Globalization.CultureInfo]::InvariantCulture)
                $cases += [pscustomobject]@{run=$run;class=$case.classname;test=$case.name;status=$status;seconds=$duration;failureType=($case.failure.type -join ';')}
                $total++; $seconds += $duration
                if ($status -eq 'failed') { $failed++ }
                if ($status -eq 'skipped') { $skipped++ }
            }
        }
        $executed = @($log | Where-Object { "$_" -match '^> Task :app:testDebugUnitTest\s*$' }).Count -gt 0
        if (!$executed) { throw 'Test task execution was not confirmed.' }
        $log | ForEach-Object { "$_" } | Where-Object { $_ -match '^> Task :[A-Za-z0-9:]+( (UP-TO-DATE|NO-SOURCE|SKIPPED))?$' } | Set-Content -LiteralPath (Join-Path $out "tasks-run-$run.txt") -Encoding UTF8
        $runs += [pscustomobject]@{run=$run;tests=$total;failures=$failed;skipped=$skipped;testSeconds=$seconds;gradleWallSeconds=[math]::Round($clock.Elapsed.TotalSeconds,3);testTaskExecuted=$executed;gradleExit=$exit}
        [xml]$coverage = Get-Content -LiteralPath (Join-Path $snapshot 'app/build/reports/jacoco/baseline.xml') -Raw
        foreach ($session in @($coverage.SelectNodes('/report/sessioninfo'))) { [void]$session.ParentNode.RemoveChild($session) }
        # Remove the external DTD and session IDs; keep counters and relative source names.
        foreach ($node in @($coverage.ChildNodes)) { if ($node.NodeType -eq 'DocumentType') { [void]$coverage.RemoveChild($node) } }
        $coverage.Save((Join-Path $out "coverage-run-$run.xml"))
        Write-Output "Run $run : $total tests, $failed failed; test seconds $seconds; wall seconds $($clock.Elapsed.TotalSeconds)"
    }
    $runs | Export-Csv -LiteralPath (Join-Path $out 'runs.csv') -NoTypeInformation -Encoding UTF8
    $cases | Export-Csv -LiteralPath (Join-Path $out 'test-cases.csv') -NoTypeInformation -Encoding UTF8
    $ErrorActionPreference = 'Continue'
    $javaVersion = @(& java -version 2>&1 | ForEach-Object { "$_" })
    $ErrorActionPreference = 'Stop'
    $metadata = [ordered]@{revision=$revision;repetitions=$Repetitions;jacoco='0.8.14';java=$javaVersion;scope='All debug compileDebugKotlin class files, including Room generated classes; no package exclusions';snapshot='git archive HEAD';command='gradlew.bat --console=plain --no-build-cache -I <evidence>/coverage.init.gradle :app:baselineCoverage'}
    $metadata | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $out 'metadata.json') -Encoding UTF8
} finally { Pop-Location }
