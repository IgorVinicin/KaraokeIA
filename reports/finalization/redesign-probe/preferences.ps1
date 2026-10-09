param([ValidateSet('Backup','Restore')] [string]$Action)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$qaAdb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$baselineDir=Join-Path $PSScriptRoot 'baseline'
$knownPreferences=@('user_scores_prefs.xml','online_queue.xml','online_library.xml','favorites_prefs.xml','history_prefs.xml','queue_prefs.xml','storage_prefs.xml')
& $qaAdb -e shell am force-stop com.araujo.karaoke_ia | Out-Null
if($LASTEXITCODE -ne 0){throw 'Could not stop app before preference operation'}
if($Action -eq 'Backup') {
    if(Test-Path -LiteralPath $baselineDir){throw 'Baseline already exists; restore before another test'}
    New-Item -ItemType Directory -Path $baselineDir | Out-Null
    $present=& $qaAdb -e shell run-as com.araujo.karaoke_ia ls shared_prefs
    if($LASTEXITCODE -ne 0){throw 'Could not list preferences; baseline is incomplete'}
    $present=@($present | ForEach-Object { $_.Trim() } | Where-Object { $_ -in $knownPreferences })
    foreach($name in $knownPreferences) {
        if($present -contains $name){
            $raw=(& $qaAdb -e shell run-as com.araujo.karaoke_ia cat ('shared_prefs/'+$name)) -join "`n"
            if($LASTEXITCODE -ne 0){throw ('Read failed: '+$name)}
            [xml]$verified=$raw
            [IO.File]::WriteAllText((Join-Path $baselineDir $name),$raw,[Text.UTF8Encoding]::new($false))
        }
    }
    ConvertTo-Json -InputObject $present -Compress | Set-Content -LiteralPath (Join-Path $baselineDir 'present.json') -Encoding utf8
    Write-Output 'User preferences preserved before QA.'
} else {
    if(-not (Test-Path -LiteralPath (Join-Path $baselineDir 'present.json'))){throw 'Missing baseline'}
    $manifest=Get-Content -LiteralPath (Join-Path $baselineDir 'present.json') -Raw -Encoding utf8 | ConvertFrom-Json
    $preserved=@(foreach($item in $manifest){$item})
    foreach($name in $preserved) {
        if($name -notin $knownPreferences){throw ('Unknown baseline preference: '+$name)}
        if(-not (Test-Path -LiteralPath (Join-Path $baselineDir $name))){throw ('Incomplete baseline: '+$name)}
        [xml]$verified=Get-Content -LiteralPath (Join-Path $baselineDir $name) -Raw -Encoding utf8
    }
    $OutputEncoding=[Text.UTF8Encoding]::new($false)
    foreach($name in $knownPreferences) {
        $snapshot=Join-Path $baselineDir $name
        if(Test-Path -LiteralPath $snapshot){
            $raw=Get-Content -LiteralPath $snapshot -Raw -Encoding utf8
            $raw | & $qaAdb -e shell ("run-as com.araujo.karaoke_ia sh -c 'cat > shared_prefs/"+$name+".qa-tmp && mv shared_prefs/"+$name+".qa-tmp shared_prefs/"+$name+"'")
            if($LASTEXITCODE -ne 0){throw ('Restore failed: '+$name)}
            & python (Join-Path $PSScriptRoot 'verify-preference.py') $qaAdb $snapshot $name
            if($LASTEXITCODE -ne 0){throw ('Restore byte verification failed: '+$name)}
        } else {
            & $qaAdb -e shell run-as com.araujo.karaoke_ia rm -f ('shared_prefs/'+$name)
            if($LASTEXITCODE -ne 0){throw ('Remove QA-only preference failed: '+$name)}
        }
    }
    foreach($name in $knownPreferences) {
        $snapshot=Join-Path $baselineDir $name
        if(Test-Path -LiteralPath $snapshot){Remove-Item -LiteralPath $snapshot}
    }
    Remove-Item -LiteralPath (Join-Path $baselineDir 'present.json')
    Remove-Item -LiteralPath $baselineDir
    Write-Output 'Original preferences restored and verified; temporary snapshots removed.'
}
