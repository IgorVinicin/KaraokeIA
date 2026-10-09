param([switch]$Clean, [switch]$VerifyOnly)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $VerifyOnly -and -not (Test-Path -LiteralPath (Join-Path $projectRoot 'signing.properties'))) {
    throw 'Configure signing.properties na raiz antes de gerar a release assinada.'
}
if (-not $VerifyOnly) { & (Join-Path $PSScriptRoot 'compilar-em-casa.ps1') -Release -Clean:$Clean }

$sdkLine = Get-Content -LiteralPath (Join-Path $projectRoot 'local.properties') |
    Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -Last 1
$sdkPath = ($sdkLine -replace '^sdk\.dir=', '').Replace('\:', ':').Replace('\\', '\')
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkPath 'build-tools') -Directory |
    Where-Object { (Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat')) -and
        (Test-Path -LiteralPath (Join-Path $_.FullName 'aapt.exe')) } |
    Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending |
    Select-Object -First 1
if (-not $buildTools) { throw 'Build-tools com apksigner e aapt ausentes no Android SDK.' }
$apkPath = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
if (-not (Test-Path -LiteralPath $apkPath)) { throw 'APK release assinado nao foi gerado.' }

# apksigner uses Java, so use the same JDK 17 validated by the build helper.
$previousJavaHome = $env:JAVA_HOME
try {
    $localJdk = Join-Path $projectRoot '.tools/jdk-17.0.20.1+1'
    if (Test-Path -LiteralPath (Join-Path $localJdk 'bin/java.exe')) { $env:JAVA_HOME = $localJdk }
    else { $env:JAVA_HOME = $env:JAVA_HOME17 }
    & (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $apkPath
    if ($LASTEXITCODE -ne 0) { throw 'Verificacao da assinatura do APK falhou.' }
} finally { $env:JAVA_HOME = $previousJavaHome }
$badging = & (Join-Path $buildTools.FullName 'aapt.exe') dump badging $apkPath
if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel inspecionar o manifesto do APK.' }
if ($badging -match 'application-debuggable') { throw 'APK debuggable rejeitado para release.' }
if (-not ($badging -match "package: name='com.araujo.karaoke_ia'")) { throw 'Pacote Android inesperado.' }
& python (Join-Path $PSScriptRoot 'verificar-segredos-apk.py') $apkPath
if ($LASTEXITCODE -ne 0) { throw 'Verificacao de arquivos privados no APK falhou.' }
$distPath = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $distPath -Force | Out-Null
$outputPath = Join-Path $distPath 'SoltaVoz-release.apk'
Copy-Item -LiteralPath $apkPath -Destination $outputPath -Force
$mappingPath = Join-Path $projectRoot 'app/build/outputs/mapping/release/mapping.txt'
if (Test-Path -LiteralPath $mappingPath) {
    Copy-Item -LiteralPath $mappingPath -Destination (Join-Path $distPath 'mapping.txt') -Force
}
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/TESTAR_NA_TVBOX.md') -Destination (Join-Path $distPath 'LEIA-ME.md') -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/producao.md') -Destination (Join-Path $distPath 'producao.md') -Force
$hash = (Get-FileHash -LiteralPath $outputPath -Algorithm SHA256).Hash
Set-Content -LiteralPath (Join-Path $distPath 'SoltaVoz-release.sha256') -Value "$hash  SoltaVoz-release.apk" -Encoding ASCII
Write-Host "Release assinada e verificada: $outputPath"
Write-Host "SHA256: $hash"
