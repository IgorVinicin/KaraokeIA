param([string]$Java = "$PSScriptRoot/../../../.tools/jdk-17.0.20.1+1/bin/java.exe")
$ErrorActionPreference = 'Stop'
$cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
$jars = Get-ChildItem $cache -Recurse -Filter '*.jar' | Where-Object {
    $_.FullName -match '(kotlin-compiler-embeddable\\1\.9\.23|kotlin-stdlib\\1\.9\.23|kotlin-script-runtime\\1\.9\.23|kotlin-reflect\\1\.6\.10|trove4j|org.jetbrains\\annotations)'
}
$stdlib = $jars | Where-Object { $_.Name -eq 'kotlin-stdlib-1.9.23.jar' } | Select-Object -First 1
if (!$stdlib) { throw 'Run the Gradle build first to populate Kotlin compiler dependencies.' }
$classes = Join-Path $env:TEMP ('karaoke-queue-regression-' + [guid]::NewGuid().ToString('N'))
$repository = Join-Path $PSScriptRoot '../../../app/src/main/java/com/araujo/karaoke_ia/data/QueueRepository.kt'
& $Java -cp ($jars.FullName -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $stdlib.FullName -d $classes "$PSScriptRoot/Android.kt" "$PSScriptRoot/Probe.kt" $repository
if ($LASTEXITCODE -ne 0) { throw 'Queue regression compilation failed.' }
& $Java -cp ($classes + ';' + $stdlib.FullName) ProbeKt
if ($LASTEXITCODE -ne 0) { throw 'Queue regression failed.' }
