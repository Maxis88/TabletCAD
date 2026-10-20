#!/usr/bin/env bash
# Desktop (Linux x86-64) verification of the TabletCAD kernel without a tablet.
#
# Builds the pinned OCCT 8.0.1 for the host, compiles app/src/main/cpp/native_cad_engine.cpp
# into a host libtabletcad.so and runs the headless probes on a desktop JVM through the same
# JNI entry points the Android app uses. Also runs the JVM host tests.
#
# Requirements: git, cmake >= 3.22, ninja, a C++20 compiler, JDK 17+, android.jar of
# platforms;android-36 (only as a compile-time class library) and org.json 20240303.
#
# Usage: tools/host-kernel-tests.sh --android-jar PATH --json-jar PATH [--occt DIR] [--jobs N]
# Results: build/host-kernel/results/*.txt; exit status is non-zero on any failure.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/host-kernel"
OCCT_SRC="$OUT/occt-src"; JOBS="$(nproc)"; ANDROID_JAR=""; JSON_JAR=""
REVISION=b8f597c677811d1f9f4d8a97f5ae2825c0353a42  # OCCT V8_0_1, same as tools/prepare-occt.ps1
while [ $# -gt 0 ]; do
  case "$1" in
    --android-jar) ANDROID_JAR="$2"; shift 2;;
    --json-jar) JSON_JAR="$2"; shift 2;;
    --occt) OCCT_SRC="$2"; shift 2;;
    --jobs) JOBS="$2"; shift 2;;
    *) echo "Unknown option $1" >&2; exit 2;;
  esac
done
[ -f "$ANDROID_JAR" ] || { echo "android.jar is required (--android-jar)" >&2; exit 2; }
[ -f "$JSON_JAR" ] || { echo "org.json jar is required (--json-jar)" >&2; exit 2; }
echo "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed  $JSON_JAR" | sha256sum -c --quiet

if [ ! -d "$OCCT_SRC" ]; then git clone --branch V8_0_1 --depth 1 https://github.com/Open-Cascade-SAS/OCCT.git "$OCCT_SRC"; fi
[ "$(git -C "$OCCT_SRC" rev-parse HEAD)" = "$REVISION" ] || { echo "Unexpected OCCT revision in $OCCT_SRC" >&2; exit 1; }
LIBS="TKDEIGES TKDESTEP TKDE TKXCAF TKXSBase TKVCAF TKCAF TKLCAF TKCDF TKOffset TKFillet TKBool TKBO TKPrim TKV3d TKService TKMesh TKShHealing TKHLR TKTopAlgo TKGeomAlgo TKBRep TKGeomBase TKG3d TKG2d TKMath TKernel"
if [ ! -f "$OUT/occt/lin64/gcc/lib/libTKDEIGES.so" ]; then
  cmake -S "$OCCT_SRC" -B "$OUT/occt" -G Ninja -DCMAKE_BUILD_TYPE=Release -DBUILD_LIBRARY_TYPE=Shared \
    -DBUILD_RESOURCES=OFF -DBUILD_MODULE_FoundationClasses=ON -DBUILD_MODULE_ModelingData=ON \
    -DBUILD_MODULE_ModelingAlgorithms=ON -DBUILD_MODULE_DataExchange=ON -DBUILD_MODULE_ApplicationFramework=OFF \
    -DBUILD_MODULE_Visualization=OFF -DBUILD_MODULE_Draw=OFF -DBUILD_DOC_Overview=OFF -DBUILD_DOC_RefMan=OFF \
    -DUSE_FREETYPE=OFF -DUSE_FREEIMAGE=OFF -DUSE_FFMPEG=OFF -DUSE_OPENVR=OFF -DUSE_DRACO=OFF -DUSE_RAPIDJSON=OFF \
    -DUSE_TK=OFF -DUSE_TCL=OFF -DUSE_OPENGL=OFF -DUSE_XLIB=OFF >/dev/null
  # shellcheck disable=SC2086
  cmake --build "$OUT/occt" --parallel "$JOBS" --target $LIBS
fi
OCCT_LIB="$(dirname "$(find -L "$OUT/occt" -name libTKernel.so -print -quit)")"
OCCT_INC="$OUT/occt/include/opencascade"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
mkdir -p "$OUT/native" "$OUT/classes" "$OUT/probes" "$OUT/results"
LINK=""; for l in $LIBS; do LINK="$LINK -l$l"; done
# shellcheck disable=SC2086
c++ -std=c++20 -O1 -fPIC -shared -Wall -Wextra -Werror -Wno-misleading-indentation -DTABLETCAD_IGES=1 \
  -I"$OCCT_INC" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  "$ROOT/app/src/main/cpp/native_cad_engine.cpp" -L"$OCCT_LIB" $LINK -Wl,-rpath,"$OCCT_LIB" \
  -o "$OUT/native/libtabletcad.so"

find "$ROOT/app/src/main/java" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -encoding UTF-8 --release 17 -classpath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"
CP="$OUT/probes:$OUT/classes:$JSON_JAR:$ANDROID_JAR"
# Probes that only use the kernel and the document model run headless on the desktop JVM.
PROBES="${PROBES:-KernelDiagnosticsProbe EdgeRepairKernelProbe IgesKernelProbe SolidPatternKernelProbe SketchDesignKernelProbe CadToolsKernelProbe ConstructionKernelProbe HistoryKernelProbe AnalysisKernelProbe PrintKernelProbe AssemblyKernelProbe FaceOffsetKernelProbe MateKernelProbe AcceptanceAKernelProbe ThreadProbe BodyPropertiesProbe EdgeChamferProbe SketchEditingProbe}"
HOST_TESTS="${HOST_TESTS:-SketchDesignHostTest SolidPatternHostTest UnitsHostTest CadToolsHostTest ConstructionHostTest LinkedCopiesHostTest ProjectLibraryFilesHostTest BodyMassHostTest LibraryWorkspaceHostTest ViewCubeHostTest SketchPositionHostTest KernelCacheHostTest FaceProjectionHostTest SketchAngleHostTest SheetDimensionHostTest StylusHostTest SketchPointHostTest ThreeMfHostTest CompactPlacementHostTest}"
status=0
for test in $HOST_TESTS $PROBES; do
  # A probe that does not compile must fail the run, never execute a stale class.
  javac -nowarn -encoding UTF-8 -classpath "$CP" -d "$OUT/probes" "$ROOT/tools/$test.java" || { echo "FAIL $test — compilation"; status=1; }
done
for test in $HOST_TESTS $PROBES; do
  mkdir -p "$OUT/work/$test"
  # Host tests take an isolated directory; probes take an output file path (e.g. a STEP export).
  case "$test" in *HostTest) arg="$OUT/work/$test";; *) arg="$OUT/work/$test/out.step";; esac
  if java -Dstdout.encoding=UTF-8 -Djava.library.path="$OUT/native" -classpath "$CP" "$test" "$arg" > "$OUT/results/$test.txt" 2>&1; then
    echo "PASS $test ($(grep -c '^PASS' "$OUT/results/$test.txt") checks)"
  else
    echo "FAIL $test — see build/host-kernel/results/$test.txt"; tail -5 "$OUT/results/$test.txt"; status=1
  fi
done
exit $status
