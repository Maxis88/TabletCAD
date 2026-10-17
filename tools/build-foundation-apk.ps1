param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [string]$Jdk = "C:\Program Files\Android\Android Studio\jbr"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$VersionSource = Get-Content (Join-Path $Root "app\build.gradle.kts") -Raw
$VersionCode = [regex]::Match($VersionSource, 'versionCode\s*=\s*(\d+)').Groups[1].Value
$VersionName = [regex]::Match($VersionSource, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $VersionCode -or -not $VersionName) { throw "Missing application version" }
$Build = Join-Path $Root "build\foundation"
$Out = Join-Path $Root "dist"
$Aapt2 = Join-Path $Sdk "build-tools\36.0.0\aapt2.exe"
$D8 = Join-Path $Sdk "build-tools\36.0.0\d8.bat"
$Signer = Join-Path $Sdk "build-tools\36.0.0\apksigner.bat"
$Cmake = Join-Path $Sdk "cmake\4.1.2\bin\cmake.exe"
$Ninja = Join-Path $Sdk "cmake\4.1.2\bin\ninja.exe"
$Ndk = Join-Path $Sdk "ndk\30.0.16248370"
$AndroidJar = Join-Path $Sdk "platforms\android-36\android.jar"
$Javac = Join-Path $Jdk "bin\javac.exe"
$Keytool = Join-Path $Jdk "bin\keytool.exe"
$Jar = Join-Path $Jdk "bin\jar.exe"
$Keystore = Join-Path $Root ".debug\tabletcad-debug.jks"
$env:JAVA_HOME = $Jdk
if (-not (Test-Path (Join-Path $Root 'build\occt-arm64\lin64\clang\lib\libTKDESTEP.so'))) {
    & (Join-Path $PSScriptRoot 'prepare-occt.ps1') -Sdk $Sdk
}
$env:TEMP = Join-Path $Build "temp"
$env:TMP = $env:TEMP

function Assert-ExitCode([string]$Step) {
    if ($LASTEXITCODE -ne 0) { throw "$Step failed with exit code $LASTEXITCODE" }
}

New-Item -ItemType Directory -Force -Path $Build, $Out, (Join-Path $Build "res"), (Join-Path $Build "classes"), (Join-Path $Build "assets\licenses"), (Split-Path $Keystore), $env:TEMP | Out-Null
Copy-Item (Join-Path $Root "third_party\occt\LICENSE_LGPL_21.txt") (Join-Path $Build "assets\licenses\OCCT-LGPL-2.1.txt") -Force

& $Aapt2 compile --dir (Join-Path $Root "app\src\main\res") -o (Join-Path $Build "res.zip")
Assert-ExitCode "Resource compilation"
& $Aapt2 link -o (Join-Path $Build "unsigned.apk") -I $AndroidJar --manifest (Join-Path $Root "app\src\main\AndroidManifest.xml") (Join-Path $Build "res.zip") -A (Join-Path $Build "assets") --min-sdk-version 34 --target-sdk-version 36 --version-code $VersionCode --version-name $VersionName --java (Join-Path $Build "generated") --auto-add-overlay
Assert-ExitCode "Resource linking"

$Sources = Get-ChildItem (Join-Path $Root "app\src\main\java") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& $Javac -source 17 -target 17 -encoding UTF-8 -classpath $AndroidJar -d (Join-Path $Build "classes") $Sources
Assert-ExitCode "Java compilation"
$ClassFiles = Get-ChildItem (Join-Path $Build "classes") -Recurse -Filter "*.class" | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force -Path (Join-Path $Build "dex") | Out-Null
& $D8 --lib $AndroidJar --output (Join-Path $Build "dex") $ClassFiles
Assert-ExitCode "DEX compilation"

Push-Location (Join-Path $Build "dex")
try {
    & $Jar uf (Join-Path $Build "unsigned.apk") "classes.dex"
    Assert-ExitCode "DEX packaging"
} finally {
    Pop-Location
}

$NativeBuild = Join-Path $Build "native-arm64"
New-Item -ItemType Directory -Force -Path $NativeBuild | Out-Null
& $Cmake -S (Join-Path $Root "app\src\main\cpp") -B $NativeBuild -G Ninja "-DCMAKE_MAKE_PROGRAM=$Ninja" "-DCMAKE_TOOLCHAIN_FILE=$Ndk\build\cmake\android.toolchain.cmake" "-DANDROID_ABI=arm64-v8a" "-DANDROID_PLATFORM=android-34" "-DCMAKE_BUILD_TYPE=Release"
Assert-ExitCode "Native configuration"
& $Cmake --build $NativeBuild
Assert-ExitCode "Native compilation"

$NativePackage = Join-Path $Build "native-package\lib\arm64-v8a"
New-Item -ItemType Directory -Force -Path $NativePackage | Out-Null
Copy-Item (Join-Path $NativeBuild "libtabletcad.so") $NativePackage -Force
$OcctLibDir = Join-Path $Root "build\occt-arm64\lin64\clang\lib"
$OcctLibraries = Get-ChildItem $OcctLibDir -Filter "*.so"
if ($OcctLibraries.Count -ne 25) { throw "Expected 25 OCCT STEP and fillet dependency libraries, found $($OcctLibraries.Count)" }
$OcctLibraries | Copy-Item -Destination $NativePackage -Force
Push-Location (Join-Path $Build "native-package")
try {
    & $Jar uf (Join-Path $Build "unsigned.apk") "lib\arm64-v8a"
    Assert-ExitCode "Native library packaging"
} finally {
    Pop-Location
}

if (-not (Test-Path $Keystore)) {
    & $Keytool -genkeypair -keystore $Keystore -storepass android -keypass android -alias tabletcad -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=TabletCAD Debug,O=TabletCAD,C=PL"
    Assert-ExitCode "Debug key creation"
}

$Apk = Join-Path $Out "TabletCAD-$VersionName.apk"
Copy-Item (Join-Path $Build "unsigned.apk") $Apk -Force
& $Signer sign --ks $Keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias tabletcad $Apk
Assert-ExitCode "APK signing"
& $Signer verify --verbose --print-certs $Apk
Assert-ExitCode "APK verification"
Write-Output $Apk
