# TabletCAD local test runner (Windows PowerShell 5.1+).
# Executes ONLY whitelisted build/ADB/Git actions requested through job files in
# .debug\runner\queue and writes results to .debug\runner\results.
# Start manually:  powershell -NoProfile -ExecutionPolicy Bypass -File tools\TabletCAD-TestRunner.ps1
# Stop: Ctrl+C or create .debug\runner\STOP
param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr',
    [int]$PollSeconds = 3
)
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$OutputEncoding = [Text.Encoding]::UTF8
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Base = Join-Path $Root '.debug\runner'
$Queue = Join-Path $Base 'queue'
$Results = Join-Path $Base 'results'
New-Item -ItemType Directory -Force -Path $Queue, $Results | Out-Null
$Adb = Join-Path $Sdk 'platform-tools\adb.exe'
$Git = (Get-Command git -ErrorAction SilentlyContinue).Source
$Pkg = 'pl.tabletcad.app'

function Num([string]$v) { if ($v -notmatch '^-?\d{1,5}$') { throw "Invalid number: $v" }; return $v }
function Run-Ps([string]$script, [string[]]$extra) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script @extra 2>&1 | ForEach-Object { "$_" }
    $script:Code = $LASTEXITCODE
}
function Invoke-Job([string]$id, [string[]]$lines, [string]$log) {
    $action = $lines[0].Trim()
    $a = @($lines | Select-Object -Skip 1 | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })
    $script:Code = 0
    switch ($action) {
        'ping'      { "runner alive $(Get-Date -Format o)"; "root=$Root"; "adb=$Adb exists=$(Test-Path $Adb)"; "git=$Git" }
        'devices'   { & $Adb devices -l; $script:Code = $LASTEXITCODE
                      foreach ($p in 'ro.product.model','ro.product.manufacturer','ro.build.version.release','ro.build.version.sdk','ro.product.cpu.abi') { "$p=" + (& $Adb shell getprop $p) }
                      & $Adb shell wm size; & $Adb shell wm density
                      & $Adb shell dumpsys package $Pkg | Select-String 'versionName|versionCode|firstInstallTime|lastUpdateTime' }
        'build'     { Run-Ps (Join-Path $Root 'tools\build-foundation-apk.ps1') @('-Sdk', $Sdk, '-Jdk', $Jdk) }
        'install'   { $apk = Get-ChildItem (Join-Path $Root 'dist') -Filter 'TabletCAD-*.apk' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
                      if (-not $apk) { 'no APK'; $script:Code = 2 } else { "installing $($apk.FullName)"; & $Adb install -r $apk.FullName 2>&1 | ForEach-Object { "$_" }; $script:Code = $LASTEXITCODE } }
        'launch'    { & $Adb shell am force-stop $Pkg; & $Adb shell am start -W -n "$Pkg/.MainActivity" 2>&1 | ForEach-Object { "$_" }; $script:Code = $LASTEXITCODE }
        'stop'      { & $Adb shell am force-stop $Pkg; $script:Code = $LASTEXITCODE }
        'awake'     { # keeps the screen on while USB-powered so long instrumented runs and screenshots are not interrupted
                      & $Adb shell svc power stayon usb; & $Adb shell input keyevent 224; & $Adb shell wm dismiss-keyguard; $script:Code = $LASTEXITCODE }
        'awake-off' { & $Adb shell svc power stayon false; $script:Code = $LASTEXITCODE }
        'uitests'   { Run-Ps (Join-Path $Root 'tools\build-ui-tests.ps1') @('-Sdk', $Sdk, '-Jdk', $Jdk)
                      if ($script:Code -eq 0) {
                          & $Adb shell svc power stayon usb; & $Adb shell input keyevent 224; & $Adb shell wm dismiss-keyguard
                          & $Adb install -r (Join-Path $Root 'build\ui-tests\TabletCAD-ui-tests.apk') 2>&1 | ForEach-Object { "$_" }
                          $out = @(& $Adb shell am instrument -w -r 'pl.tabletcad.test/pl.tabletcad.test.WorkspaceInstrumentation' 2>&1 | ForEach-Object { "$_" })
                          $out
                          & $Adb shell svc power stayon false
                          if (-not ($out -match 'result=PASS')) { $script:Code = 3 } } }
        'logcat'    { $n = if ($a.Count) { Num $a[0] } else { '3000' }; & $Adb logcat -d -t $n 2>&1 | ForEach-Object { "$_" } }
        'logcat-clear' { & $Adb logcat -c; $script:Code = $LASTEXITCODE }
        'crash'     { & $Adb logcat -d -b crash 2>&1 | ForEach-Object { "$_" } }
        'screenshot'{ $png = Join-Path $Results "$id.png"
                      & $Adb shell screencap -p /sdcard/tabletcad_runner.png; & $Adb pull /sdcard/tabletcad_runner.png $png 2>&1 | ForEach-Object { "$_" }
                      & $Adb shell rm /sdcard/tabletcad_runner.png; $script:Code = [int](-not (Test-Path $png)); "png=$png" }
        'uidump'    { & $Adb shell uiautomator dump /sdcard/tabletcad_ui.xml 2>&1 | Out-Null; & $Adb shell cat /sdcard/tabletcad_ui.xml }
        'tap'       { & $Adb shell input tap (Num $a[0]) (Num $a[1]); $script:Code = $LASTEXITCODE }
        'swipe'     { & $Adb shell input swipe (Num $a[0]) (Num $a[1]) (Num $a[2]) (Num $a[3]) (Num $a[4]); $script:Code = $LASTEXITCODE }
        'key'       { & $Adb shell input keyevent (Num $a[0]); $script:Code = $LASTEXITCODE }
        'text'      { if ($a[0] -notmatch '^[A-Za-z0-9.,\-]{1,40}$') { throw 'text not allowed' }; & $Adb shell input text $a[0]; $script:Code = $LASTEXITCODE }
        'git-status'{ Push-Location $Root; & $Git status -sb 2>&1 | ForEach-Object { "$_" }; & $Git log --oneline -5; Pop-Location }
        'git-branch'{ if ($a[0] -notmatch '^codex/[A-Za-z0-9._\-]{1,60}$') { throw 'invalid branch' }
                      Push-Location $Root; & $Git switch -c $a[0] 2>&1 | ForEach-Object { "$_" }; $script:Code = $LASTEXITCODE; Pop-Location }
        'git-switch'{ if ($a[0] -notmatch '^[A-Za-z0-9._/\-]{1,80}$') { throw 'invalid branch' }
                      Push-Location $Root; & $Git switch $a[0] 2>&1 | ForEach-Object { "$_" }; $script:Code = $LASTEXITCODE; Pop-Location }
        'git-commit'{ # args: paths to add (relative); message read from .debug\runner\commit-msg.txt
                      $msg = Join-Path $Base 'commit-msg.txt'; if (-not (Test-Path $msg)) { throw 'missing commit-msg.txt' }
                      Push-Location $Root
                      foreach ($p in $a) { if ($p -match '\.\.|^[\\/]|:') { throw "invalid path $p" }; & $Git add -- $p 2>&1 | ForEach-Object { "$_" } }
                      & $Git commit -F $msg 2>&1 | ForEach-Object { "$_" }; $script:Code = $LASTEXITCODE
                      & $Git log --oneline -3; Pop-Location }
        'git-merge-ff'{ if ($a[0] -notmatch '^codex/[A-Za-z0-9._\-]{1,60}$') { throw 'invalid branch' }
                      Push-Location $Root; & $Git merge --ff-only $a[0] 2>&1 | ForEach-Object { "$_" }; $script:Code = $LASTEXITCODE; Pop-Location }
        'git-diffstat'{ Push-Location $Root; & $Git status --porcelain 2>&1 | ForEach-Object { "$_" }; & $Git diff --stat; Pop-Location }
        'git-fetch' { Push-Location $Root; & $Git fetch origin 2>&1 | ForEach-Object { "$_" }; $script:Code = $LASTEXITCODE; Pop-Location }
        'git-push'  { if ($a[0] -notmatch '^[A-Za-z0-9._/\-]{1,80}$') { throw 'invalid branch' }
                      Push-Location $Root; & $Git push origin $a[0] 2>&1 | ForEach-Object { "$_" }; $script:Code = $LASTEXITCODE; Pop-Location }
        default     { "unknown action: $action"; $script:Code = 64 }
    }
}
Write-Host "TabletCAD test runner watching $Queue (Ctrl+C to stop)"
while (-not (Test-Path (Join-Path $Base 'STOP'))) {
    $jobs = Get-ChildItem $Queue -Filter '*.job' -File -ErrorAction SilentlyContinue | Sort-Object Name
    foreach ($j in $jobs) {
        $id = $j.BaseName
        $lines = @(Get-Content $j.FullName -Encoding UTF8)
        Remove-Item $j.FullName -Force
        $log = Join-Path $Results "$id.log"
        Write-Host "$(Get-Date -Format HH:mm:ss) job $id : $($lines[0])"
        $script:Code = 1
        try {
            Invoke-Job $id $lines $log *>&1 | ForEach-Object { "$_" } | Out-File -FilePath $log -Encoding UTF8
        } catch { "EXCEPTION: $($_.Exception.Message)" | Out-File -FilePath $log -Encoding UTF8 -Append; $script:Code = 1 }
        $code = $script:Code
        Set-Content -Path (Join-Path $Results "$id.done") -Value $code -Encoding ASCII
        Write-Host "$(Get-Date -Format HH:mm:ss) job $id done exit=$code"
    }
    Start-Sleep -Seconds $PollSeconds
}
Remove-Item (Join-Path $Base 'STOP') -Force -ErrorAction SilentlyContinue
