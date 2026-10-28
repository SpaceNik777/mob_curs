$project = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$evidence = Join-Path $project 'mob_curs_docs\stage1\baseline'
if (-not $env:JAVA_HOME) {
    throw 'Set JAVA_HOME to a working JDK 21 before running this script.'
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

& java -version 2>&1 | Tee-Object -FilePath (Join-Path $evidence 'java-version.log')
& javac -version 2>&1 | Tee-Object -FilePath (Join-Path $evidence 'javac-version.log')
& "$project\gradlew.bat" --version --console=plain 2>&1 | Tee-Object -FilePath (Join-Path $evidence 'gradle-version.log')
$versionExitCode = $LASTEXITCODE

$started = [DateTimeOffset]::Now
$watch = [Diagnostics.Stopwatch]::StartNew()
& "$project\gradlew.bat" testDebugUnitTest --console=plain --stacktrace 2>&1 | Tee-Object -FilePath (Join-Path $evidence 'testDebugUnitTest.log')
$buildExitCode = $LASTEXITCODE
$watch.Stop()
$finished = [DateTimeOffset]::Now

$reports = @()
$reportRoot = Join-Path $project 'app\build\test-results\testDebugUnitTest'
if (Test-Path -LiteralPath $reportRoot) {
    foreach ($file in Get-ChildItem -LiteralPath $reportRoot -Filter 'TEST-*.xml' -File) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName -Raw
        $suite = $xml.testsuite
        $suite.SetAttribute('hostname', 'redacted')
        $xml.Save((Join-Path $evidence $file.Name))
        $reports += [ordered]@{
            file = $file.Name
            lastWriteTime = $file.LastWriteTime.ToString('o')
            name = [string]$suite.name
            tests = [int]$suite.tests
            failures = [int]$suite.failures
            errors = [int]$suite.errors
            skipped = [int]$suite.skipped
            time = [string]$suite.time
        }
    }
}
$summary = [ordered]@{
    project = '.'
    javaHome = 'JDK 21 (JAVA_HOME)'
    command = '.\gradlew.bat testDebugUnitTest --console=plain --stacktrace'
    gradleVersionExitCode = $versionExitCode
    started = $started.ToString('o')
    finished = $finished.ToString('o')
    elapsedSeconds = $watch.Elapsed.TotalSeconds
    buildExitCode = $buildExitCode
    reportCount = $reports.Count
    reports = $reports
}
$summary | ConvertTo-Json -Depth 8 | Tee-Object -FilePath (Join-Path $evidence 'test-summary.json')
exit $buildExitCode
