param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference='Stop'
$Root=Split-Path -Parent $PSScriptRoot
$Build=Join-Path $Root 'build\host-tests'
$Classes=Join-Path $Build 'classes'
$AppClasses=Join-Path $Root 'build\foundation\classes'
$Json=Join-Path $Build 'lib\json-20240303.jar'
if(-not(Test-Path $AppClasses)){throw 'Build the application first.'}
New-Item -ItemType Directory -Force $Classes,(Split-Path -Parent $Json) | Out-Null
# Host-only JSON runtime. The Android application continues using platform org.json.
if(-not(Test-Path $Json)){Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar' -OutFile $Json}
if((Get-FileHash $Json -Algorithm SHA256).Hash -ne '3CF6CD6892E32E2B4C1C39E0F52F5248A2F5B37646FDFBB79A66B46B618414ED'){throw 'Host JSON library checksum mismatch.'}
$Classpath="$Classes;$AppClasses;$Json;$(Join-Path $Sdk 'platforms\android-36\android.jar')"
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 -classpath $Classpath -d $Classes (Join-Path $PSScriptRoot 'ProjectLibraryFilesHostTest.java')
if($LASTEXITCODE -ne 0){throw 'Host test compilation failed.'}
& (Join-Path $Jdk 'bin\java.exe') -classpath $Classpath ProjectLibraryFilesHostTest $Build | Tee-Object -FilePath (Join-Path $Build 'library-results.txt')
if($LASTEXITCODE -ne 0){throw 'Host library tests failed.'}
