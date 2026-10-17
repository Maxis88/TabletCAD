param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference='Stop'
$Root=Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Build=Join-Path $Root 'build\ui-tests'
$Classes=Join-Path $Build 'classes'
$Dex=Join-Path $Build 'dex'
$AndroidJar=Join-Path $Sdk 'platforms\android-36\android.jar'
$AppClasses=Join-Path $Root 'build\foundation\classes'
$BuildTools=Join-Path $Sdk 'build-tools\36.0.0'
$env:JAVA_HOME=$Jdk
$env:TEMP=Join-Path $Build 'temp';$env:TMP=$env:TEMP
function Check([string]$Step){if($LASTEXITCODE -ne 0){throw "$Step failed: $LASTEXITCODE"}}
if(-not(Test-Path $AppClasses)){throw 'Build the application first'}
New-Item -ItemType Directory -Force $Build,$Classes,$Dex,$env:TEMP | Out-Null
$Sources=@((Get-ChildItem (Join-Path $Root 'tools\*Probe.java')).FullName)+(Join-Path $Root 'tools\WorkspaceInstrumentation.java')
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 -classpath "$AppClasses;$AndroidJar" -d $Classes $Sources
Check 'Compile probes'
$ClassFiles=(Get-ChildItem $Classes -Recurse -Filter '*.class').FullName
& (Join-Path $BuildTools 'd8.bat') --min-api 34 --lib $AndroidJar --classpath $AppClasses --output $Dex $ClassFiles
Check 'DEX probes'
$Apk=Join-Path $Build 'TabletCAD-ui-tests.apk'
& (Join-Path $BuildTools 'aapt2.exe') link -o $Apk -I $AndroidJar --manifest (Join-Path $Root 'tools\AndroidManifest-ui-test.xml')
Check 'Package test manifest'
Push-Location $Dex
try{& (Join-Path $Jdk 'bin\jar.exe') uf $Apk classes.dex;Check 'Package test DEX'}finally{Pop-Location}
& (Join-Path $BuildTools 'apksigner.bat') sign --ks (Join-Path $Root '.debug\tabletcad-debug.jks') --ks-pass pass:android --key-pass pass:android --ks-key-alias tabletcad $Apk
Check 'Sign test APK'
& (Join-Path $BuildTools 'apksigner.bat') verify $Apk
Check 'Verify test APK'
Write-Output $Apk
