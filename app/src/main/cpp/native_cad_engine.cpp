#include <jni.h>
#include <array>
#include <cmath>
#include <sstream>
#include <string>
#include <vector>
#include <map>
#include <mutex>
#include <algorithm>
#include <sys/stat.h>

#include <BRepBndLib.hxx>
#include <BRepCheck_Analyzer.hxx>
#include <BRepGProp.hxx>
#include <GProp_GProps.hxx>
#include <BRepPrimAPI_MakeBox.hxx>
#include <BRepPrimAPI_MakeCylinder.hxx>
#include <BRepPrimAPI_MakePrism.hxx>
#include <BRepBuilderAPI_MakeFace.hxx>
#include <BRepBuilderAPI_MakeEdge.hxx>
#include <BRepBuilderAPI_MakePolygon.hxx>
#include <BRepBuilderAPI_MakeWire.hxx>
#include <GC_MakeArcOfCircle.hxx>
#include <Geom_TrimmedCurve.hxx>
#include <BRepFilletAPI_MakeFillet.hxx>
#include <BRepFilletAPI_MakeChamfer.hxx>
#include <BRepPrimAPI_MakeRevol.hxx>
#include <ShapeUpgrade_UnifySameDomain.hxx>
#include <BRepAlgoAPI_Cut.hxx>
#include <BRepAlgoAPI_Fuse.hxx>
#include <BRepAlgoAPI_Common.hxx>
#include <BRep_Builder.hxx>
#include <TopoDS_Compound.hxx>
#include <BRepMesh_IncrementalMesh.hxx>
#include <BRep_Tool.hxx>
#include <Bnd_Box.hxx>
#include <Standard_Failure.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopExp_Explorer.hxx>
#include <TopExp.hxx>
#include <NCollection_IndexedMap.hxx>
#include <NCollection_IndexedDataMap.hxx>
#include <NCollection_List.hxx>
#include <TopTools_ShapeMapHasher.hxx>
#include <TopoDS_Shape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Edge.hxx>
#include <TopoDS_Wire.hxx>
#include <TopLoc_Location.hxx>
#include <Poly_Triangulation.hxx>
#include <Poly_Triangle.hxx>
#include <gp_Ax2.hxx>
#include <gp_Circ.hxx>
#include <gp_Dir.hxx>
#include <gp_Pnt.hxx>
#include <gp_Vec.hxx>
#include <STEPControl_Reader.hxx>
#include <STEPControl_Writer.hxx>
#include <IFSelect_ReturnStatus.hxx>
#include <HLRBRep_Algo.hxx>
#include <HLRBRep_HLRToShape.hxx>
#include <HLRAlgo_Projector.hxx>
#include <BRepAdaptor_Curve.hxx>
#include <BRepAdaptor_Surface.hxx>
#include <BRepTools.hxx>
#include <BRepClass3d_SolidClassifier.hxx>
#include <Geom_CylindricalSurface.hxx>
#include <Geom2d_Line.hxx>
#include <BRepOffsetAPI_MakePipeShell.hxx>
#include <BRepBuilderAPI_Transform.hxx>
#include <gp_Ax3.hxx>
#include <gp_Trsf.hxx>
#include <gp_Pnt2d.hxx>
#include <gp_Dir2d.hxx>
#include <BRepLib.hxx>
#include <BRepAlgoAPI_Section.hxx>
#include <gp_Pln.hxx>
#include <BRepOffsetAPI_MakeThickSolid.hxx>
#include <BRepOffsetAPI_MakeOffsetShape.hxx>
#include <BRepClass_FaceClassifier.hxx>
#include <BRepBuilderAPI_MakeSolid.hxx>
#include <TopoDS_Solid.hxx>
#include <TopoDS_Shell.hxx>
#include <BRepPrimAPI_MakeCone.hxx>
#include <Geom_Surface.hxx>
#include <BRepOffsetAPI_DraftAngle.hxx>

namespace {
// Diagnostics of the last failed evaluation on this thread (feature -1 = base body, -2 = output stage).
struct KernelError { int feature=-2; std::string message; };
thread_local KernelError g_kernelError;
// View-only section plane for query mode 9 (point xyz, normal xyz; material behind the normal is kept).
thread_local std::array<double,6> g_sectionPlane{};thread_local bool g_sectionActive=false;
void kernelReset(){g_kernelError=KernelError();}
void kernelReason(const char* message){if(g_kernelError.message.empty())g_kernelError.message=message;}
void kernelFailAt(int feature,const char* message){g_kernelError.feature=feature;kernelReason(message);}
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
                            double x2, double y2, double depth, const std::string& plane,bool symmetric=false) {
    if(!std::isfinite(depth)||std::abs(depth)<=1e-6)return {};
    gp_Dir normal(0.0, 0.0, 1.0);
    if (plane == "XZ") normal = gp_Dir(0.0, 1.0, 0.0);
    else if (plane == "YZ") normal = gp_Dir(1.0, 0.0, 0.0);
    if(depth<0)normal.Reverse();
    depth=std::abs(depth);

    const gp_Vec shift=symmetric?gp_Vec(normal)*(-depth*.5):gp_Vec(0,0,0);
    auto pointOnPlane = [&plane,&shift](double u, double v) {
        gp_Pnt point=plane=="XZ"?gp_Pnt(u,0,v):plane=="YZ"?gp_Pnt(0,u,v):gp_Pnt(u,v,0);
        return point.Translated(shift);
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

TopoDS_Shape filletAllEdges(const TopoDS_Shape& source, double radius) {
    if (source.IsNull() || radius <= 0.0) return {};
    BRepFilletAPI_MakeFillet maker(source);
    for (TopExp_Explorer explorer(source, TopAbs_EDGE); explorer.More(); explorer.Next()) {
        maker.Add(radius, TopoDS::Edge(explorer.Current()));
    }
    maker.Build();
    if (!maker.IsDone()) return {};
    const TopoDS_Shape result = maker.Shape();
    if (result.IsNull() || !BRepCheck_Analyzer(result).IsValid()) return {};
    return result;
}

std::array<double,9> edgeSignature(const TopoDS_Edge& edge) {
    BRepAdaptor_Curve curve(edge);
    std::array<double,9> signature;
    for(int i=0;i<3;i++) {
        gp_Pnt p=curve.Value(curve.FirstParameter()+(curve.LastParameter()-curve.FirstParameter())*i/2.0);
        signature[i*3]=p.X();signature[i*3+1]=p.Y();signature[i*3+2]=p.Z();
    }
    return signature;
}

TopoDS_Edge edgeForSignature(const TopoDS_Shape& source,const double* signature) {
    if(source.IsNull())return {};
    NCollection_IndexedMap<TopoDS_Shape,TopTools_ShapeMapHasher> edges;TopExp::MapShapes(source,TopAbs_EDGE,edges);
    TopoDS_Edge selected;int matches=0;
    for(int i=1;i<=edges.Extent();i++) {
        TopoDS_Edge edge=TopoDS::Edge(edges(i));if(BRep_Tool::Degenerated(edge))continue;
        auto actual=edgeSignature(edge);bool forward=true,backward=true;
        for(int j=0;j<9;j++) {
            forward &= std::abs(actual[j]-signature[j])<=1e-5;
            backward &= std::abs(actual[(2-j/3)*3+j%3]-signature[j])<=1e-5;
        }
        if(forward||backward){selected=edge;matches++;}
    }
    return matches==1?selected:TopoDS_Edge();
}

bool sameEdgeGeometry(const TopoDS_Edge& a,const TopoDS_Edge& b) {
    BRepAdaptor_Curve first(a),second(b);if(first.GetType()!=second.GetType())return false;
    if(first.GetType()==GeomAbs_Circle) {
        gp_Circ x=first.Circle(),y=second.Circle();
        return x.Location().Distance(y.Location())<=1e-5&&std::abs(x.Radius()-y.Radius())<=1e-5
            &&gp_Vec(x.Axis().Direction()).Crossed(gp_Vec(y.Axis().Direction())).Magnitude()<1e-7;
    }
    return first.GetType()==GeomAbs_Line;
}

TopoDS_Shape filletSelectedEdge(const TopoDS_Shape& source,const double* signature,double radius,bool chamfer=false,const TopoDS_Edge* expected=nullptr) {
    if(source.IsNull()){kernelReason("Brak bryły dla operacji na krawędzi");return {};}
    if(!std::isfinite(radius)||radius<.1||radius>1000){kernelReason("Wartość musi mieścić się w zakresie 0,1–1000 mm");return {};}
    TopoDS_Edge selected=edgeForSignature(source,signature);
    if(selected.IsNull()){kernelReason("Nie odnaleziono jednoznacznie wskazanej krawędzi – topologia zmieniła się po edycji wcześniejszej operacji");return {};}
    if(expected&&!sameEdgeGeometry(selected,*expected)){kernelReason("Odnaleziona krawędź ma inną geometrię niż pierwotnie wskazana");return {};}
    if(chamfer) {
        BRepFilletAPI_MakeChamfer maker(source);maker.Add(radius,selected);maker.Build();
        if(!maker.IsDone()||maker.Shape().IsNull()||!BRepCheck_Analyzer(maker.Shape()).IsValid()){kernelReason("OCCT nie może wykonać fazy o tej wielkości na wskazanej krawędzi (zbyt duża względem sąsiednich ścian?)");return {};}
        return maker.Shape();
    }
    BRepFilletAPI_MakeFillet maker(source);maker.Add(radius,selected);maker.Build();
    if(!maker.IsDone()||maker.Shape().IsNull()||!BRepCheck_Analyzer(maker.Shape()).IsValid()){kernelReason("OCCT nie może wykonać zaokrąglenia o tym promieniu na wskazanej krawędzi (zbyt duży względem sąsiednich ścian?)");return {};}
    return maker.Shape();
}

jdoubleArray edgeSegments(JNIEnv* env,const TopoDS_Shape& source,bool properties=false) {
    NCollection_IndexedMap<TopoDS_Shape,TopTools_ShapeMapHasher> edges;TopExp::MapShapes(source,TopAbs_EDGE,edges);
    NCollection_IndexedDataMap<TopoDS_Shape,NCollection_List<TopoDS_Shape>,TopTools_ShapeMapHasher> faces;
    if(!properties)TopExp::MapShapesAndAncestors(source,TopAbs_EDGE,TopAbs_FACE,faces);
    std::vector<double> values;
    for(int i=1;i<=edges.Extent();i++) {
        TopoDS_Edge edge=TopoDS::Edge(edges(i));if(BRep_Tool::Degenerated(edge))continue;
        if(!properties&&faces.Contains(edge)) {
            bool seam=false;
            for(NCollection_List<TopoDS_Shape>::Iterator it(faces.FindFromKey(edge));it.More();it.Next())
                if(BRep_Tool::IsClosed(edge,TopoDS::Face(it.Value()))){seam=true;break;}
            if(seam)continue;
        }
        BRepAdaptor_Curve curve(edge);auto signature=edgeSignature(edge);
        if(properties) {
            GProp_GProps length;BRepGProp::LinearProperties(edge,length);
            double radius=0;gp_Pnt center(0,0,0);int type=curve.GetType()==GeomAbs_Line?0:2;
            if(curve.GetType()==GeomAbs_Circle){radius=curve.Circle().Radius();center=curve.Circle().Location();type=1;}
            values.insert(values.end(),signature.begin(),signature.end());
            values.insert(values.end(),{length.Mass(),radius,center.X(),center.Y(),center.Z(),static_cast<double>(type),static_cast<double>(i)});
            continue;
        }
        const int count=curve.GetType()==GeomAbs_Line?1:128;
        gp_Pnt previous=curve.Value(curve.FirstParameter());
        for(int j=1;j<=count;j++) {
            gp_Pnt next=curve.Value(curve.FirstParameter()+(curve.LastParameter()-curve.FirstParameter())*j/count);
            values.insert(values.end(),signature.begin(),signature.end());
            values.insert(values.end(),{previous.X(),previous.Y(),previous.Z(),next.X(),next.Y(),next.Z(),static_cast<double>(i)});
            previous=next;
        }
    }
    jdoubleArray result=env->NewDoubleArray(values.size());
    if(result&&!values.empty())env->SetDoubleArrayRegion(result,0,values.size(),values.data());
    return result;
}

TopoDS_Shape profileFeature(const std::string& type, double x1, double y1,
                           double x2, double y2, double depth,
                           const gp_Pnt& origin, const gp_Dir& uDirection,
                           const gp_Dir& vDirection, bool cut,bool symmetric=false) {
    if (!std::isfinite(depth)||std::abs(depth)<=1.0e-6) return {};
    gp_Vec normal = gp_Vec(uDirection).Crossed(gp_Vec(vDirection));
    if (normal.SquareMagnitude() <= 1.0e-20) return {};
    normal.Normalize();
    if(depth<0)normal.Reverse();
    depth=std::abs(depth);
    gp_Pnt planeOrigin = origin;
    if (cut) planeOrigin.Translate(-normal * depth);
    if(symmetric)planeOrigin.Translate(normal*(cut?depth*.5:-depth*.5));

    auto point = [&](double u, double v) {
        gp_Pnt result = planeOrigin;
        result.Translate(gp_Vec(uDirection) * u + gp_Vec(vDirection) * v);
        return result;
    };

    TopoDS_Face face;
    if (type == "Okrąg") {
        const double radius = std::hypot(x2 - x1, y2 - y1);
        if (radius <= 1.0e-6) return {};
        const gp_Circ circle(gp_Ax2(point(x1, y1), gp_Dir(normal), uDirection), radius);
        const TopoDS_Edge edge = BRepBuilderAPI_MakeEdge(circle);
        const TopoDS_Wire wire = BRepBuilderAPI_MakeWire(edge);
        face = BRepBuilderAPI_MakeFace(wire);
    } else {
        const double xmin = std::min(x1, x2), xmax = std::max(x1, x2);
        const double ymin = std::min(y1, y2), ymax = std::max(y1, y2);
        if (xmax - xmin <= 1.0e-6 || ymax - ymin <= 1.0e-6) return {};
        BRepBuilderAPI_MakePolygon polygon;
        polygon.Add(point(xmin, ymin)); polygon.Add(point(xmax, ymin));
        polygon.Add(point(xmax, ymax)); polygon.Add(point(xmin, ymax)); polygon.Close();
        face = BRepBuilderAPI_MakeFace(polygon.Wire());
    }
    return BRepPrimAPI_MakePrism(face, normal * depth).Shape();
}

TopoDS_Shape applyProfileFeature(const TopoDS_Shape& base, const TopoDS_Shape& tool, bool cut) {
    if (base.IsNull()) { kernelReason("Brak bryły, na której można wykonać operację"); return {}; }
    if (tool.IsNull()) { kernelReason("Profil nie tworzy poprawnej bryły narzędzia (sprawdź, czy kontur jest zamknięty i nie przecina się)"); return {}; }
    TopoDS_Shape result;
    if (cut) {
        BRepAlgoAPI_Cut operation(base, tool);
        operation.Build();
        if (!operation.IsDone()) { kernelReason("OCCT nie wykonał odejmowania brył"); return {}; }
        operation.SimplifyResult(true,true);
        result = operation.Shape();
    } else {
        BRepAlgoAPI_Fuse operation(base, tool);
        operation.Build();
        if (!operation.IsDone()) { kernelReason("OCCT nie wykonał łączenia brył"); return {}; }
        operation.SimplifyResult(true,true);
        result = operation.Shape();
    }
    if (result.IsNull() || !BRepCheck_Analyzer(result).IsValid()) { kernelReason("Wynik operacji Boolean ma niepoprawną topologię"); return {}; }
    GProp_GProps before, after;
    BRepGProp::VolumeProperties(base, before);
    BRepGProp::VolumeProperties(result, after);
    const double oldVolume = std::abs(before.Mass());
    const double newVolume = std::abs(after.Mass());
    const double tolerance = std::max(1.0e-7, oldVolume * 1.0e-10);
    if (!std::isfinite(newVolume) || newVolume <= tolerance) { kernelReason("Operacja usunęłaby całą bryłę"); return {}; }
    if ((cut ? oldVolume - newVolume : newVolume - oldVolume) <= tolerance) {
        kernelReason(cut ? "Wycięcie nie przecina bryły – nie usuwa żadnego materiału (sprawdź kierunek i głębokość)"
                         : "Dodawany materiał w całości mieści się w istniejącej bryle");
        return {};
    }
    if (!cut && countShapes(result, TopAbs_SOLID) > countShapes(base, TopAbs_SOLID)) {
        kernelReason("Dodawany materiał nie styka się z bryłą – powstałaby osobna bryła (wybierz tryb „Nowa bryła”)");
        return {};
    }
    return result;
}

TopoDS_Shape polygonPrism(const double* points, int count, double depth,
        const gp_Pnt& origin,const gp_Vec& u,const gp_Vec& v,gp_Vec normal,bool feature,bool cut,bool symmetric=false) {
    if(count<3||count>256||!std::isfinite(depth)||std::abs(depth)<=1e-6||normal.SquareMagnitude()<1e-20) return {};
    if(depth<0)normal.Reverse();
    depth=std::abs(depth);
    normal.Normalize();gp_Pnt start=origin;
    if(feature&&cut) start.Translate(normal*(-depth));
    if(symmetric)start.Translate(normal*(feature&&cut?depth*.5:-depth*.5));
    BRepBuilderAPI_MakePolygon polygon;
    double area=0;
    for(int i=0;i<count;i++) {
        double x=points[2*i],y=points[2*i+1];int j=(i+1)%count;
        if(!std::isfinite(x)||!std::isfinite(y)||std::abs(x)>5000||std::abs(y)>5000) return {};
        area+=x*points[2*j+1]-y*points[2*j];
        polygon.Add(start.Translated(u*x+v*y));
    }
    if(std::abs(area)<1e-6) return {};
    polygon.Close();if(!polygon.IsDone()) return {};
    TopoDS_Face face=BRepBuilderAPI_MakeFace(polygon.Wire());
    if(!BRepCheck_Analyzer(face).IsValid()) return {};
    // Face normal follows wire winding; prism direction remains the work-plane normal.
    TopoDS_Shape result=BRepPrimAPI_MakePrism(face,normal*depth).Shape();
    if(!BRepCheck_Analyzer(result).IsValid()) return {};
    return result;
}

TopoDS_Shape curvedPrism(const double* segments,int count,double depth,const gp_Pnt& origin,
        const gp_Vec& u,const gp_Vec& v,gp_Vec normal,bool feature,bool cut,bool symmetric) {
    if(count<2||count>128||!std::isfinite(depth)||std::abs(depth)<1e-6||normal.SquareMagnitude()<1e-20)return {};
    if(depth<0)normal.Reverse();depth=std::abs(depth);normal.Normalize();gp_Pnt start=origin;
    if(feature&&cut)start.Translate(normal*(-depth));
    if(symmetric)start.Translate(normal*(feature&&cut?depth*.5:-depth*.5));
    BRepBuilderAPI_MakeWire wire;
    for(int i=0;i<count;i++) {
        const double* s=segments+i*7;const double* next=segments+((i+1)%count)*7;
        for(int j=1;j<7;j++)if(!std::isfinite(s[j])||std::abs(s[j])>5000)return {};
        if((s[0]!=0&&s[0]!=1)||std::hypot(s[5]-next[1],s[6]-next[2])>1e-6)return {};
        gp_Pnt a=start.Translated(u*s[1]+v*s[2]),b=start.Translated(u*s[5]+v*s[6]);
        if(a.Distance(b)<.001)return {};
        if(s[0]==1) {
            gp_Pnt m=start.Translated(u*s[3]+v*s[4]);GC_MakeArcOfCircle arc(a,m,b);
            if(!arc.IsDone())return {};
            BRepBuilderAPI_MakeEdge edge(arc.Value());if(!edge.IsDone())return {};wire.Add(edge.Edge());
        }else {BRepBuilderAPI_MakeEdge edge(a,b);if(!edge.IsDone())return {};wire.Add(edge.Edge());}
    }
    if(!wire.IsDone())return {};
    BRepBuilderAPI_MakeFace face(wire.Wire(),true);if(!face.IsDone()||!BRepCheck_Analyzer(face.Face()).IsValid())return {};
    TopoDS_Shape result=BRepPrimAPI_MakePrism(face.Face(),normal*depth).Shape();
    if(result.IsNull()||!BRepCheck_Analyzer(result).IsValid())return {};return result;
}

int revolveStride(const double* f,int available) {
    if(available<22||f[0]!=-3)return 0;
    double type=f[20];int tail=0;
    if(type>=1002&&type<=1128&&type==std::floor(type))tail=(static_cast<int>(type)-1000)*7;
    else if(type>=5&&type<=258&&type==std::floor(type))tail=(static_cast<int>(type)-2)*2;
    else if(type!=0&&type!=1)return 0;
    return available>=22+tail?22+tail:0;
}

TopoDS_Shape revolvedProfile(const double* f,int available) {
    int stride=revolveStride(f,available);if(!stride)return {};
    for(int i=0;i<stride;i++)if(!std::isfinite(f[i]))return {};
    for(int i=2;i<=5;i++)if(std::abs(f[i])>5000)return {};
    if((f[1]!=0&&f[1]!=1)||f[21]!=0||std::abs(f[6])<.1||std::abs(f[6])>360)return {};
    gp_Pnt origin(f[7],f[8],f[9]);gp_Vec u(f[10],f[11],f[12]),v(f[13],f[14],f[15]);
    if(std::abs(u.SquareMagnitude()-1)>1e-6||std::abs(v.SquareMagnitude()-1)>1e-6||std::abs(u.Dot(v))>1e-6)return {};
    for(int i=16;i<20;i++)if(std::abs(f[i])>5000)return {};
    if(std::hypot(f[18]-f[16],f[19]-f[17])<.01)return {};
    auto point=[&](double x,double y){return origin.Translated(u*x+v*y);};
    TopoDS_Wire wire;
    if(f[20]==1) {
        double radius=std::hypot(f[4]-f[2],f[5]-f[3]);if(radius<.01)return {};
        wire=BRepBuilderAPI_MakeWire(BRepBuilderAPI_MakeEdge(gp_Circ(gp_Ax2(point(f[2],f[3]),gp_Dir(u.Crossed(v)),gp_Dir(u)),radius)).Edge()).Wire();
    }else if(f[20]>=1002) {
        int count=static_cast<int>(f[20])-1000;BRepBuilderAPI_MakeWire builder;
        for(int i=0;i<count;i++) {
            const double* s=f+22+i*7;const double* next=f+22+((i+1)%count)*7;
            for(int j=1;j<7;j++)if(std::abs(s[j])>5000)return {};
            if((s[0]!=0&&s[0]!=1)||std::hypot(s[5]-next[1],s[6]-next[2])>1e-6)return {};
            gp_Pnt a=point(s[1],s[2]),b=point(s[5],s[6]);if(a.Distance(b)<.001)return {};
            if(s[0]==1){GC_MakeArcOfCircle arc(a,point(s[3],s[4]),b);if(!arc.IsDone())return {};builder.Add(BRepBuilderAPI_MakeEdge(arc.Value()).Edge());}
            else builder.Add(BRepBuilderAPI_MakeEdge(a,b).Edge());
        }
        if(!builder.IsDone())return {};wire=builder.Wire();
    }else {
        BRepBuilderAPI_MakePolygon polygon;
        if(f[20]==0) {
            if(std::abs(f[4]-f[2])<.01||std::abs(f[5]-f[3])<.01)return {};
            polygon.Add(point(f[2],f[3]));polygon.Add(point(f[4],f[3]));polygon.Add(point(f[4],f[5]));polygon.Add(point(f[2],f[5]));
        }else {
            int count=static_cast<int>(f[20])-2;
            for(int i=0;i<count;i++){if(std::abs(f[22+2*i])>5000||std::abs(f[23+2*i])>5000)return {};polygon.Add(point(f[22+2*i],f[23+2*i]));}
        }
        polygon.Close();if(!polygon.IsDone())return {};wire=polygon.Wire();
    }
    BRepBuilderAPI_MakeFace face(wire,true);if(!face.IsDone()||!BRepCheck_Analyzer(face.Face()).IsValid())return {};
    gp_Pnt axisOrigin=point(f[16],f[17]);gp_Vec direction=u*(f[18]-f[16])+v*(f[19]-f[17]);direction.Normalize();
    gp_Vec lateral=u.Crossed(v).Crossed(direction);lateral.Normalize();
    double low=1e100,high=-1e100;
    auto addSide=[&](const gp_Pnt& p){double d=gp_Vec(axisOrigin,p).Dot(lateral);low=std::min(low,d);high=std::max(high,d);};
    for(TopExp_Explorer it(wire,TopAbs_EDGE);it.More();it.Next()) {
        BRepAdaptor_Curve curve(TopoDS::Edge(it.Current()));double first=curve.FirstParameter(),last=curve.LastParameter();
        addSide(curve.Value(first));addSide(curve.Value(last));
        if(curve.GetType()==GeomAbs_Circle) {
            gp_Circ circle=curve.Circle();double a=gp_Vec(circle.Position().XDirection()).Dot(lateral),b=gp_Vec(circle.Position().YDirection()).Dot(lateral);
            double extremum=std::atan2(b,a);
            for(int k=-4;k<=4;k++){double t=extremum+k*M_PI;if(t>=first-1e-9&&t<=last+1e-9)addSide(curve.Value(t));}
        }else if(curve.GetType()!=GeomAbs_Line)return {};
    }
    if(low<-1e-7&&high>1e-7)return {};
    if(f[6]<0)direction.Reverse();
    BRepPrimAPI_MakeRevol maker(face.Face(),gp_Ax1(axisOrigin,gp_Dir(direction)),std::abs(f[6])*M_PI/180,true);
    if(!maker.IsDone()||maker.Shape().IsNull()||countShapes(maker.Shape(),TopAbs_SOLID)!=1||!BRepCheck_Analyzer(maker.Shape()).IsValid())return {};
    ShapeUpgrade_UnifySameDomain unify(maker.Shape(),true,true);unify.Build();
    TopoDS_Shape result=unify.Shape();if(result.IsNull()||!BRepCheck_Analyzer(result).IsValid())return {};
    GProp_GProps properties;BRepGProp::VolumeProperties(result,properties);
    if(!std::isfinite(properties.Mass())||std::abs(properties.Mass())<1e-7)return {};
    return result;
}

TopoDS_Shape referenceRoot(const double* b,int size) {
    if(size==22&&b[0]==-3&&b[20]==0&&b[1]==0)return revolvedProfile(b,size);
    if(size!=7||(b[0]!=0&&b[0]!=1)||b[5]!=std::floor(b[5])||b[5]<0||b[5]>6||(static_cast<int>(b[5])&3)>2)return {};
    int plane=static_cast<int>(b[5])&3;
    return extrudedProfile(b[0]==1?"Okrąg":"Prostokąt",b[1],b[2],b[3],b[4],b[6],plane==1?"XZ":plane==2?"YZ":"XY",(static_cast<int>(b[5])&4)!=0);
}

struct RevolutionBasis {
    gp_Pnt origin,axisOrigin;
    gp_Vec u,v,axis,radial;
    explicit RevolutionBasis(const double* b):origin(b[7],b[8],b[9]),u(b[10],b[11],b[12]),v(b[13],b[14],b[15]) {
        axisOrigin=origin.Translated(u*b[16]+v*b[17]);axis=u*(b[18]-b[16])+v*(b[19]-b[17]);axis.Normalize();
        radial=u.Crossed(v).Crossed(axis);radial.Normalize();
        gp_Pnt center=origin.Translated(u*((b[2]+b[4])*.5)+v*((b[3]+b[5])*.5));
        if(gp_Vec(axisOrigin,center).Dot(radial)<0)radial.Reverse();
    }
};

bool mapRootEdge(const double* reference,int oldSize,const double* current,int newSize,const double* signature,std::array<double,9>& mapped) {
    if(oldSize==7&&newSize==7&&reference[0]==current[0]&&(reference[0]==0||reference[0]==1)) {
        auto basis=[](const double* b,gp_Vec& u,gp_Vec& v,gp_Vec& n) {
            int plane=static_cast<int>(b[5])&3;u=plane==2?gp_Vec(0,1,0):gp_Vec(1,0,0);
            v=plane==0?gp_Vec(0,1,0):gp_Vec(0,0,1);n=plane==0?gp_Vec(0,0,1):plane==1?gp_Vec(0,1,0):gp_Vec(1,0,0);
        };
        gp_Vec oldU,oldV,oldN,newU,newV,newN;basis(reference,oldU,oldV,oldN);basis(current,newU,newV,newN);
        double oldStart=(static_cast<int>(reference[5])&4)?-reference[6]*.5:0;
        double newStart=(static_cast<int>(current[5])&4)?-current[6]*.5:0;
        if(std::abs(reference[6])<1e-6)return false;
        for(int i=0;i<3;i++) {
            gp_Vec p(signature[i*3],signature[i*3+1],signature[i*3+2]);
            double x=p.Dot(oldU),y=p.Dot(oldV),t=(p.Dot(oldN)-oldStart)/reference[6];
            if(reference[0]==0) {
                double dx=reference[3]-reference[1],dy=reference[4]-reference[2];if(std::abs(dx)<1e-6||std::abs(dy)<1e-6)return false;
                x=current[1]+(x-reference[1])*(current[3]-current[1])/dx;y=current[2]+(y-reference[2])*(current[4]-current[2])/dy;
            }else {
                double oldRadius=std::hypot(reference[3]-reference[1],reference[4]-reference[2]),newRadius=std::hypot(current[3]-current[1],current[4]-current[2]);
                if(oldRadius<1e-6||newRadius<1e-6)return false;
                x=current[1]+(x-reference[1])*newRadius/oldRadius;y=current[2]+(y-reference[2])*newRadius/oldRadius;
            }
            gp_Vec result=newU*x+newV*y+newN*(newStart+t*current[6]);mapped[i*3]=result.X();mapped[i*3+1]=result.Y();mapped[i*3+2]=result.Z();
        }
    }else if(oldSize==22&&newSize==22&&reference[0]==-3&&current[0]==-3&&reference[20]==0&&current[20]==0
            &&std::abs(reference[6]-current[6])<1e-9) {
        RevolutionBasis old(reference),now(current);
        double dx=reference[4]-reference[2],dy=reference[5]-reference[3];if(std::abs(dx)<1e-6||std::abs(dy)<1e-6)return false;
        for(int i=0;i<3;i++) {
            gp_Pnt point(signature[i*3],signature[i*3+1],signature[i*3+2]);gp_Vec fromAxis(old.axisOrigin,point);
            double axial=fromAxis.Dot(old.axis);gp_Vec radial=fromAxis-old.axis*axial;double radius=radial.Magnitude();
            double cosine=radius<1e-9?1:radial.Dot(old.radial)/radius,sine=radius<1e-9?0:radial.Dot(old.axis.Crossed(old.radial))/radius;
            gp_Pnt generator=old.axisOrigin.Translated(old.axis*axial+old.radial*radius);gp_Vec local(old.origin,generator);
            double x=current[2]+(local.Dot(old.u)-reference[2])*(current[4]-current[2])/dx;
            double y=current[3]+(local.Dot(old.v)-reference[3])*(current[5]-current[3])/dy;
            gp_Pnt newGenerator=now.origin.Translated(now.u*x+now.v*y);gp_Vec newLocal(now.axisOrigin,newGenerator);
            double newAxial=newLocal.Dot(now.axis),newRadius=newLocal.Dot(now.radial);
            if(newRadius<-1e-7||(radius<1e-9&&newRadius>1e-7))return false;
            gp_Pnt result=now.axisOrigin.Translated(now.axis*newAxial+now.radial*(newRadius*cosine)+now.axis.Crossed(now.radial)*(newRadius*sine));
            mapped[i*3]=result.X();mapped[i*3+1]=result.Y();mapped[i*3+2]=result.Z();
        }
    }else return false;
    for(double value:mapped)if(!std::isfinite(value))return false;return true;
}

jdoubleArray meshResult(JNIEnv* env, const TopoDS_Shape& shape) {
    if (shape.IsNull() || !BRepCheck_Analyzer(shape).IsValid()) return env->NewDoubleArray(0);
    const std::vector<double> mesh = tessellatedTriangles(shape);
    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(mesh.size()));
    if (result != nullptr && !mesh.empty()) {
        env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(mesh.size()), mesh.data());
    }
    return result;
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
    if (javaType == nullptr || javaPlane == nullptr || !std::isfinite(depth)||std::abs(depth)<=1e-6) return env->NewDoubleArray(0);
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

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeFilletPrism(
        JNIEnv* env, jclass, jdouble width, jdouble height, jdouble depth, jdouble radius) {
    if (width <= 0.0 || height <= 0.0 || depth <= 0.0 || radius <= 0.0) {
        return env->NewDoubleArray(0);
    }
    try {
        return meshResult(env, filletAllEdges(BRepPrimAPI_MakeBox(width, height, depth).Shape(), radius));
    } catch (const Standard_Failure&) {
        return env->NewDoubleArray(0);
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeFilletSketchExtrusion(
        JNIEnv* env, jclass, jstring javaType, jdouble x1, jdouble y1, jdouble x2,
        jdouble y2, jdouble depth, jstring javaPlane, jdouble radius) {
    if (javaType == nullptr || javaPlane == nullptr || !std::isfinite(depth)||std::abs(depth)<=1e-6 || radius <= 0.0) {
        return env->NewDoubleArray(0);
    }
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
        return meshResult(env, filletAllEdges(extrudedProfile(type, x1, y1, x2, y2, depth, plane), radius));
    } catch (const Standard_Failure&) {
        return env->NewDoubleArray(0);
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeApplyFaceFeature(
        JNIEnv* env, jclass, jdouble width, jdouble height, jdouble baseDepth,
        jdouble filletRadius, jstring javaType, jdouble x1, jdouble y1, jdouble x2,
        jdouble y2, jdouble featureDepth, jdouble ox, jdouble oy, jdouble oz,
        jdouble ux, jdouble uy, jdouble uz, jdouble vx, jdouble vy, jdouble vz,
        jstring javaOperation) {
    if (javaType == nullptr || javaOperation == nullptr || width <= 0.0 || height <= 0.0
            || baseDepth <= 0.0 || !std::isfinite(featureDepth)||std::abs(featureDepth)<=1e-6) return env->NewDoubleArray(0);
    const char* typeChars = env->GetStringUTFChars(javaType, nullptr);
    const char* operationChars = env->GetStringUTFChars(javaOperation, nullptr);
    if (typeChars == nullptr || operationChars == nullptr) {
        if (typeChars != nullptr) env->ReleaseStringUTFChars(javaType, typeChars);
        if (operationChars != nullptr) env->ReleaseStringUTFChars(javaOperation, operationChars);
        return env->NewDoubleArray(0);
    }
    const std::string type(typeChars), operation(operationChars);
    env->ReleaseStringUTFChars(javaType, typeChars);
    env->ReleaseStringUTFChars(javaOperation, operationChars);
    try {
        TopoDS_Shape base = BRepPrimAPI_MakeBox(width, height, baseDepth).Shape();
        if (filletRadius > 0.0) base = filletAllEdges(base, filletRadius);
        if (base.IsNull()) return env->NewDoubleArray(0);
        const gp_Vec uVector(ux, uy, uz), vVector(vx, vy, vz);
        if (uVector.SquareMagnitude() <= 1.0e-20 || vVector.SquareMagnitude() <= 1.0e-20) {
            return env->NewDoubleArray(0);
        }
        const bool cut = operation == "cut";
        const TopoDS_Shape tool = profileFeature(type, x1, y1, x2, y2, featureDepth,
            gp_Pnt(ox, oy, oz), gp_Dir(uVector), gp_Dir(vVector), cut);
        return meshResult(env, applyProfileFeature(base, tool, cut));
    } catch (const Standard_Failure&) {
        return env->NewDoubleArray(0);
    }
}


namespace {
TopoDS_Shape threadedHole(const TopoDS_Shape& shape,const double* f) {
    if(shape.IsNull()||(f[1]!=1&&f[1]!=-1)||f[8]<=0||f[9]<=f[8]||f[10]<.05||f[11]<.1||f[11]/f[10]>200)return {};
    if(f[12]!=0&&f[12]<f[11])return {};for(int i=13;i<16;i++)if(f[i]!=0)return {};
    gp_Vec axis(f[5],f[6],f[7]);if(std::abs(axis.Magnitude()-1)>1e-6)return {};
    const double pitch=f[10],depth=f[11],inner=f[8]-std::min(.05,pitch*.02),outer=f[9],height=outer-inner;
    if(inner<=0||height>pitch*.82)return {};
    const double halfWidth=height/std::sqrt(3.0),reference=(inner+outer)/2,start=-pitch,run=depth+2*pitch;
    Handle(Geom_CylindricalSurface) cylinder=new Geom_CylindricalSurface(gp_Ax3(),reference);
    Handle(Geom2d_Line) helix=new Geom2d_Line(gp_Pnt2d(0,start),gp_Dir2d(f[1]*2*M_PI,pitch));
    const double last=run/pitch*std::sqrt(4*M_PI*M_PI+pitch*pitch);
    BRepBuilderAPI_MakeEdge edge(helix,cylinder,0,last);if(!edge.IsDone())return {};
    TopoDS_Edge spine=edge.Edge();if(!BRepLib::BuildCurves3d(spine))return {};
    BRepBuilderAPI_MakeWire wire(spine);if(!wire.IsDone())return {};
    BRepBuilderAPI_MakePolygon profile;profile.Add(gp_Pnt(inner,0,start-halfWidth));profile.Add(gp_Pnt(outer,0,start));profile.Add(gp_Pnt(inner,0,start+halfWidth));profile.Close();
    if(!profile.IsDone())return {};
    BRepOffsetAPI_MakePipeShell pipe(wire.Wire());pipe.SetMode(true);pipe.Add(profile.Wire(),false,false);pipe.Build();
    if(!pipe.IsDone()||!pipe.MakeSolid())return {};
    BRepAlgoAPI_Common clipped(pipe.Shape(),BRepPrimAPI_MakeCylinder(outer+.1,depth).Shape());clipped.Build();if(!clipped.IsDone())return {};
    // Use one canonical sweep frame for either entry end. Reversing the input
    // axis must preserve the same axial interval and thread handedness; a frame
    // rotated by 180 degrees otherwise makes OCCT lose some full-depth cuts.
    gp_Pnt entry(f[2],f[3],f[4]);
    const double leading=std::abs(axis.Z())>1e-9?axis.Z():std::abs(axis.Y())>1e-9?axis.Y():axis.X();
    if(leading<0){entry.Translate(axis*depth);axis.Reverse();}
    gp_Trsf placement;placement.SetDisplacement(gp_Ax3(),gp_Ax3(entry,gp_Dir(axis)));
    TopoDS_Shape tool=BRepBuilderAPI_Transform(clipped.Shape(),placement,true).Shape();
    if(tool.IsNull()||!BRepCheck_Analyzer(tool).IsValid()){kernelReason("Narzędzie gwintu ma niepoprawną topologię");return {};}
    TopoDS_Shape result=applyProfileFeature(shape,tool,true);
    if(result.IsNull()||countShapes(result,TopAbs_SOLID)!=countShapes(shape,TopAbs_SOLID))return {};
    return result;
}
// Creator of each face (history tag) and the tags of the faces it was trimmed from. A Boolean or
// edge operation that only trims a face keeps its underlying surface, so a face that is not the
// same shape but lies on the identical surface handle inherits the ancestor's lineage.
struct FaceOrigin { TopoDS_Shape first; int second; std::vector<int> lineage; };
using FaceOrigins=std::vector<FaceOrigin>;
using BodyShapes=std::map<int,TopoDS_Shape>;

// Evaluation state at a top-level history record boundary ("before record at offset").
struct EvalState { int offset; int featureNumber; TopoDS_Shape shape; BodyShapes bodies; bool indexed; FaceOrigins origins; };
// Single-entry cache of the last evaluated history. B-Rep shapes are immutable inputs to the
// boolean/fillet algorithms, so a state whose record prefix is unchanged can be resumed from.
// All evaluations are serialised by g_evalMutex (preview, commit and UI threads).
struct EvalCache {
    bool valid=false;double width=0,height=0,depth=0,radius=0;std::vector<double> base;bool hasBase=false;
    std::string source;long long sourceSize=-1,sourceTime=-1;
    TopoDS_Shape root,initial;std::vector<double> rootBase;
    std::vector<double> values;std::vector<EvalState> states;
};
std::mutex g_evalMutex;
EvalCache g_evalCache;
void updateOrigins(const TopoDS_Shape& before,const TopoDS_Shape& after,int tag,FaceOrigins& origins) {
    static_cast<void>(before);FaceOrigins next;
    for(TopExp_Explorer e(after,TopAbs_FACE);e.More();e.Next()) {
        const FaceOrigin* same=nullptr;for(const auto& known:origins)if(e.Current().IsSame(known.first)){same=&known;break;}
        if(same){next.push_back(*same);continue;}
        FaceOrigin created{e.Current(),tag,{}};
        TopLoc_Location location;const Handle(Geom_Surface) surface=BRep_Tool::Surface(TopoDS::Face(e.Current()),location);
        if(!surface.IsNull())for(const auto& known:origins) {
            TopLoc_Location other;if(BRep_Tool::Surface(TopoDS::Face(known.first),other)!=surface||!other.IsEqual(location))continue;
            for(int ancestor:known.lineage)if(std::find(created.lineage.begin(),created.lineage.end(),ancestor)==created.lineage.end())created.lineage.push_back(ancestor);
            if(std::find(created.lineage.begin(),created.lineage.end(),known.second)==created.lineage.end())created.lineage.push_back(known.second);
        }
        next.push_back(std::move(created));
    }
    origins=std::move(next);
}
double shapeVolume(const TopoDS_Shape& shape){GProp_GProps props;BRepGProp::VolumeProperties(shape,props);return std::abs(props.Mass());}
// Planar face referenced by a shell record: same creating operation and outward normal;
// a stored interior point resolves several candidates. Never falls back to the nearest face.
TopoDS_Face shellFace(const TopoDS_Shape& solid,const double* ref,const FaceOrigins& origins,std::string& error,const char* subject="usuwanej przez powłokę") {
    const std::string lost=std::string("Nie odnaleziono ściany ")+subject+" – topologia zmieniła się. Wskaż ścianę ponownie";
    const gp_Pnt point(ref[0],ref[1],ref[2]);const gp_Dir normal(ref[3],ref[4],ref[5]);const int origin=static_cast<int>(ref[6]);
    std::vector<TopoDS_Face> candidates;
    for(TopExp_Explorer e(solid,TopAbs_FACE);e.More();e.Next()) {
        const TopoDS_Face face=TopoDS::Face(e.Current());BRepAdaptor_Surface surface(face);if(surface.GetType()!=GeomAbs_Plane)continue;
        gp_Dir n=surface.Plane().Axis().Direction();if(face.Orientation()==TopAbs_REVERSED)n.Reverse();
        if(n.Dot(normal)<1-1e-6)continue;
        bool created=false;
        for(const auto& known:origins)if(face.IsSame(known.first)){created=known.second==origin||std::find(known.lineage.begin(),known.lineage.end(),origin)!=known.lineage.end();break;}
        if(!created)continue;
        bool duplicate=false;for(const auto& c:candidates)if(c.IsSame(face))duplicate=true;if(!duplicate)candidates.push_back(face);
    }
    if(candidates.size()==1)return candidates.front();
    if(candidates.empty()){error=lost;return {};}
    std::vector<TopoDS_Face> inside;
    for(const auto& face:candidates) {
        BRepAdaptor_Surface surface(face);if(surface.Plane().Distance(point)>1e-3)continue;
        BRepClass_FaceClassifier classifier(face,point,1e-4);if(classifier.State()==TopAbs_IN||classifier.State()==TopAbs_ON)inside.push_back(face);
    }
    if(inside.size()==1)return inside.front();
    error=inside.empty()?lost:std::string("Ściana ")+subject+" jest niejednoznaczna – wskaż ją ponownie";return {};
}
TopoDS_Shape shellBody(const TopoDS_Shape& shape,const double* f,const FaceOrigins& origins) {
    if(shape.IsNull()){kernelReason("Brak bryły, na której można wykonać powłokę");return {};}
    if(countShapes(shape,TopAbs_SOLID)!=1){kernelReason("Powłoka wymaga dokładnie jednej bryły zamkniętej");return {};}
    TopExp_Explorer find(shape,TopAbs_SOLID);const TopoDS_Shape solid=find.Current();
    const int faces=static_cast<int>(f[1]);const double thickness=f[2];const bool outside=f[3]==1;
    NCollection_List<TopoDS_Shape> removed;
    for(int k=0;k<faces;k++) {
        std::string error;TopoDS_Face face=shellFace(solid,f+16+k*7,origins,error);if(face.IsNull()){kernelReason(error.c_str());return {};}
        for(const auto& existing:removed)if(existing.IsSame(face)){kernelReason("Ta sama ściana została wskazana dwukrotnie");return {};}
        removed.Append(face);
    }
    const double before=shapeVolume(solid);TopoDS_Shape result;
    if(faces>0) {
        BRepOffsetAPI_MakeThickSolid maker;maker.MakeThickSolidByJoin(solid,removed,outside?thickness:-thickness,1e-4,BRepOffset_Skin,false,false,GeomAbs_Arc,false);
        maker.Build();if(!maker.IsDone()){kernelReason("OCCT nie może wykonać powłoki o tej grubości (zmniejsz grubość lub zmień usuwane ściany)");return {};}
        result=maker.Shape();
    } else {
        BRepOffsetAPI_MakeOffsetShape offset;offset.PerformByJoin(solid,outside?thickness:-thickness,1e-4,BRepOffset_Skin,false,false,GeomAbs_Arc);
        offset.Build();if(!offset.IsDone()){kernelReason("OCCT nie może odsunąć ścian bryły o tę grubość");return {};}
        TopoDS_Shape other=offset.Shape();
        if(countShapes(other,TopAbs_SOLID)==0) {
            TopExp_Explorer shells(other,TopAbs_SHELL);if(!shells.More()){kernelReason("Odsunięcie nie utworzyło zamkniętej powłoki");return {};}
            BRepBuilderAPI_MakeSolid solidMaker(TopoDS::Shell(shells.Current()));if(!solidMaker.IsDone()){kernelReason("Odsunięcie nie utworzyło zamkniętej powłoki");return {};}
            other=solidMaker.Solid();BRepLib::OrientClosedSolid(TopoDS::Solid(other));
        }
        BRepAlgoAPI_Cut hollow(outside?other:solid,outside?solid:other);hollow.Build();
        if(!hollow.IsDone()){kernelReason("OCCT nie wydrążył bryły");return {};}
        result=hollow.Shape();
    }
    if(result.IsNull()||!BRepCheck_Analyzer(result).IsValid()||countShapes(result,TopAbs_SOLID)!=1){kernelReason("Powłoka dała niepoprawną bryłę (zmniejsz grubość ścianki)");return {};}
    const double after=shapeVolume(result);
    if(!std::isfinite(after)||after<=1e-9||(!outside&&after>=before-1e-9)){kernelReason("Powłoka nie zmieniła bryły – sprawdź grubość ścianki");return {};}
    return result;
}
// Parametric hole on a planar face. The stored point is projected onto the CURRENT plane of the
// referenced face (same creator and normal), so the hole follows parametric edits of that face.
TopoDS_Shape holeFeature(const TopoDS_Shape& shape,const double* f,const FaceOrigins& origins) {
    if(shape.IsNull()){kernelReason("Brak bryły, w której można wykonać otwór");return {};}
    const double ref[7]={f[2],f[3],f[4],f[5],f[6],f[7],f[8]};std::string error;
    TopoDS_Face face=shellFace(shape,ref,origins,error,"otworu");
    if(face.IsNull()){kernelReason(error.c_str());return {};}
    BRepAdaptor_Surface surface(face);const gp_Pln plane=surface.Plane();
    gp_Dir normal(f[5],f[6],f[7]);gp_Pnt point(f[2],f[3],f[4]);
    const double offset=gp_Vec(plane.Location(),point).Dot(gp_Vec(normal));point.Translate(gp_Vec(normal)*(-offset));
    BRepClass_FaceClassifier inside(face,point,1e-4);
    if(inside.State()!=TopAbs_IN&&inside.State()!=TopAbs_ON){kernelReason("Środek otworu leży poza wskazaną ścianą");return {};}
    Bnd_Box box;BRepBndLib::Add(shape,box);double xmin,ymin,zmin,xmax,ymax,zmax;box.Get(xmin,ymin,zmin,xmax,ymax,zmax);
    const double span=std::sqrt((xmax-xmin)*(xmax-xmin)+(ymax-ymin)*(ymax-ymin)+(zmax-zmin)*(zmax-zmin))+10;
    const double r=f[9]/2,margin=1e-3;const bool through=f[1]==1;const double depth=through?span*2:f[10];
    // Tool built directly in model space along the drilling axis (no transformed copy), with the
    // bore as the prism of a circular face like profile cuts, so threads cut into it reliably.
    const gp_Dir axis=normal.Reversed();const gp_Dir side=std::abs(axis.Z())<.9?gp_Dir(0,0,1).Crossed(axis):gp_Dir(1,0,0).Crossed(axis);
    auto at=[&](double t){return point.Translated(gp_Vec(axis)*t);};
    auto frameAt=[&](double t){return gp_Ax2(at(t),axis,side);};
    const TopoDS_Face disc=BRepBuilderAPI_MakeFace(BRepBuilderAPI_MakeWire(BRepBuilderAPI_MakeEdge(gp_Circ(frameAt(-margin),r))));
    TopoDS_Shape tool=BRepPrimAPI_MakePrism(disc,gp_Vec(axis)*(depth+margin)).Shape();
    auto fuse=[&](const TopoDS_Shape& part)->bool{BRepAlgoAPI_Fuse join(tool,part);join.Build();if(!join.IsDone())return false;tool=join.Shape();return true;};
    if(!through&&f[14]>0) {
        const double h=r/std::tan(f[14]*M_PI/360);
        if(!fuse(BRepPrimAPI_MakeCone(frameAt(depth),r,0,h).Shape())){kernelReason("Nie można utworzyć stożkowego dna otworu");return {};}
    }
    if(f[11]==1) {
        if(!fuse(BRepPrimAPI_MakeCylinder(frameAt(-margin),f[12]/2,f[13]+margin).Shape())){kernelReason("Nie można utworzyć pogłębienia walcowego");return {};}
    } else if(f[11]==2) {
        const double R=f[12]/2,h=(R-r)/std::tan(f[13]*M_PI/360);
        if(!fuse(BRepPrimAPI_MakeCone(frameAt(-margin),R+margin*(R-r)/h,r,h+margin).Shape())){kernelReason("Nie można utworzyć pogłębienia stożkowego");return {};}
    }
    if(tool.IsNull()||!BRepCheck_Analyzer(tool).IsValid()){kernelReason("Narzędzie otworu ma niepoprawną topologię");return {};}
    TopoDS_Shape result=applyProfileFeature(shape,tool,true);
    if(result.IsNull())return {};
    return result;
}
// Box covering the half-space behind (front=false) or in front of a plane, sized from the shape.
TopoDS_Shape halfSpace(const TopoDS_Shape& shape,const gp_Pnt& point,const gp_Dir& normal,bool front) {
    Bnd_Box box;BRepBndLib::Add(shape,box);double x0,y0,z0,x1,y1,z1;box.Get(x0,y0,z0,x1,y1,z1);
    const double size=2*std::sqrt((x1-x0)*(x1-x0)+(y1-y0)*(y1-y0)+(z1-z0)*(z1-z0))+gp_Vec(gp_Pnt(0,0,0),point).Magnitude()+100;
    TopoDS_Shape half=BRepPrimAPI_MakeBox(gp_Pnt(-size,-size,front?0:-size),gp_Pnt(size,size,front?size:0)).Shape();
    gp_Trsf placement;placement.SetDisplacement(gp_Ax3(),gp_Ax3(point,normal));
    return BRepBuilderAPI_Transform(half,placement,true).Shape();
}
// Draft (taper) of referenced planar faces about a neutral plane, pulled along its normal.
TopoDS_Shape draftFaces(const TopoDS_Shape& shape,const double* f,const FaceOrigins& origins) {
    if(shape.IsNull()){kernelReason("Brak bryły do pochylenia ścian");return {};}
    const gp_Dir pull(f[6],f[7],f[8]);const gp_Pln neutral(gp_Pnt(f[3],f[4],f[5]),pull);
    BRepOffsetAPI_DraftAngle draft(shape);
    for(int k=0;k<static_cast<int>(f[1]);k++) {
        std::string error;TopoDS_Face face=shellFace(shape,f+16+k*7,origins,error,"pochylanej");if(face.IsNull()){kernelReason(error.c_str());return {};}
        BRepAdaptor_Surface surface(face);
        if(std::abs(surface.Plane().Axis().Direction().Dot(pull))>1-1e-6){kernelReason("Ściana równoległa do płaszczyzny neutralnej nie może zostać pochylona");return {};}
        draft.Add(face,pull,f[2]*M_PI/180,neutral);
        if(!draft.AddDone()){kernelReason("OCCT nie może pochylić wskazanej ściany (sprawdź płaszczyznę neutralną i kąt)");return {};}
    }
    draft.Build();if(!draft.IsDone()){kernelReason("OCCT nie wykonał pochylenia ścian");return {};}
    TopoDS_Shape result=draft.Shape();
    if(result.IsNull()||!BRepCheck_Analyzer(result).IsValid()||countShapes(result,TopAbs_SOLID)!=countShapes(shape,TopAbs_SOLID)){kernelReason("Pochylenie dało niepoprawną bryłę");return {};}
    if(std::abs(shapeVolume(result)-shapeVolume(shape))<=1e-9*std::max(1.0,shapeVolume(shape))){kernelReason("Pochylenie nie zmieniło bryły");return {};}
    return result;
}
// Rigid move, rotation or uniform scale of the target body.
TopoDS_Shape transformBody(const TopoDS_Shape& shape,const double* f) {
    if(shape.IsNull()){kernelReason("Brak bryły do przekształcenia");return {};}
    gp_Trsf t;
    if(f[1]==1)t.SetTranslation(gp_Vec(f[2],f[3],f[4]));
    else if(f[1]==2)t.SetRotation(gp_Ax1(gp_Pnt(f[2],f[3],f[4]),gp_Dir(f[5],f[6],f[7])),f[8]*M_PI/180);
    else t.SetScale(gp_Pnt(f[2],f[3],f[4]),f[8]);
    TopoDS_Shape result=BRepBuilderAPI_Transform(shape,t,true).Shape();
    if(result.IsNull()||!BRepCheck_Analyzer(result).IsValid()){kernelReason("Przekształcenie dało niepoprawną bryłę");return {};}
    return result;
}
TopoDS_Shape evaluateRecords(TopoDS_Shape shape,const std::vector<double>& values,const std::vector<double>& rootBase,const TopoDS_Shape& rootShape,bool imported,BodyShapes* finalBodies,FaceOrigins* finalOrigins,EvalCache* cache=nullptr,const FaceOrigins* seed=nullptr,int seedTag=0) {
    const int count=static_cast<int>(values.size());int featureNumber=0;BodyShapes bodies;bool indexed=false;FaceOrigins origins;
    if(seed)origins=*seed;
    else {if(!rootShape.IsNull())updateOrigins({},rootShape,-1,origins);if(!shape.IsNull()&&!shape.IsSame(rootShape))updateOrigins(rootShape,shape,-2,origins);}
        jsize i=0;
        if(cache) {
            // Resume from the deepest cached boundary whose record prefix is unchanged.
            size_t common=0;const size_t limit=std::min(values.size(),cache->values.size());
            while(common<limit&&values[common]==cache->values[common])common++;
            const EvalState* resume=nullptr;
            for(const auto& state:cache->states)if(static_cast<size_t>(state.offset)<=common&&(!resume||state.offset>resume->offset))resume=&state;
            if(resume&&resume->offset>0){i=resume->offset;featureNumber=resume->featureNumber;shape=resume->shape;bodies=resume->bodies;indexed=resume->indexed;origins=resume->origins;}
            const int kept=i;std::vector<EvalState> retained;
            for(auto& state:cache->states)if(state.offset<kept)retained.push_back(std::move(state));
            cache->states=std::move(retained);cache->values=values;
        }
        for (; i < count;) {
            if(cache)cache->states.push_back(EvalState{static_cast<int>(i),featureNumber,shape,bodies,indexed,origins});
            if(count-i<16){kernelFailAt(featureNumber,"Niekompletny zapis operacji w historii");return {};}
            const double* f = values.data() + i;
            const TopoDS_Shape prior=shape;const int tag=seed?seedTag:featureNumber;featureNumber++;
            if(f[0]==-9) {
                if(f[1]<0||f[1]!=std::floor(f[1])||f[2]<1||f[2]!=std::floor(f[2])||f[3]<16||f[3]!=std::floor(f[3])||f[3]>count-i-16){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                for(int k=4;k<16;k++)if(f[k]!=0){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                if(!indexed){bodies[1]=shape;indexed=true;}
                int target=static_cast<int>(f[1]),output=static_cast<int>(f[2]),length=static_cast<int>(f[3]);
                if(target==0&&bodies.contains(output)){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                if(target!=0&&(!bodies.contains(target)||output!=target)){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                std::vector<double> record(f+16,f+16+length);TopoDS_Shape input=target==0?TopoDS_Shape():bodies[target];
                if(length==16&&record[0]==-18) {
                    // Split the target body by a plane: the side behind the normal stays, the other side becomes a new body.
                    const double* c=record.data();const int created=static_cast<int>(c[1]);
                    if(target==0||c[1]!=std::floor(c[1])||created<2||bodies.contains(created)||std::abs(c[5]*c[5]+c[6]*c[6]+c[7]*c[7]-1)>1e-6){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    for(int k=8;k<16;k++)if(c[k]!=0){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    const TopoDS_Shape a=bodies[target];const gp_Pnt point(c[2],c[3],c[4]);const gp_Dir normal(c[5],c[6],c[7]);
                    TopoDS_Shape sides[2];
                    for(int side=0;side<2;side++){BRepAlgoAPI_Common op(a,halfSpace(a,point,normal,side==1));op.Build();if(!op.IsDone()){kernelFailAt(tag,"OCCT nie podzielił bryły");return {};}op.SimplifyResult(true,true);sides[side]=op.Shape();}
                    const double total=shapeVolume(a),eps=std::max(1e-7,total*1e-9);
                    for(const auto& part:sides)if(part.IsNull()||countShapes(part,TopAbs_SOLID)==0||shapeVolume(part)<=eps||!BRepCheck_Analyzer(part).IsValid()){kernelFailAt(tag,"Płaszczyzna podziału nie przecina bryły");return {};}
                    bodies[target]=sides[0];bodies[created]=sides[1];
                    BRep_Builder builder;TopoDS_Compound compound;builder.MakeCompound(compound);for(const auto& body:bodies)builder.Add(compound,body.second);shape=compound;
                    updateOrigins(prior,shape,tag,origins);i+=16+length;continue;
                }
                if(length==16&&record[0]==-15) {
                    // Body Boolean: the tool body is another document body, consumed unless kept.
                    const double* c=record.data();const int tool=static_cast<int>(c[2]);
                    if(target==0||(c[1]!=0&&c[1]!=1&&c[1]!=2)||c[2]!=std::floor(c[2])||(c[3]!=0&&c[3]!=1)){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    for(int k=4;k<16;k++)if(c[k]!=0){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    if(tool==target||!bodies.contains(tool)){kernelFailAt(tag,"Bryła narzędzia nie istnieje albo jest bryłą docelową");return {};}
                    const TopoDS_Shape a=bodies[target],b=bodies[tool];TopoDS_Shape combined;
                    if(c[1]==0){BRepAlgoAPI_Fuse op(a,b);op.Build();if(op.IsDone()){op.SimplifyResult(true,true);combined=op.Shape();}}
                    else if(c[1]==1){BRepAlgoAPI_Cut op(a,b);op.Build();if(op.IsDone()){op.SimplifyResult(true,true);combined=op.Shape();}}
                    else {BRepAlgoAPI_Common op(a,b);op.Build();if(op.IsDone()){op.SimplifyResult(true,true);combined=op.Shape();}}
                    if(combined.IsNull()||!BRepCheck_Analyzer(combined).IsValid()){kernelFailAt(tag,"OCCT nie wykonał operacji na bryłach");return {};}
                    const double va=shapeVolume(a),vb=shapeVolume(b),vr=shapeVolume(combined),eps=std::max(1e-7,(va+vb)*1e-10);
                    if(countShapes(combined,TopAbs_SOLID)==0||vr<=eps){kernelFailAt(tag,c[1]==2?"Bryły nie mają części wspólnej":"Operacja usunęłaby całą bryłę");return {};}
                    if(c[1]==1&&va-vr<=eps){kernelFailAt(tag,"Bryła narzędzia nie przecina bryły docelowej – nic nie odjęto");return {};}
                    if(c[1]==0&&vr<va+vb-eps&&vr<=va+eps){kernelFailAt(tag,"Bryła narzędzia w całości mieści się w bryle docelowej");return {};}
                    bodies[output]=combined;if(c[3]==0)bodies.erase(tool);
                    BRep_Builder builder;TopoDS_Compound compound;builder.MakeCompound(compound);for(const auto& body:bodies)builder.Add(compound,body.second);shape=compound;
                    updateOrigins(prior,shape,tag,origins);i+=16+length;continue;
                }
                TopoDS_Shape result=evaluateRecords(input,record,rootBase,rootShape,imported,nullptr,nullptr,nullptr,&origins,tag);
                if(result.IsNull()){kernelFailAt(tag,"Operacja na wskazanej bryle nie powiodła się");return {};}bodies[output]=result;
                BRep_Builder builder;TopoDS_Compound compound;builder.MakeCompound(compound);for(const auto& body:bodies)builder.Add(compound,body.second);shape=compound;
                updateOrigins(prior,shape,tag,origins);i+=16+length;continue;
            }
            if(indexed){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
            if(f[0]==-16) {
                if(f[1]!=std::floor(f[1])||f[1]<1||f[1]>16||f[2]==0||std::abs(f[2])>60||std::abs(f[6]*f[6]+f[7]*f[7]+f[8]*f[8]-1)>1e-6){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                for(int k=9;k<16;k++)if(f[k]!=0){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                const int stride=16+static_cast<int>(f[1])*7;if(count-i<stride){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                shape=draftFaces(shape,f,origins);if(shape.IsNull()){kernelFailAt(tag,"Pochylenie ścian nie powiodło się");return {};}
                updateOrigins(prior,shape,tag,origins);i+=stride;continue;
            }
            if(f[0]==-17) {
                const bool ok=(f[1]==1&&std::abs(f[2])+std::abs(f[3])+std::abs(f[4])>1e-9)||(f[1]==2&&std::abs(f[5]*f[5]+f[6]*f[6]+f[7]*f[7]-1)<1e-6&&f[8]!=0&&std::abs(f[8])<=360)||(f[1]==3&&f[8]>=.001&&f[8]<=1000&&f[8]!=1);
                if(!ok){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                for(int k=9;k<16;k++)if(f[k]!=0){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                shape=transformBody(shape,f);if(shape.IsNull()){kernelFailAt(tag,"Przekształcenie bryły nie powiodło się");return {};}
                updateOrigins(prior,shape,tag,origins);i+=16;continue;
            }
            if(f[0]==-14) {
                if((f[1]!=0&&f[1]!=1)||f[9]<.05||f[9]>2000||(f[1]==0&&(f[10]<.01||f[10]>5000))||(f[11]!=0&&f[11]!=1&&f[11]!=2)||f[15]!=0||(f[14]!=0&&(f[14]<30||f[14]>179))){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                if(f[11]!=0&&(f[12]<=f[9]||(f[11]==1&&(f[13]<.01||(f[1]==0&&f[13]>=f[10])))||(f[11]==2&&(f[13]<30||f[13]>179)))){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                shape=holeFeature(shape,f,origins);if(shape.IsNull()){kernelFailAt(tag,"Otwór nie powiódł się");return {};}
                updateOrigins(prior,shape,tag,origins);i+=16;continue;
            }
            if(f[0]==-12) {
                if(f[1]!=std::floor(f[1])||f[1]<0||f[1]>16||f[2]<.01||f[2]>1000||(f[3]!=0&&f[3]!=1)){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                for(int k=4;k<16;k++)if(f[k]!=0){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                const int stride=16+static_cast<int>(f[1])*7;if(count-i<stride){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                shape=shellBody(shape,f,origins);if(shape.IsNull()){kernelFailAt(tag,"Powłoka nie powiodła się");return {};}
                updateOrigins(prior,shape,tag,origins);i+=stride;continue;
            }
            if(f[0]==-8){shape=threadedHole(shape,f);if(shape.IsNull()){kernelFailAt(tag,"Nie można wyciąć gwintu w tym otworze (sprawdź średnicę, skok i głębokość)");return {};}updateOrigins(prior,shape,tag,origins);i+=16;continue;}
            if(f[0]==-6||f[0]==-7) {
                if(f[1]!=std::floor(f[1])||f[1]<1||f[1]>256||f[11]<.1||f[11]>1000){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                for(int k=2;k<16;k++)if(k!=11&&f[k]!=0){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                const bool chamfer=f[0]==-7;int stride=16;
                std::vector<TopoDS_Edge> selected;
                for(int k=0;k<static_cast<int>(f[1]);k++) {
                    if(count-i-stride<1){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    double size=f[stride++];if(size!=16&&size!=24&&size!=39){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    int length=static_cast<int>(size);if(count-i-stride<length){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    const double* member=f+stride;stride+=length;
                    if(member[0]!=(chamfer?-2:-1)&&member[0]!=(chamfer?-5:-4)){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    if(member[1]!=0||member[11]!=f[11]||member[12]!=0||member[13]!=0||member[14]!=0||member[15]!=0){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    std::array<double,9> mapped;const double* signature=member+2;TopoDS_Edge expected;
                    if(member[0]==-4||member[0]==-5) {
                        if(imported||length<24||(member[16]!=7&&member[16]!=22)||length!=17+member[16]){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                        int baseSize=static_cast<int>(member[16]);TopoDS_Shape before=referenceRoot(member+17,baseSize);
                        if(before.IsNull()||edgeForSignature(before,member+2).IsNull()){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                        if(!mapRootEdge(member+17,baseSize,rootBase.data(),static_cast<int>(rootBase.size()),member+2,mapped)){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                        expected=edgeForSignature(rootShape,mapped.data());if(expected.IsNull()){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                        signature=mapped.data();
                    } else if(length!=16){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    TopoDS_Edge edge=edgeForSignature(shape,signature);
                    if(edge.IsNull()||(!expected.IsNull()&&!sameEdgeGeometry(edge,expected))){kernelFailAt(tag,"Nie odnaleziono jednoznacznie jednej ze wskazanych krawędzi – topologia zmieniła się po edycji");return {};}
                    for(const auto& previous:selected)if(edge.IsSame(previous)){kernelFailAt(tag,"Ta sama krawędź została wskazana dwukrotnie");return {};}
                    selected.push_back(edge);
                }
                if(chamfer) {
                    BRepFilletAPI_MakeChamfer maker(shape);
                    for(const auto& edge:selected)if(maker.Contour(edge)==0)maker.Add(f[11],edge);
                    maker.Build();if(!maker.IsDone()){kernelFailAt(tag,"OCCT nie może wykonać fazy o tej wielkości na wskazanych krawędziach");return {};}shape=maker.Shape();
                } else {
                    BRepFilletAPI_MakeFillet maker(shape);
                    for(const auto& edge:selected)if(maker.Contour(edge)==0)maker.Add(f[11],edge);
                    maker.Build();if(!maker.IsDone()){kernelFailAt(tag,"OCCT nie może wykonać zaokrąglenia o tym promieniu na wskazanych krawędziach");return {};}shape=maker.Shape();
                }
                if(shape.IsNull()||countShapes(shape,TopAbs_SOLID)==0||!BRepCheck_Analyzer(shape).IsValid()){kernelFailAt(tag,"Operacja na krawędziach dała niepoprawną bryłę");return {};}
                updateOrigins(prior,shape,tag,origins);i+=stride;continue;
            }
            if(f[0]==-3) {
                int stride=revolveStride(f,count-i);if(!stride){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                shape=applyProfileFeature(shape,revolvedProfile(f,stride),f[1]==1);
                if(shape.IsNull()){kernelFailAt(tag,"Obrót profilu nie powiódł się (profil nie może przecinać osi)");return {};}updateOrigins(prior,shape,tag,origins);i+=stride;continue;
            }
            if(f[0]==-1||f[0]==-2||f[0]==-4||f[0]==-5) {
                if(f[1]!=0||f[12]!=0||f[13]!=0||f[14]!=0||f[15]!=0){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                int stride=16;std::array<double,9> mapped;const double* signature=f+2;TopoDS_Edge expected;
                if(f[0]==-4||f[0]==-5) {
                    if(imported||count-i<24||(f[16]!=7&&f[16]!=22)){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    int size=static_cast<int>(f[16]);stride=17+size;if(count-i<stride){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    TopoDS_Shape before=referenceRoot(f+17,size);if(before.IsNull()||edgeForSignature(before,f+2).IsNull()){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    if(!mapRootEdge(f+17,size,rootBase.data(),static_cast<int>(rootBase.size()),f+2,mapped)){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    expected=edgeForSignature(rootShape,mapped.data());if(expected.IsNull()){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
                    signature=mapped.data();
                }
                shape=filletSelectedEdge(shape,signature,f[11],f[0]==-2||f[0]==-5,expected.IsNull()?nullptr:&expected);
                if(shape.IsNull()){kernelFailAt(tag,"Operacja na krawędzi nie powiodła się");return {};}
                updateOrigins(prior,shape,tag,origins);i+=stride;continue;
            }
            int vertices=0,curves=0;
            if(f[0]>=1002&&f[0]<=1128&&f[0]==std::floor(f[0]))curves=static_cast<int>(f[0])-1000;
            else if(f[0]>=5&&f[0]<=258&&f[0]==std::floor(f[0])) vertices=static_cast<int>(f[0])-2;
            else if(f[0]!=0&&f[0]!=1) {kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
            int stride=16+vertices*2+curves*7;if(count-i<stride) {kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
            const gp_Vec u(f[10], f[11], f[12]), v(f[13], f[14], f[15]);
            if (u.SquareMagnitude() < 1.0e-20 || v.SquareMagnitude() < 1.0e-20) {
                {kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
            }
            if(f[1]!=std::floor(f[1])||!(f[1]==0||f[1]==1||f[1]==2||f[1]==3||f[1]==4||f[1]==6||f[1]==8||f[1]==10)){kernelFailAt(featureNumber-1,"Niepoprawne parametry operacji w historii");return {};}
            const bool cut=(static_cast<int>(f[1])&1)!=0,symmetric=(static_cast<int>(f[1])&2)!=0;
            const TopoDS_Shape tool = curves>0?curvedPrism(f+16,curves,f[6],gp_Pnt(f[7],f[8],f[9]),u,v,u.Crossed(v),true,cut,symmetric):vertices>0 ? polygonPrism(f+16,vertices,f[6],gp_Pnt(f[7],f[8],f[9]),
                u,v,u.Crossed(v),true,cut,symmetric) : profileFeature(f[0] == 1.0 ? "Okrąg" : "Prostokąt",
                f[2], f[3], f[4], f[5], f[6], gp_Pnt(f[7], f[8], f[9]),
                gp_Dir(u), gp_Dir(v), cut,symmetric);
            if(tool.IsNull()){kernelFailAt(tag,"Profil nie tworzy poprawnej bryły narzędzia (sprawdź, czy kontur jest zamknięty i nie przecina się)");return {};}
            if((static_cast<int>(f[1])&4)!=0) {
                BRep_Builder builder;TopoDS_Compound compound;builder.MakeCompound(compound);
                if(shape.IsNull())shape=tool;else {builder.Add(compound,shape);builder.Add(compound,tool);shape=compound;}
                if(!BRepCheck_Analyzer(shape).IsValid()){kernelFailAt(tag,"Nowa bryła ma niepoprawną topologię");return {};}
            } else if((static_cast<int>(f[1])&8)!=0) {
                BRepAlgoAPI_Common operation(shape,tool);operation.Build();
                if(!operation.IsDone()){kernelFailAt(tag,"OCCT nie wyznaczył części wspólnej");return {};}
                TopoDS_Shape result=operation.Shape();
                if(result.IsNull()||!BRepCheck_Analyzer(result).IsValid()||countShapes(result,TopAbs_SOLID)==0){kernelFailAt(tag,"Bryły nie mają części wspólnej");return {};}
                shape=result;
            } else shape = applyProfileFeature(shape, tool, cut);
            if (shape.IsNull()){kernelFailAt(tag,cut?"Wycięcie nie powiodło się":"Dodanie materiału nie powiodło się");return {};}
            updateOrigins(prior,shape,tag,origins);i+=stride;
        }

    if(cache)cache->states.push_back(EvalState{count,featureNumber,shape,bodies,indexed,origins});
    if(finalBodies){if(!indexed)bodies[1]=shape;*finalBodies=std::move(bodies);}
    if(finalOrigins)*finalOrigins=std::move(origins);
    return shape;
}
}

extern "C" JNIEXPORT void JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeSetSectionPlane(JNIEnv* env,jclass,jdoubleArray plane) {
    g_sectionActive=false;if(plane==nullptr||env->GetArrayLength(plane)!=6)return;
    env->GetDoubleArrayRegion(plane,0,6,g_sectionPlane.data());
    for(double v:g_sectionPlane)if(!std::isfinite(v))return;g_sectionActive=true;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeEvaluateFeatures(
        JNIEnv* env, jclass, jdouble width, jdouble height, jdouble depth,
        jdouble radius, jdoubleArray javaBase, jdoubleArray javaFeatures, jstring javaExportPath,
        jstring javaSourceStep, jint queryMode) {
    kernelReset();
    if((queryMode<0||queryMode>3)&&queryMode!=6&&queryMode!=8&&queryMode!=9)return env->NewDoubleArray(0);
    if(queryMode==9&&!g_sectionActive){kernelFailAt(-2,"Brak płaszczyzny przekroju");return env->NewDoubleArray(0);}
    if (javaFeatures == nullptr || width <= 0 || height <= 0 || depth <= 0) {
        kernelFailAt(-1,"Niepoprawne wymiary bryły bazowej");
        return env->NewDoubleArray(0);
    }
    const jsize count = env->GetArrayLength(javaFeatures);
    std::vector<double> values(count);
    env->GetDoubleArrayRegion(javaFeatures, 0, count, values.data());
    for(double value:values) if(!std::isfinite(value)) return env->NewDoubleArray(0);
    std::string exportPath;
    if (javaExportPath != nullptr) {
        const char* chars = env->GetStringUTFChars(javaExportPath, nullptr);
        if (chars == nullptr) return env->NewDoubleArray(0);
        exportPath = chars;
        env->ReleaseStringUTFChars(javaExportPath, chars);
    }
    try {
        std::unique_lock<std::mutex> evalLock(g_evalMutex);
        std::vector<double> keyBase;bool keyHasBase=false;std::string keySource;long long keySize=-1,keyTime=-1;
        if(javaSourceStep!=nullptr) {
            const char* chars=env->GetStringUTFChars(javaSourceStep,nullptr);if(chars==nullptr)return env->NewDoubleArray(0);
            keySource=chars;env->ReleaseStringUTFChars(javaSourceStep,chars);
            struct stat info{};if(stat(keySource.c_str(),&info)==0){keySize=static_cast<long long>(info.st_size);keyTime=static_cast<long long>(info.st_mtime);}
        }else if(javaBase!=nullptr) {
            keyHasBase=true;keyBase.resize(env->GetArrayLength(javaBase));env->GetDoubleArrayRegion(javaBase,0,static_cast<jsize>(keyBase.size()),keyBase.data());
        }
        EvalCache& cache=g_evalCache;
        const bool hit=cache.valid&&cache.width==width&&cache.height==height&&cache.depth==depth&&cache.radius==radius
            &&cache.hasBase==keyHasBase&&cache.base==keyBase&&cache.source==keySource&&cache.sourceSize==keySize&&cache.sourceTime==keyTime;
        TopoDS_Shape shape,rootShape;std::vector<double> rootBase;
        if(hit){shape=cache.initial;rootShape=cache.root;rootBase=cache.rootBase;}
        else {
        cache=EvalCache();
        shape = BRepPrimAPI_MakeBox(width, height, depth).Shape();
        rootBase={0,0,0,width,height,0,depth};
        if (javaSourceStep != nullptr) {
            const char* chars = env->GetStringUTFChars(javaSourceStep, nullptr);
            if (chars == nullptr) return env->NewDoubleArray(0);
            const std::string sourcePath(chars);
            env->ReleaseStringUTFChars(javaSourceStep, chars);
            STEPControl_Reader reader;
            if (reader.ReadFile(sourcePath.c_str()) != IFSelect_RetDone
                    || reader.TransferRoots() <= 0) { kernelFailAt(-1,"Nie można odczytać źródłowego pliku STEP"); return env->NewDoubleArray(0); }
            shape = reader.OneShape();
            if (shape.IsNull() || countShapes(shape, TopAbs_SOLID) == 0
                    || !BRepCheck_Analyzer(shape).IsValid()) { kernelFailAt(-1,"Importowany STEP nie zawiera poprawnej bryły"); return env->NewDoubleArray(0); }
        } else if (javaBase != nullptr) {
            const int size=env->GetArrayLength(javaBase);
            if(size<7) return env->NewDoubleArray(0);
            std::vector<double> b(size);env->GetDoubleArrayRegion(javaBase,0,size,b.data());
            for(double value:b) if(!std::isfinite(value)) return env->NewDoubleArray(0);
            rootBase=b;
            if(b[0]==-10) {
                if(size<23||std::abs(b[1])<.1||b[2]<0||b[2]>5000||std::abs(std::abs(b[6])-std::abs(b[1])-b[2])>1e-6)return env->NewDoubleArray(0);
                std::vector<double> tool(b.begin()+7,b.end());if(tool[1]!=4&&tool[1]!=6)return env->NewDoubleArray(0);
                shape=evaluateRecords({},tool,b,{},false,nullptr,nullptr);
            }else if(b[0]==-3) {
                if(revolveStride(b.data(),size)!=size||b[1]!=0)return env->NewDoubleArray(0);
                shape=revolvedProfile(b.data(),size);
            }else {
            if(b[5]!=std::floor(b[5])||b[5]<0||b[5]>6||(static_cast<int>(b[5])&3)>2)return env->NewDoubleArray(0);
            const int plane=static_cast<int>(b[5])&3;
            const bool symmetric=(static_cast<int>(b[5])&4)!=0;
            if(b[0]>=1002&&b[0]<=1128&&b[0]==std::floor(b[0])) {
                int n=static_cast<int>(b[0])-1000;if(size!=7+n*7)return env->NewDoubleArray(0);
                gp_Vec u(1,0,0),v(0,1,0),normal(0,0,1);
                if(plane==1){v=gp_Vec(0,0,1);normal=gp_Vec(0,1,0);}
                else if(plane==2){u=gp_Vec(0,1,0);v=gp_Vec(0,0,1);normal=gp_Vec(1,0,0);}
                shape=curvedPrism(b.data()+7,n,b[6],gp_Pnt(0,0,0),u,v,normal,false,false,symmetric);
            }else if(b[0]>=5 && b[0]<=258 && b[0]==std::floor(b[0])) {
                int n=static_cast<int>(b[0])-2;if(size!=7+n*2) return env->NewDoubleArray(0);
                gp_Vec u(1,0,0),v(0,1,0),normal(0,0,1);
                if(plane==1) {v=gp_Vec(0,0,1);normal=gp_Vec(0,1,0);}
                else if(plane==2) {u=gp_Vec(0,1,0);v=gp_Vec(0,0,1);normal=gp_Vec(1,0,0);}
                shape=polygonPrism(b.data()+7,n,b[6],gp_Pnt(0,0,0),u,v,normal,false,false,symmetric);
            } else {
                if(size!=7 || (b[0]!=0&&b[0]!=1)) return env->NewDoubleArray(0);
                shape = extrudedProfile(b[0] == 1 ? "Okrąg" : "Prostokąt", b[1], b[2],
                    b[3], b[4], b[6], plane == 1 ? "XZ" : plane == 2 ? "YZ" : "XY",symmetric);
            }
        }
            }
        if(shape.IsNull()) { kernelFailAt(-1,"Pierwsza bryła (bazowa) nie może zostać utworzona z tego profilu"); return env->NewDoubleArray(0); }
        rootShape=shape;
        if (radius > 0) { shape = filletAllEdges(shape, radius); if(shape.IsNull()) { kernelFailAt(-1,"Zaokrąglenie wszystkich krawędzi bryły bazowej nie powiodło się"); return env->NewDoubleArray(0); } }
        cache.valid=true;cache.width=width;cache.height=height;cache.depth=depth;cache.radius=radius;cache.hasBase=keyHasBase;cache.base=keyBase;
        cache.source=keySource;cache.sourceSize=keySize;cache.sourceTime=keyTime;cache.root=rootShape;cache.initial=shape;cache.rootBase=rootBase;
        }
        BodyShapes bodies;FaceOrigins origins;
        shape=evaluateRecords(shape,values,rootBase,rootShape,javaSourceStep!=nullptr,&bodies,&origins,&cache);
        if(shape.IsNull()){kernelReason("Przebudowa modelu nie powiodła się");return env->NewDoubleArray(0);}
        if (!exportPath.empty()) {
            STEPControl_Writer writer;
            if (writer.Transfer(shape, STEPControl_AsIs) != IFSelect_RetDone
                    || writer.Write(exportPath.c_str()) != IFSelect_RetDone) {
                kernelFailAt(-2,"Zapis pliku STEP nie powiódł się");
                return env->NewDoubleArray(0);
            }
            STEPControl_Reader verifier;
            if (verifier.ReadFile(exportPath.c_str()) != IFSelect_RetDone
                    || verifier.TransferRoots() <= 0
                    || !BRepCheck_Analyzer(verifier.OneShape()).IsValid()) {
                kernelFailAt(-2,"Kontrolny odczyt zapisanego pliku STEP nie powiódł się");
                return env->NewDoubleArray(0);
            }
        }
        if(queryMode==9) {
            // Display-only section: each body is intersected with the half-space behind the plane,
            // so cut faces are real capped B-Rep faces and body identity is kept per face.
            gp_Vec n(g_sectionPlane[3],g_sectionPlane[4],g_sectionPlane[5]);if(n.Magnitude()<1e-9){kernelFailAt(-2,"Niepoprawna płaszczyzna przekroju");return env->NewDoubleArray(0);}
            const TopoDS_Shape half=halfSpace(shape,gp_Pnt(g_sectionPlane[0],g_sectionPlane[1],g_sectionPlane[2]),gp_Dir(n),false);
            if(bodies.empty())bodies[1]=shape;
            BRep_Builder builder;TopoDS_Compound compound;builder.MakeCompound(compound);std::vector<std::pair<TopoDS_Shape,int>> parts;
            for(const auto& body:bodies) {
                BRepAlgoAPI_Common cut(body.second,half);cut.Build();if(!cut.IsDone()){kernelFailAt(-2,"OCCT nie wyznaczył przekroju");return env->NewDoubleArray(0);}
                if(countShapes(cut.Shape(),TopAbs_FACE)==0)continue;builder.Add(compound,cut.Shape());parts.emplace_back(cut.Shape(),body.first);
            }
            std::vector<double> mesh=tessellatedTriangles(compound),info;int face=0;
            for(TopExp_Explorer e(compound,TopAbs_FACE);e.More();e.Next()) {
                int bodyId=1;face++;for(const auto& part:parts){bool found=false;for(TopExp_Explorer f(part.first,TopAbs_FACE);f.More();f.Next())if(e.Current().IsSame(f.Current())){found=true;break;}if(found){bodyId=part.second;break;}}
                info.push_back(face);info.push_back(bodyId);info.push_back(-1);
            }
            std::vector<double> combined;combined.push_back(static_cast<double>(mesh.size()));combined.insert(combined.end(),mesh.begin(),mesh.end());combined.insert(combined.end(),info.begin(),info.end());
            jdoubleArray result=env->NewDoubleArray(static_cast<jsize>(combined.size()));if(result)env->SetDoubleArrayRegion(result,0,static_cast<jsize>(combined.size()),combined.data());return result;
        }
        if(queryMode==6||queryMode==8) {
            std::vector<double> info,cylinders;int face=0;
            for(TopExp_Explorer e(shape,TopAbs_FACE);e.More();e.Next()) {
                int bodyId=0,origin=-1;face++;
                for(const auto& body:bodies){for(TopExp_Explorer f(body.second,TopAbs_FACE);f.More();f.Next())if(e.Current().IsSame(f.Current())){bodyId=body.first;break;}if(bodyId)break;}
                for(const auto& known:origins)if(e.Current().IsSame(known.first)){origin=known.second;break;}
                info.push_back(face);info.push_back(bodyId);info.push_back(origin);
                BRepAdaptor_Surface surface(TopoDS::Face(e.Current()),true);
                if(surface.GetType()==GeomAbs_Cylinder) {
                    double umin,umax,vmin,vmax;BRepTools::UVBounds(TopoDS::Face(e.Current()),umin,umax,vmin,vmax);
                    if(std::isfinite(vmin)&&std::isfinite(vmax)&&vmax-vmin>.1&&umax-umin>=2*M_PI-1e-5) {
                        const gp_Cylinder cylinder=surface.Cylinder();const gp_Pnt p=cylinder.Location();const gp_Dir axis=cylinder.Axis().Direction();
                        const double v=(vmin+vmax)/2,epsilon=std::max(1e-5,std::min(.001,cylinder.Radius()*.001));
                        gp_Pnt center=p.Translated(gp_Vec(axis)*v);gp_Vec radial(center,surface.Value((umin+umax)/2,v));if(radial.SquareMagnitude()<1e-12)continue;radial.Normalize();
                        BRepClass3d_SolidClassifier inner(bodies.at(bodyId),center.Translated(radial*(cylinder.Radius()-epsilon)),1e-7),outer(bodies.at(bodyId),center.Translated(radial*(cylinder.Radius()+epsilon)),1e-7);
                        if(inner.State()!=TopAbs_OUT||outer.State()!=TopAbs_IN)continue;
                        for(double value:{static_cast<double>(face),static_cast<double>(bodyId),cylinder.Radius(),p.X(),p.Y(),p.Z(),axis.X(),axis.Y(),axis.Z(),vmin,vmax,umin,umax,1.0,0.0})cylinders.push_back(value);
                    }
                }
            }
            if(queryMode==8&&!cylinders.empty()){info.push_back(-8);info.push_back(static_cast<double>(cylinders.size()/15));info.push_back(0);info.insert(info.end(),cylinders.begin(),cylinders.end());}
            if(queryMode==8){std::vector<double> mesh=tessellatedTriangles(shape);if(mesh.empty()){kernelFailAt(-2,"Triangulacja bryły nie powiodła się");return env->NewDoubleArray(0);}std::vector<double> combined;combined.reserve(1+mesh.size()+info.size());combined.push_back(static_cast<double>(mesh.size()));combined.insert(combined.end(),mesh.begin(),mesh.end());combined.insert(combined.end(),info.begin(),info.end());info=std::move(combined);}
            jdoubleArray result=env->NewDoubleArray(static_cast<jsize>(info.size()));if(result&&!info.empty())env->SetDoubleArrayRegion(result,0,static_cast<jsize>(info.size()),info.data());return result;
        }
        if(queryMode==3) {
            GProp_GProps volume,surface;BRepGProp::VolumeProperties(shape,volume);BRepGProp::SurfaceProperties(shape,surface);
            gp_Pnt center=volume.CentreOfMass();
            double properties[]={std::abs(volume.Mass()),std::abs(surface.Mass()),center.X(),center.Y(),center.Z(),
                static_cast<double>(countShapes(shape,TopAbs_SOLID)),static_cast<double>(countShapes(shape,TopAbs_FACE))};
            for(double value:properties)if(!std::isfinite(value))return env->NewDoubleArray(0);
            jdoubleArray result=env->NewDoubleArray(7);if(result)env->SetDoubleArrayRegion(result,0,7,properties);return result;
        }
        return queryMode ? edgeSegments(env,shape,queryMode==2) : meshResult(env, shape);
    } catch (const Standard_Failure& failure) {
        std::string message="Wyjątek jądra OCCT: ";
        const char* text=failure.what();message+=(text&&*text)?text:"nieznany";
        if(g_kernelError.message.empty())g_kernelError.message=message;
        return env->NewDoubleArray(0);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeLastError(JNIEnv* env, jclass) {
    std::string value=std::to_string(g_kernelError.feature)+"\t"+g_kernelError.message;
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_pl_tabletcad_app_NativeCadEngine_nativeTechnicalDrawing(
        JNIEnv* env, jclass, jstring javaPath, jdouble sectionFraction) {
    if (javaPath == nullptr) return env->NewDoubleArray(0);
    const char* chars = env->GetStringUTFChars(javaPath, nullptr);
    if (chars == nullptr) return env->NewDoubleArray(0);
    const std::string path(chars);
    env->ReleaseStringUTFChars(javaPath, chars);
    try {
        STEPControl_Reader reader;
        if (reader.ReadFile(path.c_str()) != IFSelect_RetDone || reader.TransferRoots() <= 0)
            return env->NewDoubleArray(0);
        const TopoDS_Shape shape = reader.OneShape();
        std::vector<double> lines;
        auto append = [&](const TopoDS_Shape& edges, int view, int hidden, bool section) {
            if (edges.IsNull()) return;
            BRepLib::BuildCurves3d(edges);
            for (TopExp_Explorer e(edges, TopAbs_EDGE); e.More(); e.Next()) {
                BRepAdaptor_Curve curve(TopoDS::Edge(e.Current()));
                const double first=curve.FirstParameter(), last=curve.LastParameter();
                if (!std::isfinite(first) || !std::isfinite(last)) continue;
                const int count=curve.GetType()==GeomAbs_Line ? 1 : 96;
                gp_Pnt previous=curve.Value(first);
                for(int i=1;i<=count;i++) {
                    const gp_Pnt next=curve.Value(first+(last-first)*i/count);
                    const double x0=section ? previous.Z() : previous.X();
                    const double x1=section ? next.Z() : next.X();
                    if (std::hypot(x1-x0,next.Y()-previous.Y()) > 1.0e-8)
                        lines.insert(lines.end(), {static_cast<double>(view),static_cast<double>(hidden),
                            x0,previous.Y(),x1,next.Y()});
                    previous=next;
                }
            }
        };
        const gp_Ax2 frames[] = {
            gp_Ax2(gp_Pnt(0,0,0),gp_Dir(0,0,1),gp_Dir(1,0,0)),
            gp_Ax2(gp_Pnt(0,0,0),gp_Dir(0,1,0),gp_Dir(1,0,0)),
            gp_Ax2(gp_Pnt(0,0,0),gp_Dir(-1,0,0),gp_Dir(0,0,1))};
        for(int view=0;view<3;view++) {
            occ::handle<HLRBRep_Algo> algorithm = new HLRBRep_Algo();
            algorithm->Add(shape);
            algorithm->Projector(HLRAlgo_Projector(frames[view]));
            algorithm->Update(); algorithm->Hide();
            HLRBRep_HLRToShape output(algorithm);
            append(output.VCompound(),view,0,false);
            append(output.OutLineVCompound(),view,0,false);
            append(output.HCompound(),view,1,false);
            append(output.OutLineHCompound(),view,1,false);
        }
        const auto bounds=prismBounds(shape);
        if (!std::isfinite(sectionFraction) || sectionFraction <= 0 || sectionFraction >= 1)
            return env->NewDoubleArray(0);
        const double middle=bounds[0]+(bounds[3]-bounds[0])*sectionFraction;
        BRepAlgoAPI_Section section(shape,gp_Pln(gp_Pnt(middle,0,0),gp_Dir(1,0,0)));
        if(section.IsDone()) append(section.Shape(),3,0,true);
        jdoubleArray result=env->NewDoubleArray(static_cast<jsize>(lines.size()));
        if(result!=nullptr&&!lines.empty()) env->SetDoubleArrayRegion(result,0,lines.size(),lines.data());
        return result;
    } catch(const Standard_Failure&) { return env->NewDoubleArray(0); }
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
        jstring javaPlane, jdouble filletRadius, jstring javaFeatureOperation,
        jdouble featureDepth, jdouble ox, jdouble oy, jdouble oz, jdouble ux, jdouble uy,
        jdouble uz, jdouble vx, jdouble vy, jdouble vz) {
    if (javaPath == nullptr || javaType == nullptr || javaPlane == nullptr
            || javaFeatureOperation == nullptr || depth <= 0.0) {
        return JNI_FALSE;
    }
    const char* pathChars = env->GetStringUTFChars(javaPath, nullptr);
    const char* typeChars = env->GetStringUTFChars(javaType, nullptr);
    const char* planeChars = env->GetStringUTFChars(javaPlane, nullptr);
    const char* operationChars = env->GetStringUTFChars(javaFeatureOperation, nullptr);
    if (pathChars == nullptr || typeChars == nullptr || planeChars == nullptr
            || operationChars == nullptr) {
        if (pathChars != nullptr) env->ReleaseStringUTFChars(javaPath, pathChars);
        if (typeChars != nullptr) env->ReleaseStringUTFChars(javaType, typeChars);
        if (planeChars != nullptr) env->ReleaseStringUTFChars(javaPlane, planeChars);
        if (operationChars != nullptr) {
            env->ReleaseStringUTFChars(javaFeatureOperation, operationChars);
        }
        return JNI_FALSE;
    }
    const std::string path(pathChars), type(typeChars), plane(planeChars),
        featureOperation(operationChars);
    env->ReleaseStringUTFChars(javaPath, pathChars);
    env->ReleaseStringUTFChars(javaType, typeChars);
    env->ReleaseStringUTFChars(javaPlane, planeChars);
    env->ReleaseStringUTFChars(javaFeatureOperation, operationChars);
    try {
        TopoDS_Shape shape;
        if (featureOperation != "none") {
            shape = BRepPrimAPI_MakeBox(width, height, depth).Shape();
            if (filletRadius > 0.0) shape = filletAllEdges(shape, filletRadius);
            const gp_Vec uVector(ux, uy, uz), vVector(vx, vy, vz);
            if (shape.IsNull() || !std::isfinite(featureDepth)||std::abs(featureDepth)<=1e-6
                    || uVector.SquareMagnitude() <= 1.0e-20
                    || vVector.SquareMagnitude() <= 1.0e-20) return JNI_FALSE;
            const bool cut = featureOperation == "cut";
            const TopoDS_Shape tool = profileFeature(type, x1, y1, x2, y2, featureDepth,
                gp_Pnt(ox, oy, oz), gp_Dir(uVector), gp_Dir(vVector), cut);
            shape = applyProfileFeature(shape, tool, cut);
        } else {
            if (useProfile == JNI_TRUE) {
                shape = extrudedProfile(type, x1, y1, x2, y2, depth, plane);
            } else {
                shape = BRepPrimAPI_MakeBox(width, height, depth).Shape();
            }
            if (shape.IsNull() || !BRepCheck_Analyzer(shape).IsValid()) return JNI_FALSE;
            if (filletRadius > 0.0) shape = filletAllEdges(shape, filletRadius);
        }
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
