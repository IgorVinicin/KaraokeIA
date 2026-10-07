param([string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$qaAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$qaOutput = $PSScriptRoot
function Send-KaraokeKey([int[]]$Keys) {
    foreach ($qaKey in $Keys) {
        & $qaAdb -s $Serial shell input keyevent $qaKey
        Start-Sleep -Milliseconds 300
    }
    Start-Sleep -Milliseconds 500
}
function Save-KaraokeScreen([string]$Name) {
    & $qaAdb -s $Serial shell uiautomator dump /sdcard/karaoke-qa.xml | Out-Null
    & $qaAdb -s $Serial pull /sdcard/karaoke-qa.xml (Join-Path $qaOutput "$Name.xml") | Out-Null
    & $qaAdb -s $Serial shell screencap -p /sdcard/karaoke-qa.png
    & $qaAdb -s $Serial pull /sdcard/karaoke-qa.png (Join-Path $qaOutput "$Name.png") | Out-Null
    [xml]$qaTree = Get-Content (Join-Path $qaOutput "$Name.xml") -Encoding UTF8
    $qaTree.SelectNodes('//node[@text!="" or @focused="true"]') | ForEach-Object {
        [pscustomobject]@{Text=$_.text; Description=$_.'content-desc'; Focused=$_.focused; Bounds=$_.bounds}
    }
}
function Save-KaraokeDiagnostics([string]$Name) {
    & $qaAdb -s $Serial shell dumpsys meminfo com.araujo.karaoke_ia | Out-File (Join-Path $qaOutput "$Name-memory.txt") -Encoding utf8
    & $qaAdb -s $Serial logcat -d -b crash | Out-File (Join-Path $qaOutput "$Name-crash.txt") -Encoding utf8
    & $qaAdb -s $Serial logcat -d -s AndroidRuntime:E ExoPlayerImpl:E MediaCodec:E | Out-File (Join-Path $qaOutput "$Name-player-log.txt") -Encoding utf8
}
