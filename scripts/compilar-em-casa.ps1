param([switch]$Clean, [switch]$Release)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdkCandidates = @(
    (Join-Path $projectRoot '.tools/jdk-17.0.20.1+1'),
    $env:JAVA_HOME17
)
$jdkPath = $null
foreach ($candidate in $jdkCandidates) {
    if (-not $candidate) { continue }
    $java = Join-Path $candidate 'bin/java.exe'
    $javac = Join-Path $candidate 'bin/javac.exe'
    $jdkReleaseMetadata = Join-Path $candidate 'release'
    if ((Test-Path -LiteralPath $java) -and (Test-Path -LiteralPath $javac) -and
        (Test-Path -LiteralPath $jdkReleaseMetadata) -and
        ((Get-Content -LiteralPath $jdkReleaseMetadata -Raw) -match '(?m)^JAVA_VERSION="17(?:\.|\")')) {
        $jdkPath = (Resolve-Path -LiteralPath $candidate).Path
        break
    }
}
if (-not $jdkPath) {
    throw 'JDK 17 ausente. Instale um JDK 17 em .tools/jdk-17.0.20.1+1 ou defina JAVA_HOME17 para a pasta dele.'
}

$sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
if ($env:LOCALAPPDATA) { $sdkCandidates += Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$sdkPath = $null
foreach ($candidate in $sdkCandidates) {
    if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'platforms'))) {
        $sdkPath = (Resolve-Path -LiteralPath $candidate).Path
        break
    }
}
if (-not $sdkPath) {
    throw 'Android SDK ausente. Configure ANDROID_HOME ou instale o SDK pelo Android Studio em LOCALAPPDATA/Android/Sdk.'
}
if (-not $env:USERPROFILE) { throw 'USERPROFILE ausente: nao foi possivel localizar o cache pessoal do Gradle.' }

$propertiesPath = Join-Path $projectRoot 'local.properties'
$properties = @()
if (Test-Path -LiteralPath $propertiesPath) {
    $properties = @(Get-Content -LiteralPath $propertiesPath | Where-Object { $_ -notmatch '^\s*sdk\.dir\s*=' })
}
$escapedSdk = $sdkPath.Replace('\', '/').Replace(':', '\:')
$properties += "sdk.dir=$escapedSdk"
[System.IO.File]::WriteAllLines($propertiesPath, [string[]]$properties, [System.Text.UTF8Encoding]::new($false))

$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = $jdkPath
    $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
    Push-Location $projectRoot
    try {
        $tasks = @('assembleDebug', 'lintDebug', 'testDebugUnitTest')
        if ($Release) { $tasks = @('assembleRelease', 'lintRelease') }
        if ($Clean) { $tasks = @('clean') + $tasks }
        Write-Host "JDK: $jdkPath"
        Write-Host "Android SDK: $sdkPath"
        & .\gradlew.bat @tasks --max-workers=1 --no-daemon `
            '-Pkotlin.compiler.execution.strategy=in-process' `
            '-Pkotlin.incremental=false' `
            '-Porg.gradle.java.installations.auto-detect=false' `
            '-Porg.gradle.java.installations.auto-download=false' `
            "-Porg.gradle.java.installations.paths=$jdkPath"
        if ($LASTEXITCODE -ne 0) { throw "Compilacao ou verificacoes falharam (codigo $LASTEXITCODE)." }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleHome
}
