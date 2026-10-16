#include <jni.h>
#include <array>
#include <sstream>

#include <BRepBndLib.hxx>
#include <BRepCheck_Analyzer.hxx>
#include <BRepPrimAPI_MakeBox.hxx>
#include <Bnd_Box.hxx>
#include <Standard_Failure.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS_Shape.hxx>
#include <STEPControl_Reader.hxx>
#include <STEPControl_Writer.hxx>
#include <IFSelect_ReturnStatus.hxx>

namespace {
std::array<double, 24> prismBounds(const TopoDS_Shape& shape) {
    Bnd_Box bounds;
    BRepBndLib::Add(shape, bounds);
    double xmin = 0.0, ymin = 0.0, zmin = 0.0;
    double xmax = 0.0, ymax = 0.0, zmax = 0.0;
    bounds.Get(xmin, ymin, zmin, xmax, ymax, zmax);
    return {
        xmin, ymin, zmin, xmax, ymin, zmin, xmax, ymax, zmin, xmin, ymax, zmin,
        xmin, ymin, zmax, xmax, ymin, zmax, xmax, ymax, zmax, xmin, ymax, zmax
    };
}

int countShapes(const TopoDS_Shape& shape, TopAbs_ShapeEnum type) {
    int count = 0;
    for (TopExp_Explorer explorer(shape, type); explorer.More(); explorer.Next()) {
        ++count;
    }
    return count;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeKernelStatus(JNIEnv* env, jclass) {
    try {
        const TopoDS_Shape probe = BRepPrimAPI_MakeBox(10.0, 10.0, 10.0).Shape();
        const bool valid = BRepCheck_Analyzer(probe).IsValid();
        return env->NewStringUTF(valid ? "OCCT 8.0.1 B-Rep aktywny" : "OCCT: test B-Rep nieudany");
    } catch (const Standard_Failure&) {
        return env->NewStringUTF("OCCT: błąd inicjalizacji");
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeCreatePrism(
        JNIEnv* env, jclass, jdouble width, jdouble height, jdouble depth) {
    if (width <= 0.0 || height <= 0.0 || depth <= 0.0) {
        return env->NewDoubleArray(0);
    }
    std::array<double, 24> vertices{};
    try {
        const TopoDS_Shape shape = BRepPrimAPI_MakeBox(width, height, depth).Shape();
        if (!BRepCheck_Analyzer(shape).IsValid()) {
            return env->NewDoubleArray(0);
        }
        vertices = prismBounds(shape);
    } catch (const Standard_Failure&) {
        return env->NewDoubleArray(0);
    }
    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(vertices.size()));
    if (result != nullptr) {
        env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(vertices.size()), vertices.data());
    }
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeModelInfo(
        JNIEnv* env, jclass, jdouble width, jdouble height, jdouble depth) {
    try {
        const TopoDS_Shape shape = BRepPrimAPI_MakeBox(width, height, depth).Shape();
        const bool valid = BRepCheck_Analyzer(shape).IsValid();
        std::ostringstream info;
        info << (valid ? "Poprawna bryła" : "Błędna bryła")
             << " • ściany " << countShapes(shape, TopAbs_FACE)
             << " • krawędzie " << countShapes(shape, TopAbs_EDGE)
             << " • wierzchołki " << countShapes(shape, TopAbs_VERTEX);
        return env->NewStringUTF(info.str().c_str());
    } catch (const Standard_Failure&) {
        return env->NewStringUTF("Nie udało się utworzyć bryły B-Rep");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeStepRoundTrip(
        JNIEnv* env, jclass, jstring javaPath) {
    if (javaPath == nullptr) {
        return env->NewStringUTF("STEP: brak ścieżki testowej");
    }
    const char* pathChars = env->GetStringUTFChars(javaPath, nullptr);
    if (pathChars == nullptr) {
        return env->NewStringUTF("STEP: błąd ścieżki testowej");
    }
    const std::string path(pathChars);
    env->ReleaseStringUTFChars(javaPath, pathChars);

    try {
        const TopoDS_Shape source = BRepPrimAPI_MakeBox(80.0, 48.0, 32.0).Shape();
        STEPControl_Writer writer;
        if (writer.Transfer(source, STEPControl_AsIs) != IFSelect_RetDone
                || writer.Write(path.c_str()) != IFSelect_RetDone) {
            return env->NewStringUTF("STEP: zapis testowy nieudany");
        }

        STEPControl_Reader reader;
        if (reader.ReadFile(path.c_str()) != IFSelect_RetDone || reader.TransferRoots() <= 0) {
            return env->NewStringUTF("STEP: odczyt testowy nieudany");
        }
        const TopoDS_Shape imported = reader.OneShape();
        if (imported.IsNull() || !BRepCheck_Analyzer(imported).IsValid()) {
            return env->NewStringUTF("STEP: niepoprawna bryła po imporcie");
        }
        return env->NewStringUTF("STEP: zapis i odczyt aktywne");
    } catch (const Standard_Failure&) {
        return env->NewStringUTF("STEP: wyjątek jądra CAD");
    }
}
