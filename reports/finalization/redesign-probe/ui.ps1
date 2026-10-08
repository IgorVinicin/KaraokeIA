param([ValidateSet('Capture','Tap','Key')] [string]$Action='Capture', [string]$Name='current', [int]$Count=1)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$qaAdb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$qaRoot=Split-Path $PSScriptRoot -Parent
function Read-Ui {
    & $qaAdb -e shell uiautomator dump /sdcard/redesign-ui.xml | Out-Null
    if($LASTEXITCODE -ne 0){throw 'UI dump failed'}
    $raw=(& $qaAdb -e shell cat /sdcard/redesign-ui.xml) -join "`n"
    return [xml]$raw
}
if($Action -eq 'Key') {
    1..$Count | ForEach-Object { & $qaAdb -e shell input keyevent $Name | Out-Null }
    exit $LASTEXITCODE
}
$doc=Read-Ui
if($Action -eq 'Tap') {
    $node=$doc.SelectNodes('//node') | Where-Object { $_.text -eq $Name -or $_.'content-desc' -eq $Name } | Select-Object -First 1
    if(-not $node){throw ('Missing UI: '+$Name)}
    $numbers=[regex]::Matches($node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    & $qaAdb -e shell input tap ([int](($numbers[0]+$numbers[2])/2)) ([int](($numbers[1]+$numbers[3])/2))
} else {
    $xmlPath=Join-Path $qaRoot ('redesign-'+$Name+'.xml')
    [IO.File]::WriteAllText($xmlPath,$doc.OuterXml,[Text.UTF8Encoding]::new($false))
    & $qaAdb -e shell screencap -p /sdcard/redesign-screen.png | Out-Null
    $ErrorActionPreference='Continue'
    & $qaAdb -e pull /sdcard/redesign-screen.png (Join-Path $qaRoot ('redesign-'+$Name+'.png')) 2>&1 | Out-Null
    $ErrorActionPreference='Stop'
    if($LASTEXITCODE -ne 0){throw 'Screenshot pull failed'}
    $doc.SelectNodes('//node') | Where-Object { $_.text -ne '' -or $_.'content-desc' -ne '' } |
        Select-Object text,content-desc,bounds,focused | Format-Table -AutoSize
}
