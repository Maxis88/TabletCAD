param(
    [string]$Sdk = $env:ANDROID_HOME,
    [int]$Jobs = 4
)
$ErrorActionPreference = 'Stop'
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$Root = Split-Path -Parent $PSScriptRoot
$Source = Join-Path $Root 'third_party/occt'
$Build = Join-Path $Root 'build/occt-arm64'
$Revision = 'b8f597c677811d1f9f4d8a97f5ae2825c0353a42' # OCCT V8_0_1
$Cmake = Join-Path $Sdk 'cmake/4.1.2/bin/cmake.exe'
$Ninja = Join-Path $Sdk 'cmake/4.1.2/bin/ninja.exe'
$Toolchain = Join-Path $Sdk 'ndk/30.0.16248370/build/cmake/android.toolchain.cmake'
foreach ($Required in @($Cmake, $Ninja, $Toolchain)) {
    if (-not (Test-Path $Required)) { throw "Missing SDK component: $Required" }
}
function Check([string]$Step) {
    if ($LASTEXITCODE -ne 0) { throw "$Step failed: $LASTEXITCODE" }
}
if (-not (Test-Path $Source)) {
    New-Item -ItemType Directory -Force (Split-Path $Source) | Out-Null
    & git clone --branch V8_0_1 --depth 1 https://github.com/Open-Cascade-SAS/OCCT.git $Source
    Check 'OCCT clone'
}
$Actual = & git -C $Source rev-parse HEAD
Check 'OCCT revision'
if ($Actual -ne $Revision) { throw "Expected OCCT $Revision, found $Actual; source left unchanged" }
$Changes = & git -C $Source status --porcelain --untracked-files=no
Check 'OCCT status'
if ($Changes) { throw 'OCCT has local changes; source left unchanged' }
$Options = @(
    '-S', $Source, '-B', $Build, '-G', 'Ninja',
    "-DCMAKE_MAKE_PROGRAM=$Ninja", "-DCMAKE_TOOLCHAIN_FILE=$Toolchain",
    '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-34',
    '-DCMAKE_BUILD_TYPE=Release', '-DBUILD_LIBRARY_TYPE=Shared',
    '-DBUILD_SOVERSION_NUMBERS=0', '-DBUILD_RESOURCES=OFF',
    '-DBUILD_MODULE_FoundationClasses=ON', '-DBUILD_MODULE_ModelingData=ON',
    '-DBUILD_MODULE_ModelingAlgorithms=ON', '-DBUILD_MODULE_DataExchange=ON',
    '-DBUILD_MODULE_ApplicationFramework=OFF', '-DBUILD_MODULE_Visualization=OFF',
    '-DBUILD_MODULE_Draw=OFF', '-DBUILD_DOC_Overview=OFF', '-DBUILD_DOC_RefMan=OFF',
    '-DUSE_FREETYPE=OFF', '-DUSE_FREEIMAGE=OFF', '-DUSE_FFMPEG=OFF',
    '-DUSE_OPENVR=OFF', '-DUSE_DRACO=OFF', '-DUSE_RAPIDJSON=OFF'
)
& $Cmake @Options
Check 'OCCT configure'
$Targets = @('TKDESTEP','TKDE','TKXCAF','TKXSBase','TKVCAF','TKCAF','TKLCAF',
    'TKCDF','TKOffset','TKFillet','TKBool','TKBO','TKPrim','TKV3d','TKService','TKMesh',
    'TKShHealing','TKHLR','TKTopAlgo','TKGeomAlgo','TKBRep','TKGeomBase',
    'TKG3d','TKG2d','TKMath','TKernel')
& $Cmake --build $Build --parallel $Jobs --target @Targets
Check 'OCCT build'
