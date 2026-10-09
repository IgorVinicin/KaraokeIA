param([string]$Java = "$PSScriptRoot/../../../.tools/jdk-17.0.20.1+1/bin/java.exe")
$ErrorActionPreference = 'Stop'
$cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
$jars = Get-ChildItem $cache -Recurse -Filter '*.jar' | Where-Object {
    $_.FullName -match '(kotlin-compiler-embeddable\\1\.9\.23|kotlin-stdlib\\1\.9\.23|kotlin-script-runtime\\1\.9\.23|kotlin-reflect\\1\.6\.10|trove4j|org.jetbrains\\annotations)'
}
$stdlib = $jars | Where-Object { $_.Name -eq 'kotlin-stdlib-1.9.23.jar' } | Select-Object -First 1
if (!$stdlib) { throw 'Run the Gradle build first to populate Kotlin compiler dependencies.' }
$probeDir = Join-Path $env:TEMP ('karaoke-youtube-regression-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $probeDir | Out-Null
$repository = Get-Content -LiteralPath "$PSScriptRoot/../../../app/src/main/java/com/araujo/karaoke_ia/online/OnlineRepository.kt" -Raw -Encoding UTF8
$start = $repository.IndexOf('internal fun normalizeYouTubeKey(')
$end = $repository.IndexOf('class OnlineRepository(', $start)
if ($start -lt 0 -or $end -lt 0) { throw 'Cannot locate the actual key normalization helper.' }
# Extract the actual Android-independent helper, without copying its implementation.
Set-Content -LiteralPath "$probeDir/Normalize.kt" -Encoding UTF8 -Value $repository.Substring($start, $end - $start)
& $Java -cp ($jars.FullName -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $stdlib.FullName -d "$probeDir/classes" "$probeDir/Normalize.kt" "$PSScriptRoot/Probe.kt"
if ($LASTEXITCODE -ne 0) { throw 'Configuration regression compilation failed.' }
& $Java -cp ("$probeDir/classes;" + $stdlib.FullName) ProbeKt
if ($LASTEXITCODE -ne 0) { throw 'Configuration regression failed.' }
