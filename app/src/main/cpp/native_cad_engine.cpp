#include <jni.h>
#include <array>
#include <cmath>
#include <sstream>
#include <vector>

#include <BRepBndLib.hxx>
#include <BRepCheck_Analyzer.hxx>
#include <BRepPrimAPI_MakeBox.hxx>
#include <BRepPrimAPI_MakeCylinder.hxx>
#include <BRepPrimAPI_MakePrism.hxx>
#include <BRepBuilderAPI_MakeFace.hxx>
#include <BRepBuilderAPI_MakePolygon.hxx>
#include <BRepMesh_IncrementalMesh.hxx>
#include <BRep_Tool.hxx>
#include <Bnd_Box.hxx>
#include <Standard_Failure.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS_Shape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <TopLoc_Location.hxx>
#include <Poly_Triangulation.hxx>
#include <Poly_Triangle.hxx>
#include <gp_Ax2.hxx>
#include <gp_Dir.hxx>
#include <gp_Pnt.hxx>
#include <gp_Vec.hxx>
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

std::vector<double> tessellatedTriangles(const TopoDS_Shape& shape) {
    BRepMesh_IncrementalMesh mesher(shape, 0.15, false, 0.35, true);
    mesher.Perform();
    if (!mesher.IsDone()) return {};

    std::vector<double> packed;
    int faceIndex = 0;
    for (TopExp_Explorer explorer(shape, TopAbs_FACE); explorer.More(); explorer.Next()) {
        ++faceIndex;
        const TopoDS_Face face = TopoDS::Face(explorer.Current());
        TopLoc_Location location;
        const Handle(Poly_Triangulation) triangulation = BRep_Tool::Triangulation(face, location);
        if (triangulation.IsNull()) continue;
        const gp_Trsf transform = location.Transformation();
        for (int triangleIndex = 1; triangleIndex <= triangulation->NbTriangles(); ++triangleIndex) {
            int n1 = 0, n2 = 0, n3 = 0;
            triangulation->Triangle(triangleIndex).Get(n1, n2, n3);
            if (face.Orientation() == TopAbs_REVERSED) std::swap(n2, n3);
            const gp_Pnt p1 = triangulation->Node(n1).Transformed(transform);
            const gp_Pnt p2 = triangulation->Node(n2).Transformed(transform);
            const gp_Pnt p3 = triangulation->Node(n3).Transformed(transform);
            const gp_Vec edge1(p1, p2);
            const gp_Vec edge2(p1, p3);
            gp_Vec normal = edge1.Crossed(edge2);
            if (normal.SquareMagnitude() <= 1.0e-20) continue;
            normal.Normalize();
            packed.insert(packed.end(), {
                p1.X(), p1.Y(), p1.Z(), p2.X(), p2.Y(), p2.Z(), p3.X(), p3.Y(), p3.Z(),
                normal.X(), normal.Y(), normal.Z(), static_cast<double>(faceIndex)
            });
        }
    }
    return packed;
}

TopoDS_Shape extrudedProfile(const std::string& type, double x1, double y1,
                            double x2, double y2, double depth, const std::string& plane) {
    gp_Dir normal(0.0, 0.0, 1.0);
    if (plane == "XZ") normal = gp_Dir(0.0, 1.0, 0.0);
    else if (plane == "YZ") normal = gp_Dir(1.0, 0.0, 0.0);

    auto pointOnPlane = [&plane](double u, double v) {
        if (plane == "XZ") return gp_Pnt(u, 0.0, v);
        if (plane == "YZ") return gp_Pnt(0.0, u, v);
        return gp_Pnt(u, v, 0.0);
    };

    if (type == "Okrąg") {
        const double radius = std::hypot(x2 - x1, y2 - y1);
        if (radius <= 1.0e-6) return {};
        return BRepPrimAPI_MakeCylinder(gp_Ax2(pointOnPlane(x1, y1), normal), radius, depth).Shape();
    }

    const double xmin = std::min(x1, x2), xmax = std::max(x1, x2);
    const double ymin = std::min(y1, y2), ymax = std::max(y1, y2);
    if (xmax - xmin <= 1.0e-6 || ymax - ymin <= 1.0e-6) return {};
    BRepBuilderAPI_MakePolygon polygon;
    polygon.Add(pointOnPlane(xmin, ymin));
    polygon.Add(pointOnPlane(xmax, ymin));
    polygon.Add(pointOnPlane(xmax, ymax));
    polygon.Add(pointOnPlane(xmin, ymax));
    polygon.Close();
    const TopoDS_Face face = BRepBuilderAPI_MakeFace(polygon.Wire());
    return BRepPrimAPI_MakePrism(face, gp_Vec(normal) * depth).Shape();
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

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeTessellatePrism(
        JNIEnv* env, jclass, jdouble width, jdouble height, jdouble depth) {
    if (width <= 0.0 || height <= 0.0 || depth <= 0.0) return env->NewDoubleArray(0);
    try {
        const TopoDS_Shape shape = BRepPrimAPI_MakeBox(width, height, depth).Shape();
        if (!BRepCheck_Analyzer(shape).IsValid()) return env->NewDoubleArray(0);
        const std::vector<double> mesh = tessellatedTriangles(shape);
        jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(mesh.size()));
        if (result != nullptr && !mesh.empty()) {
            env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(mesh.size()), mesh.data());
        }
        return result;
    } catch (const Standard_Failure&) {
        return env->NewDoubleArray(0);
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeTessellateSketchExtrusion(
        JNIEnv* env, jclass, jstring javaType, jdouble x1, jdouble y1, jdouble x2,
        jdouble y2, jdouble depth, jstring javaPlane) {
    if (javaType == nullptr || javaPlane == nullptr || depth <= 0.0) return env->NewDoubleArray(0);
    const char* typeChars = env->GetStringUTFChars(javaType, nullptr);
    const char* planeChars = env->GetStringUTFChars(javaPlane, nullptr);
    if (typeChars == nullptr || planeChars == nullptr) {
        if (typeChars != nullptr) env->ReleaseStringUTFChars(javaType, typeChars);
        if (planeChars != nullptr) env->ReleaseStringUTFChars(javaPlane, planeChars);
        return env->NewDoubleArray(0);
    }
    const std::string type(typeChars), plane(planeChars);
    env->ReleaseStringUTFChars(javaType, typeChars);
    env->ReleaseStringUTFChars(javaPlane, planeChars);
    try {
        const TopoDS_Shape shape = extrudedProfile(type, x1, y1, x2, y2, depth, plane);
        if (shape.IsNull() || !BRepCheck_Analyzer(shape).IsValid()) return env->NewDoubleArray(0);
        const std::vector<double> mesh = tessellatedTriangles(shape);
        jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(mesh.size()));
        if (result != nullptr && !mesh.empty()) {
            env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(mesh.size()), mesh.data());
        }
        return result;
    } catch (const Standard_Failure&) {
        return env->NewDoubleArray(0);
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeTessellateStep(
        JNIEnv* env, jclass, jstring javaPath) {
    if (javaPath == nullptr) return env->NewDoubleArray(0);
    const char* pathChars = env->GetStringUTFChars(javaPath, nullptr);
    if (pathChars == nullptr) return env->NewDoubleArray(0);
    const std::string path(pathChars);
    env->ReleaseStringUTFChars(javaPath, pathChars);
    try {
        STEPControl_Reader reader;
        if (reader.ReadFile(path.c_str()) != IFSelect_RetDone || reader.TransferRoots() <= 0) {
            return env->NewDoubleArray(0);
        }
        const TopoDS_Shape shape = reader.OneShape();
        if (shape.IsNull() || !BRepCheck_Analyzer(shape).IsValid()) return env->NewDoubleArray(0);
        const std::vector<double> mesh = tessellatedTriangles(shape);
        jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(mesh.size()));
        if (result != nullptr && !mesh.empty()) {
            env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(mesh.size()), mesh.data());
        }
        return result;
    } catch (const Standard_Failure&) {
        return env->NewDoubleArray(0);
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

extern "C" JNIEXPORT jboolean JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeExportStep(
        JNIEnv* env, jclass, jstring javaPath, jdouble width, jdouble height, jdouble depth,
        jboolean useProfile, jstring javaType, jdouble x1, jdouble y1, jdouble x2, jdouble y2,
        jstring javaPlane) {
    if (javaPath == nullptr || javaType == nullptr || javaPlane == nullptr || depth <= 0.0) {
        return JNI_FALSE;
    }
    const char* pathChars = env->GetStringUTFChars(javaPath, nullptr);
    const char* typeChars = env->GetStringUTFChars(javaType, nullptr);
    const char* planeChars = env->GetStringUTFChars(javaPlane, nullptr);
    if (pathChars == nullptr || typeChars == nullptr || planeChars == nullptr) {
        if (pathChars != nullptr) env->ReleaseStringUTFChars(javaPath, pathChars);
        if (typeChars != nullptr) env->ReleaseStringUTFChars(javaType, typeChars);
        if (planeChars != nullptr) env->ReleaseStringUTFChars(javaPlane, planeChars);
        return JNI_FALSE;
    }
    const std::string path(pathChars), type(typeChars), plane(planeChars);
    env->ReleaseStringUTFChars(javaPath, pathChars);
    env->ReleaseStringUTFChars(javaType, typeChars);
    env->ReleaseStringUTFChars(javaPlane, planeChars);
    try {
        TopoDS_Shape shape;
        if (useProfile == JNI_TRUE) shape = extrudedProfile(type, x1, y1, x2, y2, depth, plane);
        else shape = BRepPrimAPI_MakeBox(width, height, depth).Shape();
        if (shape.IsNull() || !BRepCheck_Analyzer(shape).IsValid()) return JNI_FALSE;
        STEPControl_Writer writer;
        if (writer.Transfer(shape, STEPControl_AsIs) != IFSelect_RetDone
                || writer.Write(path.c_str()) != IFSelect_RetDone) return JNI_FALSE;
        STEPControl_Reader verifier;
        if (verifier.ReadFile(path.c_str()) != IFSelect_RetDone || verifier.TransferRoots() <= 0) {
            return JNI_FALSE;
        }
        const TopoDS_Shape verified = verifier.OneShape();
        return !verified.IsNull() && BRepCheck_Analyzer(verified).IsValid() ? JNI_TRUE : JNI_FALSE;
    } catch (const Standard_Failure&) {
        return JNI_FALSE;
    }
}
