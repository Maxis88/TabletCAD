param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [string]$Jdk = "C:\Program Files\Android\Android Studio\jbr"
)
# Android Studio may be missing or ship a runtime without javac/jar: fall back to the portable JDK in third_party\jdk.
if (-not (Test-Path (Join-Path $Jdk 'bin\javac.exe'))) {
    $Portable = Get-ChildItem (Join-Path (Split-Path -Parent $PSScriptRoot) 'third_party\jdk') -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } | Select-Object -First 1
    if ($Portable) { $Jdk = $Portable.FullName; Write-Host "Using portable JDK $Jdk" } else { throw "No JDK with javac.exe: $Jdk (and none in third_party\jdk)" }
}


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
# Newer Android Studio runtimes ship without jar.exe: fall back to .NET zip handling (APK and JAR are zip files).
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
function Zip-Add([string]$Archive, [string]$BaseDir, [string]$Relative, [switch]$Create) {
    if ($Create -and (Test-Path $Archive)) { Remove-Item $Archive -Force }
    $mode = if (Test-Path $Archive) { [IO.Compression.ZipArchiveMode]::Update } else { [IO.Compression.ZipArchiveMode]::Create }
    $zip = [IO.Compression.ZipFile]::Open($Archive, $mode)
    try {
        $start = [IO.Path]::GetFullPath((Join-Path $BaseDir $Relative))
        $files = if (Test-Path $start -PathType Container) { Get-ChildItem $start -Recurse -File } else { @(Get-Item $start) }
        $base = [IO.Path]::GetFullPath($BaseDir).TrimEnd('\') + '\'
        foreach ($f in $files) {
            $name = $f.FullName.Substring($base.Length).Replace('\', '/')
            $old = $zip.GetEntry($name); if ($old) { $old.Delete() }
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $f.FullName, $name, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally { $zip.Dispose() }
    $global:LASTEXITCODE = 0
}
$HasJar = Test-Path $Jar
$Keystore = Join-Path $Root ".debug\tabletcad-debug.jks"
$env:JAVA_HOME = $Jdk
if (-not (Test-Path (Join-Path $Root 'build\occt-arm64\lin64\clang\lib\libTKDEIGES.so'))) {
    # 0.99 adds IGES; the incremental OCCT build compiles only the missing toolkit.
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
& $Aapt2 link -o (Join-Path $Build "unsigned.apk") -I $AndroidJar --manifest (Join-Path $Root "app\src\main\AndroidManifest.xml") (Join-Path $Build "res.zip") --min-sdk-version 34 --target-sdk-version 36 --version-code $VersionCode --version-name $VersionName --java (Join-Path $Build "generated") --auto-add-overlay
Assert-ExitCode "Resource linking"
if ($HasJar) { & $Jar uf (Join-Path $Build "unsigned.apk") -C $Build assets } else { Zip-Add (Join-Path $Build "unsigned.apk") $Build "assets" }
Assert-ExitCode "License assets packaging"

$Sources = Get-ChildItem (Join-Path $Root "app\src\main\java") -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& $Javac -source 17 -target 17 -encoding UTF-8 -classpath $AndroidJar -d (Join-Path $Build "classes") $Sources
Assert-ExitCode "Java compilation"
$ClassesJar = Join-Path $Build "classes.jar"
if ($HasJar) { & $Jar cf $ClassesJar -C (Join-Path $Build "classes") . } else { Zip-Add $ClassesJar (Join-Path $Build "classes") "." -Create }
Assert-ExitCode "Class archive creation"
New-Item -ItemType Directory -Force -Path (Join-Path $Build "dex") | Out-Null
& $D8 --thread-count 2 --lib $AndroidJar --output (Join-Path $Build "dex") $ClassesJar
Assert-ExitCode "DEX compilation"

Push-Location (Join-Path $Build "dex")
try {
    if ($HasJar) { & $Jar uf (Join-Path $Build "unsigned.apk") "classes.dex" } else { Zip-Add (Join-Path $Build "unsigned.apk") (Get-Location).Path "classes.dex" }
    Assert-ExitCode "DEX packaging"
} finally {
    Pop-Location
}

$NativeBuild = Join-Path $Build "native-arm64"
New-Item -ItemType Directory -Force -Path $NativeBuild | Out-Null
& $Cmake -S (Join-Path $Root "app\src\main\cpp") -B $NativeBuild -G Ninja "-DCMAKE_MAKE_PROGRAM=$Ninja" "-DCMAKE_TOOLCHAIN_FILE=$Ndk\build\cmake\android.toolchain.cmake" "-DANDROID_ABI=arm64-v8a" "-DANDROID_PLATFORM=android-34" "-DCMAKE_BUILD_TYPE=Release"
Assert-ExitCode "Native configuration"
& $Cmake --build $NativeBuild --parallel 2
Assert-ExitCode "Native compilation"

$NativePackage = Join-Path $Build "native-package\lib\arm64-v8a"
New-Item -ItemType Directory -Force -Path $NativePackage | Out-Null
Copy-Item (Join-Path $NativeBuild "libtabletcad.so") $NativePackage -Force
$OcctLibDir = Join-Path $Root "build\occt-arm64\lin64\clang\lib"
$OcctLibraries = Get-ChildItem $OcctLibDir -Filter "*.so"
if ($OcctLibraries.Count -ne 27) { throw "Expected 27 OCCT STEP, IGES, fillet and sweep dependency libraries, found $($OcctLibraries.Count)" }
$OcctLibraries | Copy-Item -Destination $NativePackage -Force
Push-Location (Join-Path $Build "native-package")
try {
    if ($HasJar) { & $Jar uf (Join-Path $Build "unsigned.apk") "lib\arm64-v8a" } else { Zip-Add (Join-Path $Build "unsigned.apk") (Get-Location).Path "lib\arm64-v8a" }
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
