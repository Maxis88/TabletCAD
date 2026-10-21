package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Measurements: points, edges, faces, angles, areas, the measurement panel and drawing */
final class MeasureTools {
    private final CadWorkspaceView ws;
    MeasureTools(CadWorkspaceView view){ws=view;}
    void handleMeasureTap(float x, float y) {
        if(ws.measurementOutput==null)showMeasurementPanel();
        if(ws.measureFaceMode>0&&ws.project.importedMeshSource==null){measureFaceTap(x,y);return;}
        if(ws.findFaceAt(x,y)<0&&nearestBrepVertex(x,y)==null&&nearestMeasurePoint(x,y)==null){ws.clearViewportSelection();return;}
        if(!ws.measurePointMode&&ws.project.importedMeshSource==null) {
            double distance=ws.dp(18);int selected=-1;
            for(int i=0;i+15<ws.measureEdges.length;i+=16) {
                double[] a=ws.projectWorldPoint(ws.measureEdges[i+9],ws.measureEdges[i+10],ws.measureEdges[i+11]);
                double[] b=ws.projectWorldPoint(ws.measureEdges[i+12],ws.measureEdges[i+13],ws.measureEdges[i+14]);
                double d=HiddenLineVisibility.distanceToVisibleSegment(x,y,a,b,ws.depthSurfaces);
                if(d<distance){distance=d;selected=i;}
            }
            if(selected>=0)ws.measuredCurve=CadMeasurements.edge(ws.measureProperties,ws.measureEdges[selected+15]);
            else ws.operationMessage="Dotknij bliżej widocznej krawędzi";
            updateMeasurementOutput();ws.invalidate();return;
        }
        CadWorkspaceView.MeasurePoint picked = ws.project.importedMeshSource==null?nearestBrepVertex(x,y):nearestMeasurePoint(x, y);
        if (picked == null) {
            ws.measuredEdgeText = "Brak krawędzi w zasięgu";
            ws.operationMessage="Dotknij widocznego wierzchołka bryły";
            updateMeasurementOutput();
            ws.invalidate();
            return;
        }
        if (ws.measureA == null || ws.measureB != null) {
            ws.measureA = picked;
            ws.measureB = null;
        } else {
            ws.measureB = picked;
        }
        ws.measuredEdgeText = picked.edgeLength > 0.0 ? ws.formatMm(picked.edgeLength) : "";
        updateMeasurementOutput();
        ws.invalidate();
    }

    /** Angle (two picks: straight edges, planar faces or surface axes) or exact face area. */
    void measureFaceTap(float x,float y) {
        double distance=ws.dp(14);int selected=-1;
        if(ws.measureFaceMode==1)for(int i=0;i+15<ws.measureEdges.length;i+=16) {
            double[] a=ws.projectWorldPoint(ws.measureEdges[i+9],ws.measureEdges[i+10],ws.measureEdges[i+11]),b=ws.projectWorldPoint(ws.measureEdges[i+12],ws.measureEdges[i+13],ws.measureEdges[i+14]);
            double d=HiddenLineVisibility.distanceToVisibleSegment(x,y,a,b,ws.depthSurfaces);if(d<distance){distance=d;selected=i;}
        }
        GeometryMeasure.Direction picked=null;
        if(selected>=0){try{picked=GeometryMeasure.edgeDirection(CadMeasurements.edge(ws.measureProperties,ws.measureEdges[selected+15]));}catch(IllegalArgumentException ignored){picked=null;}}
        int face=selected>=0&&picked!=null?-1:ws.findFaceAt(x,y);
        if(picked==null&&face<0){ws.operationMessage=ws.measureFaceMode==1?"Dotknij prostej krawędzi albo ściany":"Dotknij ściany bryły";ws.invalidate();return;}
        double[] row=face>=0?GeometryMeasure.face(ws.measureFaces,face):null;
        if(face>=0&&row==null){ws.operationMessage="Brak danych B-Rep tej ściany";ws.invalidate();return;}
        if(ws.measureFaceMode==2){ws.measuredFace=row;ws.measuredFaceIndex=face;ws.selectedFace=face;updateMeasurementOutput();ws.invalidate();return;}
        if(picked==null){picked=GeometryMeasure.faceDirection(row);if(picked==null){ws.operationMessage="Ta powierzchnia nie ma kierunku (kula lub powierzchnia swobodna)";ws.invalidate();return;}ws.selectedFace=face;}
        if(ws.measureDirections.size()>=2)ws.measureDirections.clear();ws.measureDirections.add(picked);
        updateMeasurementOutput();ws.invalidate();
    }
    CadWorkspaceView.MeasurePoint nearestBrepVertex(float x,float y) {
        CadWorkspaceView.MeasurePoint best=null;double distance=ws.dp(20);
        for(int i=0;i+15<ws.measureProperties.length;i+=16)for(int endpoint:new int[]{0,6}) {
            double wx=ws.measureProperties[i+endpoint],wy=ws.measureProperties[i+endpoint+1],wz=ws.measureProperties[i+endpoint+2];
            double[] p=ws.projectWorldPoint(wx,wy,wz);double d=Math.hypot(p[0]-x,p[1]-y);
            if(d<distance&&HiddenLineVisibility.visible(p[0],p[1],p[2],ws.depthSurfaces)) {
                distance=d;best=new CadWorkspaceView.MeasurePoint(wx-ws.meshCenterX,wy-ws.meshCenterY,wz-ws.meshCenterZ,(float)p[0],(float)p[1],"Punkt B-Rep",0);
            }
        }
        return best;
    }

    void loadMeasurementGeometry() {
        ws.measureEdges=ws.measureProperties=new double[0];ws.measuredCurve=null;ws.measureA=ws.measureB=null;
        if(!ws.project.hasSolid()||ws.project.importedMeshSource!=null)return;
        String source=ws.project.importedStep?ws.importedSourceFile().getAbsolutePath():null;
        double radius=ws.project.importedStep?0:ws.project.filletRadius;
        ws.measureEdges=NativeCadEngine.modelEdges(ws.modelWidth,ws.modelHeight,ws.modelDepth,radius,ws.evaluationBase(),ws.kernelFeatures(),source);
        ws.measureProperties=NativeCadEngine.edgeMeasurements(ws.modelWidth,ws.modelHeight,ws.modelDepth,radius,ws.evaluationBase(),ws.kernelFeatures(),source);
        ws.measureFaces=NativeCadEngine.faceAnalysis(ws.modelWidth,ws.modelHeight,ws.modelDepth,radius,ws.evaluationBase(),ws.kernelFeatures(),source);
        ws.measureDirections.clear();ws.measuredFace=null;ws.measuredFaceIndex=-1;
    }

    void showMeasurementPanel() {
        android.widget.RadioGroup types=new android.widget.RadioGroup(ws.getContext());
        android.widget.RadioButton edge=new android.widget.RadioButton(ws.getContext()),points=new android.widget.RadioButton(ws.getContext());
        edge.setId(View.generateViewId());points.setId(View.generateViewId());
        edge.setText("Krawędź · długość / R / Ø");points.setText("Punkt–punkt · wierzchołki");
        android.widget.RadioButton angle=new android.widget.RadioButton(ws.getContext()),area=new android.widget.RadioButton(ws.getContext());
        angle.setId(View.generateViewId());area.setId(View.generateViewId());angle.setText("Kąt · ściany, krawędzie, osie");area.setText("Ściana · pole i typ powierzchni");
        edge.setEnabled(ws.project.importedMeshSource==null);angle.setEnabled(ws.project.importedMeshSource==null);area.setEnabled(ws.project.importedMeshSource==null);
        types.addView(edge);types.addView(points);types.addView(angle);types.addView(area);
        types.check(ws.measureFaceMode==1?angle.getId():ws.measureFaceMode==2?area.getId():ws.measurePointMode?points.getId():edge.getId());
        android.widget.TextView output=new android.widget.TextView(ws.getContext());output.setTextSize(15);
        output.setTextColor(ws.INK);output.setPadding(0,(int)ws.dp(12),0,(int)ws.dp(16));
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);form.addView(types);form.addView(output);
        ws.new PropertyBuilder().setTitle(ws.project.importedMeshSource==null?"Pomiar · geometria OCCT":"Pomiar · siatka STL (przybliżony)")
            .setView(form).setNeutralButton("Wyczyść pomiar",(d,w)-> {
                ws.measureA=ws.measureB=null;ws.measuredCurve=null;showMeasurementPanel();ws.invalidate();
            }).setNegativeButton("Zamknij pomiar",(d,w)->{ws.mode="Wybierz";ws.measureA=ws.measureB=null;ws.measuredCurve=null;ws.invalidate();}).show();
        ws.measurementOutput=output;
        types.setOnCheckedChangeListener((group,id)-> {
            ws.measurePointMode=id==points.getId();ws.measureFaceMode=id==angle.getId()?1:id==area.getId()?2:0;ws.measureA=ws.measureB=null;ws.measuredCurve=null;ws.measureDirections.clear();ws.measuredFace=null;
            updateMeasurementOutput();ws.invalidate();
        });
        updateMeasurementOutput();
    }

    void updateMeasurementOutput() {
        if(ws.measurementOutput==null)return;
        if(ws.measureFaceMode==1) {
            if(ws.measureDirections.isEmpty())ws.measurementOutput.setText("Dotknij pierwszej ściany płaskiej, prostej krawędzi albo ściany walcowej (oś).");
            else if(ws.measureDirections.size()==1)ws.measurementOutput.setText("Wybrano: "+ws.measureDirections.get(0).label+". Dotknij drugiego elementu.");
            else ws.measurementOutput.setText(ws.measureDirections.get(0).label+" · "+ws.measureDirections.get(1).label+"\n\n"+GeometryMeasure.describeAngle(ws.measureDirections.get(0),ws.measureDirections.get(1))+"\n\nWartości z geometrii OCCT. Kolejne dotknięcie zaczyna nowy pomiar.");
            return;
        }
        if(ws.measureFaceMode==2) {
            if(ws.measuredFace==null){ws.measurementOutput.setText("Dotknij ściany bryły.");return;}
            String text="Ściana "+(ws.measuredFaceIndex+1)+" · "+GeometryMeasure.surfaceName(ws.measuredFace[3])+"\n\nPole: "+ws.formatArea(ws.measuredFace[2]);
            if(ws.measuredFace[3]==1||ws.measuredFace[3]==3)text+="\nPromień R: "+ws.formatMm(ws.measuredFace[10])+"\nŚrednica Ø: "+ws.formatMm(2*ws.measuredFace[10]);
            ws.measurementOutput.setText(text+"\n\nPole dokładne z B-Rep (BRepGProp), nie z siatki ekranu.");return;
        }
        if(!ws.measurePointMode) {
            if(ws.measuredCurve==null){ws.measurementOutput.setText(ws.measureEdges.length==0?"Brak krawędzi B-Rep do pomiaru":"Dotknij widocznej krawędzi bryły. Przeciągnięcie obraca model.");return;}
            String value=(ws.measuredCurve[14]==0?"Krawędź prosta":ws.measuredCurve[14]==1?"Krawędź kołowa / łuk":"Krzywa B-Rep")+"\n\nDługość: "+ws.formatMm(ws.measuredCurve[9]);
            if(ws.measuredCurve[14]==1)value+="\nPromień R: "+ws.formatMm(ws.measuredCurve[10])+"\nŚrednica Ø: "+ws.formatMm(ws.measuredCurve[10]*2);
            ws.measurementOutput.setText(value+"\n\nWartości z geometrii OCCT, nie z siatki ekranu.");
        }else if(ws.measureA==null)ws.measurementOutput.setText(ws.project.importedMeshSource==null?"Wskaż pierwszy widoczny wierzchołek. Punkty są przyciągane do końców krawędzi B-Rep.":"Wskaż dwa punkty siatki STL. Pomiar zależy od jej dokładności.");
        else if(ws.measureB==null)ws.measurementOutput.setText("Punkt A wybrany. Wskaż drugi wierzchołek.");
        else ws.measurementOutput.setText("Odległość 3D: "+ws.formatMm(ws.measureA.distanceTo(ws.measureB))
            +"\nΔX: "+ws.formatMm(Math.abs(ws.measureB.x-ws.measureA.x))+"\nΔY: "+ws.formatMm(Math.abs(ws.measureB.y-ws.measureA.y))+"\nΔZ: "+ws.formatMm(Math.abs(ws.measureB.z-ws.measureA.z))
            +"\n\nKolejne dotknięcie rozpoczyna nowy pomiar.");
    }

    void drawMeasuredCurve(Canvas canvas) {
        boolean visible=false;ws.paint.setStrokeWidth(ws.dp(3));ws.paint.setColor(CadWorkspaceView.MEASURE);
        for(int i=0;i+15<ws.measureEdges.length;i+=16)if(ws.measureEdges[i+15]==ws.measuredCurve[15]) {
            double[] a=ws.projectWorldPoint(ws.measureEdges[i+9],ws.measureEdges[i+10],ws.measureEdges[i+11]);
            double[] b=ws.projectWorldPoint(ws.measureEdges[i+12],ws.measureEdges[i+13],ws.measureEdges[i+14]);
            for(double[] r:HiddenLineVisibility.segments(a[0],a[1],a[2],b[0],b[1],b[2],ws.depthSurfaces)) {
                visible=true;canvas.drawLine((float)(a[0]+(b[0]-a[0])*r[0]),(float)(a[1]+(b[1]-a[1])*r[0]),
                    (float)(a[0]+(b[0]-a[0])*r[1]),(float)(a[1]+(b[1]-a[1])*r[1]),ws.paint);
            }
        }
        if(!visible)return;
        double[] a=ws.projectWorldPoint(ws.measuredCurve[0],ws.measuredCurve[1],ws.measuredCurve[2]);
        double[] b=ws.projectWorldPoint(ws.measuredCurve[6],ws.measuredCurve[7],ws.measuredCurve[8]);
        String label=ws.formatMm(ws.measuredCurve[9]);
        if(ws.measuredCurve[14]==1) {b=a;a=ws.projectWorldPoint(ws.measuredCurve[11],ws.measuredCurve[12],ws.measuredCurve[13]);label="Ø "+ws.formatMm(2*ws.measuredCurve[10])+"  (R "+ws.formatMm(ws.measuredCurve[10])+")";}
        else if(ws.measuredCurve[14]!=0)return;
        double dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy);if(length<ws.dp(3))return;
        float nx=(float)(-dy/length*ws.dp(22)),ny=(float)(dx/length*ws.dp(22));
        float ax=(float)a[0],ay=(float)a[1],bx=(float)b[0],by=(float)b[1];
        ws.paint.setStrokeWidth(ws.dp(1));canvas.drawLine(ax,ay,ax+nx*1.3f,ay+ny*1.3f,ws.paint);canvas.drawLine(bx,by,bx+nx*1.3f,by+ny*1.3f,ws.paint);
        canvas.drawLine(ax+nx,ay+ny,bx+nx,by+ny,ws.paint);
        canvas.drawCircle(ax+nx,ay+ny,ws.dp(2.5f),ws.paint);canvas.drawCircle(bx+nx,by+ny,ws.dp(2.5f),ws.paint);
        ws.text(canvas,label,(ax+bx)/2+nx,(ay+by)/2+ny-ws.dp(8),12,CadWorkspaceView.MEASURE,true,Paint.Align.CENTER);
    }

    CadWorkspaceView.MeasurePoint nearestMeasurePoint(float x, float y) {
        CadWorkspaceView.MeasurePoint bestVertex = null;
        double bestVertexDistance = ws.dp(15);
        for (CadWorkspaceView.ProjectedTriangle triangle : ws.projectedTriangles) {
            for (int i = 0; i < 3; i++) {
                double screenDistance = Math.hypot(x - triangle.sx[i], y - triangle.sy[i]);
                if (screenDistance < bestVertexDistance) {
                    if(!HiddenLineVisibility.visible(triangle.sx[i],triangle.sy[i],triangle.q[i],ws.depthSurfaces))continue;
                    bestVertexDistance = screenDistance;
                    bestVertex = new CadWorkspaceView.MeasurePoint(triangle.wx[i], triangle.wy[i], triangle.wz[i],
                        triangle.sx[i], triangle.sy[i], "Wierzchołek", 0.0);
                }
            }
        }
        if (bestVertex != null) return bestVertex;

        CadWorkspaceView.MeasurePoint bestEdge = null;
        double bestEdgeDistance = ws.dp(13);
        for (CadWorkspaceView.ProjectedTriangle triangle : ws.projectedTriangles) {
            for (int edge = 0; edge < 3; edge++) {
                if (!ws.isVisibleCadEdge(triangle, edge)) continue;
                int next = (edge + 1) % 3;
                double dx = triangle.sx[next] - triangle.sx[edge];
                double dy = triangle.sy[next] - triangle.sy[edge];
                double lengthSquared = dx * dx + dy * dy;
                if (lengthSquared < 1.0e-8) continue;
                double t = ((x - triangle.sx[edge]) * dx + (y - triangle.sy[edge]) * dy) / lengthSquared;
                t = Math.max(0.0, Math.min(1.0, t));
                float sx = triangle.sx[edge] + (float)(t * dx);
                float sy = triangle.sy[edge] + (float)(t * dy);
                double screenDistance = Math.hypot(x - sx, y - sy);
                if (screenDistance < bestEdgeDistance) {
                    if(!HiddenLineVisibility.visible(sx,sy,triangle.q[edge]+t*(triangle.q[next]-triangle.q[edge]),ws.depthSurfaces))continue;
                    double wx = triangle.wx[edge] + t * (triangle.wx[next] - triangle.wx[edge]);
                    double wy = triangle.wy[edge] + t * (triangle.wy[next] - triangle.wy[edge]);
                    double wz = triangle.wz[edge] + t * (triangle.wz[next] - triangle.wz[edge]);
                    double edgeLength = Math.sqrt(
                        ws.square(triangle.wx[next] - triangle.wx[edge])
                        + ws.square(triangle.wy[next] - triangle.wy[edge])
                        + ws.square(triangle.wz[next] - triangle.wz[edge]));
                    bestEdgeDistance = screenDistance;
                    bestEdge = new CadWorkspaceView.MeasurePoint(wx, wy, wz, sx, sy, "Krawędź", edgeLength);
                }
            }
        }
        return bestEdge;
    }

}
