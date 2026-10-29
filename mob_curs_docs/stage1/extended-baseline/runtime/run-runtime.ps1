param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidHome = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }),
    [string]$Serial = 'emulator-5554',
    [int]$Runs = 3
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $JavaHome)) { throw 'Supply a JDK 21 installation using -JavaHome.' }
$adb = Join-Path $AndroidHome 'platform-tools/adb.exe'
if (!(Test-Path -LiteralPath $adb)) { throw 'Android platform-tools required.' }
$parent = Join-Path ([IO.Path]::GetTempPath()) 'kilo'
if (!(Test-Path -LiteralPath $parent)) { throw 'Approved kilo temporary parent must exist.' }
$runtime = Join-Path $parent ('baseline-runtime-' + [guid]::NewGuid().ToString('N'))
$output = Join-Path $PSScriptRoot 'results'
[IO.Directory]::CreateDirectory($output) | Out-Null
function Save-Sanitized([string]$Name, [object]$Data) {
    $text = ($Data | Out-String -Width 4096)
    foreach ($p in @($runtime, $ProjectRoot, $JavaHome, $AndroidHome, $env:USERPROFILE, $env:COMPUTERNAME)) {
        if ($p) { $text = $text.Replace($p, '<local>').Replace($p.Replace('\', '/'), '<local>') }
    }
    [IO.File]::WriteAllText((Join-Path $output $Name), $text, [Text.UTF8Encoding]::new($false))
}
& robocopy $ProjectRoot $runtime /E /XD .git .gradle .idea .kotlin build mob_curs_docs /XF local.properties /NFL /NDL /NJH /NJS | Out-Null
if ($LASTEXITCODE -ge 8) { throw 'Source copy failed.' }
$manifest = @()
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'app/src/main') -File -Recurse) {
    $relative = $file.FullName.Substring($ProjectRoot.Length + 1)
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    $copied = (Get-FileHash -LiteralPath (Join-Path $runtime $relative) -Algorithm SHA256).Hash
    if ($hash -ne $copied) { throw "Production source mismatch: $relative" }
    $manifest += [ordered]@{ path = $relative.Replace('\', '/'); sha256 = $hash }
}
Save-Sanitized 'production-source-sha256.json' (ConvertTo-Json -InputObject $manifest -Depth 5)
$build = Join-Path $runtime 'app/build.gradle.kts'
$config = [IO.File]::ReadAllText($build).Replace('applicationId = "ru.course.cityservices"', 'applicationId = "ru.course.cityservices.baseline"' + "`n        testInstrumentationRunner = `"androidx.test.runner.AndroidJUnitRunner`"")
$config = $config.Replace('dependencies {', "dependencies {`n    androidTestImplementation(`"androidx.test:runner:1.7.0`")`n    androidTestImplementation(`"androidx.test.ext:junit:1.3.0`")`n    androidTestImplementation(`"androidx.test.uiautomator:uiautomator:2.3.0`")")
[IO.File]::WriteAllText($build, $config)
$testDir = Join-Path $runtime 'app/src/androidTest/kotlin/ru/course/cityservices/baselinefixture'
[IO.Directory]::CreateDirectory($testDir) | Out-Null
[IO.File]::Copy((Join-Path $PSScriptRoot 'BaselineRuntimeTest.kt'), (Join-Path $testDir 'BaselineRuntimeTest.kt'))
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidHome
Push-Location $runtime
try {
    $buildLog = & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain 2>&1
    Save-Sanitized 'build.txt' $buildLog
    if ($LASTEXITCODE -ne 0) { throw 'Build failed; see sanitized build.txt.' }
} finally { Pop-Location }
$install = & $adb -s $Serial install -r (Join-Path $runtime 'app/build/outputs/apk/debug/app-debug.apk') 2>&1
$install += & $adb -s $Serial install -r (Join-Path $runtime 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk') 2>&1
Save-Sanitized 'install.txt' $install
if ($LASTEXITCODE -ne 0) { throw 'Baseline install failed.' }
for ($i = 1; $i -le $Runs; $i++) {
    $log = & $adb -s $Serial shell am instrument -w -r ru.course.cityservices.baseline.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    Save-Sanitized "instrumentation-$i.txt" $log
    Save-Sanitized "metrics-$i.json" (& $adb -s $Serial exec-out run-as ru.course.cityservices.baseline cat files/baseline-metrics.json)
    Save-Sanitized "gfxinfo-scroll-$i.txt" (& $adb -s $Serial exec-out run-as ru.course.cityservices.baseline cat files/gfxinfo-scroll.txt)
    if (($log | Out-String) -notmatch 'OK \(1 test\)') { throw "Instrumentation run $i failed." }
}
# All clears/force-stops target only the isolated baseline package.
& $adb -s $Serial shell pm clear ru.course.cityservices.baseline | Out-Null
$launches = @()
for ($i = 1; $i -le 5; $i++) {
    & $adb -s $Serial shell am force-stop ru.course.cityservices.baseline | Out-Null
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $startLog = & $adb -s $Serial shell am start -W -n ru.course.cityservices.baseline/ru.course.cityservices.MainActivity
    $activityMs = $watch.Elapsed.TotalMilliseconds
    $found = $false
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        & $adb -s $Serial shell uiautomator dump /data/local/tmp/baseline-window.xml 2>&1 | Out-Null
        $xml = & $adb -s $Serial shell cat /data/local/tmp/baseline-window.xml
        if (($xml | Out-String).Contains('ru.course.cityservices.baseline') -and ($xml | Out-String).Contains('RUT')) { $found = $true; break }
    }
    $watch.Stop()
    $launches += [ordered]@{ run = $i; activityCommandMs = $activityMs; visibleLoginObservedMs = $watch.Elapsed.TotalMilliseconds; ready = $found }
    Save-Sanitized "cold-launch-$i.txt" $startLog
    if (!$found) { throw 'Cold launch login UI readiness selector not found.' }
}
Save-Sanitized 'cold-launch-readiness.json' (ConvertTo-Json -InputObject $launches -Depth 5)
Write-Output 'Baseline runtime measurements captured. Production package and source were not modified.'
