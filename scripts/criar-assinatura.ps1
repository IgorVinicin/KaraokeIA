param([string]$JavaHome17)
$ErrorActionPreference = 'Stop'
$signingRoot = Split-Path $PSScriptRoot -Parent
if (-not $JavaHome17) { $JavaHome17 = Join-Path $signingRoot '.tools/jdk-17.0.20.1+1' }
$keytool = Join-Path $JavaHome17 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw 'Informe um JDK 17 com keytool.' }
$settingsPath = Join-Path $signingRoot 'signing.properties'
$keysDir = Join-Path $signingRoot '.signing'
$keyPath = Join-Path $keysDir 'karaoke-release.jks'
if ((Test-Path -LiteralPath $settingsPath) -or (Test-Path -LiteralPath $keyPath)) {
    throw 'Assinatura existente: reutilize a chave original para preservar atualizacoes.'
}
New-Item -ItemType Directory -Path $keysDir -Force | Out-Null
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $bytes = New-Object byte[] 32
    $random.GetBytes($bytes)
    $env:KARAOKE_SIGNING_STORE_PASS = [Convert]::ToBase64String($bytes)
    $random.GetBytes($bytes)
    $env:KARAOKE_SIGNING_KEY_PASS = [Convert]::ToBase64String($bytes)
    $ErrorActionPreference = 'Continue'
    & $keytool -genkeypair -noprompt -storetype JKS -keystore $keyPath `
        -alias karaokeia -keyalg RSA -keysize 4096 -sigalg SHA256withRSA `
        -validity 10000 -dname 'CN=KaraokeIA Release,O=KaraokeIA' `
        -storepass:env KARAOKE_SIGNING_STORE_PASS -keypass:env KARAOKE_SIGNING_KEY_PASS
    $keytoolExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($keytoolExit -ne 0) { throw 'Nao foi possivel gerar a assinatura.' }
    $settings = @(
        'storeFile=.signing/karaoke-release.jks',
        ('storePassword=' + $env:KARAOKE_SIGNING_STORE_PASS),
        'keyAlias=karaokeia',
        ('keyPassword=' + $env:KARAOKE_SIGNING_KEY_PASS)
    )
    [IO.File]::WriteAllLines($settingsPath, $settings, [Text.UTF8Encoding]::new($false))
    Write-Host 'Assinatura de release criada. Guarde .signing e signing.properties em backup privado; nao publique esses arquivos.'
} finally {
    $random.Dispose()
    Remove-Item Env:KARAOKE_SIGNING_STORE_PASS -ErrorAction SilentlyContinue
    Remove-Item Env:KARAOKE_SIGNING_KEY_PASS -ErrorAction SilentlyContinue
}
