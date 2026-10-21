package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Technical drawing sheet: views, sheet dimensions, tolerances and their menus. */
final class SheetTools {
    private final CadWorkspaceView ws;
    SheetTools(CadWorkspaceView view){ws=view;}
    /** Sheet dimensioning: tap two end points of the views; tap a value to change or delete it. */
    void drawTechnicalViews(Canvas canvas, float left, float top, float right, float bottom) {
        ws.sheetArea.set(left+ws.dp(10),top+ws.dp(10),right-ws.dp(10),bottom-ws.dp(10));
        if(ws.technicalSheet!=null){ws.technicalSheet.pending=null;ws.technicalSheet.draw(canvas,ws.sheetArea,ws.project);
            if(ws.sheetDimensionMode&&ws.sheetFirst!=null){float factor=Math.min(ws.sheetArea.width()/297,ws.sheetArea.height()/210);
                ws.paint.setStyle(Paint.Style.STROKE);ws.paint.setStrokeWidth(ws.dp(2));ws.paint.setColor(ws.ACCENT);
                float[] at=sheetScreen(ws.sheetFirst);if(at!=null)canvas.drawCircle(at[0],at[1],ws.dp(7),ws.paint);
                if(ws.sheetSecond!=null){float[] second=sheetScreen(ws.sheetSecond);if(second!=null&&at!=null){canvas.drawCircle(second[0],second[1],ws.dp(5),ws.paint);canvas.drawLine(at[0],at[1],second[0],second[1],ws.paint);}}
                ws.paint.setStyle(Paint.Style.FILL);}}
        else ws.text(canvas,"Nie można utworzyć arkusza z bieżącego modelu",left+ws.dp(20),top+ws.dp(50),14,ws.INK,true);
    }

    float[] sheetScreen(double[] point) {
        if(ws.technicalSheet==null)return null;float[] paper=ws.technicalSheet.paperOf((int)point[0],point[1],point[2]);if(paper==null)return null;
        float factor=Math.min(ws.sheetArea.width()/297,ws.sheetArea.height()/210);
        return new float[]{ws.sheetArea.centerX()-297*factor/2+paper[0]*factor,ws.sheetArea.centerY()-210*factor/2+paper[1]*factor};
    }
    void sheetTap(float x,float y) {
        if(ws.technicalSheet==null)return;
        float[] paper=TechnicalSheet.paper(ws.sheetArea,x,y);float factor=Math.min(ws.sheetArea.width()/297,ws.sheetArea.height()/210);
        int existing=ws.technicalSheet.dimensionAt(ws.project.drawingDimensions,paper[0],paper[1]);
        if(existing>=0&&(!ws.sheetDimensionMode||ws.sheetFirst==null)){showSheetDimensionMenu(existing);return;}
        if(!ws.sheetDimensionMode){ws.operationMessage="Wymiaruj w panelu dodaje wymiary; dotknięcie wartości wymiaru pozwala go zmienić";ws.invalidate();return;}
        double tolerance=ws.dp(20)/factor;
        double[] hit=ws.technicalSheet.snap(paper[0],paper[1],tolerance),centre=ws.technicalSheet.centreAt(paper[0],paper[1],tolerance*.8);
        if(centre!=null)hit=centre;// circle centres (hole positions) take precedence over polyline points
        if(!ws.sheetAngleMode&&ws.sheetFirst==null&&centre==null) {
            double[] circle=ws.technicalSheet.circleAt(paper[0],paper[1],tolerance);
            boolean onOutline=circle!=null&&(hit==null||Math.abs(Math.hypot(hit[1]-circle[1],hit[2]-circle[2])-circle[3])<1e-3);
            if(onOutline) {
                float[] c=ws.technicalSheet.paperOf((int)circle[0],circle[1],circle[2]);
                double angle=Math.toDegrees(Math.atan2(-(paper[1]-c[1]),paper[0]-c[0]));
                addSheetDimension(TechnicalSheet.circleDimension((int)circle[0],circle[1],circle[2],circle[3],circle[4]==1,Math.rint(angle)),"Środek okręgu można też wskazać jako punkt wymiaru.");
                return;
            }
        }
        if(hit==null){ws.operationMessage=ws.sheetAngleMode?"Dotknij wierzchołka kąta, potem końców obu ramion":"Dotknij narożnika, końca krawędzi, środka lub obrysu okręgu w rzucie";ws.invalidate();return;}
        if(ws.sheetFirst==null){ws.sheetFirst=hit;ws.sheetSecond=null;ws.operationMessage=ws.sheetAngleMode?"Wierzchołek kąta wybrany. Wskaż koniec pierwszego ramienia":"Wskaż drugi punkt w tym samym rzucie";ws.invalidate();return;}
        if((int)hit[0]!=(int)ws.sheetFirst[0]){ws.operationMessage="Wszystkie punkty muszą leżeć w tym samym rzucie";ws.sheetFirst=hit;ws.sheetSecond=null;ws.invalidate();return;}
        if(Math.hypot(hit[1]-ws.sheetFirst[1],hit[2]-ws.sheetFirst[2])<1e-6||ws.sheetSecond!=null&&Math.hypot(hit[1]-ws.sheetSecond[1],hit[2]-ws.sheetSecond[2])<1e-6){ws.operationMessage="Wskaż inny punkt";ws.invalidate();return;}
        if(ws.sheetAngleMode) {
            if(ws.sheetSecond==null){ws.sheetSecond=hit;ws.operationMessage="Wskaż koniec drugiego ramienia";ws.invalidate();return;}
            double[] d=TechnicalSheet.angle((int)hit[0],ws.sheetFirst[1],ws.sheetFirst[2],ws.sheetSecond[1],ws.sheetSecond[2],hit[1],hit[2]);
            ws.sheetFirst=ws.sheetSecond=null;
            if(!(TechnicalSheet.value(d)>0.01&&TechnicalSheet.value(d)<179.99)){ws.operationMessage="Ramiona leżą na jednej prostej — wskaż inne punkty";ws.invalidate();return;}
            addSheetDimension(d,"Wskaż kolejny wierzchołek.");return;
        }
        double[] d=TechnicalSheet.dimension((int)hit[0],ws.sheetFirst[1],ws.sheetFirst[2],hit[1],hit[2],TechnicalSheet.autoKind(ws.sheetFirst[1],ws.sheetFirst[2],hit[1],hit[2]),8);
        ws.sheetFirst=null;
        addSheetDimension(d,"Wskaż kolejną parę punktów.");
    }
    void addSheetDimension(double[] raw,String hint) {
        double[] d=ws.technicalSheet.withAnchors(raw);
        try{ws.checkpoint();ws.project.drawingDimensions.add(d);ws.projectRepository.save(ws.project);ws.operationMessage="Wymiar "+TechnicalSheet.label(d)+" dodany. "+hint;}
        catch(RuntimeException error){ws.project.drawingDimensions.remove(d);ws.operationMessage="Nie zapisano wymiaru: "+error.getMessage();}
        ws.invalidate();
    }
    /** Stored record of a sheet dimension, re-attached to the current model when it moved. */
    double[] sheetRecord(int index) {
        double[] d=ws.project.drawingDimensions.get(index);double[] r=ws.technicalSheet==null?null:ws.technicalSheet.resolve(d);
        return r==null?d:ws.technicalSheet.withAnchors(r);
    }
    void storeSheetDimension(int index,double[] next,String message) {
        ws.checkpoint();
        if(next==null)ws.project.drawingDimensions.remove(index);else ws.project.drawingDimensions.set(index,next);
        try{ws.projectRepository.save(ws.project);ws.operationMessage=message;}
        catch(RuntimeException error){ws.operationMessage="Nie zapisano: "+error.getMessage();}
        ws.invalidate();
    }
    /** Symmetric (±) or different upper/lower deviation of a sheet dimension. */
    void showSheetTolerance(int index) {
        double[] d=sheetRecord(index);boolean angular=(int)d[5]==TechnicalSheet.ANGLE;
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        EditText plus=ws.labeledCoordinate(form,angular?"Odchyłka górna [°]":"Odchyłka górna [mm]",d[9]),minus=ws.labeledCoordinate(form,angular?"Odchyłka dolna [°]":"Odchyłka dolna [mm]",d[10]);
        android.widget.TextView hint=new android.widget.TextView(ws.getContext());hint.setText("Równe wartości dają zapis ±. Zero w obu polach usuwa tolerancję.");form.addView(hint);
        ws.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle("Tolerancja · "+TechnicalSheet.label(d)).setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(dialog,w)->{
            try{double up=Math.abs(ws.parseCoordinate(plus)),down=Math.abs(ws.parseCoordinate(minus));if(!(up<=100&&down<=100))throw new IllegalArgumentException("Odchyłka do 100");
                double[] next=d.clone();next[9]=up;next[10]=down;storeSheetDimension(index,next,"Tolerancja: "+TechnicalSheet.label(next));}
            catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();ws.invalidate();}
        }).show();
    }
    void showSheetCircleMenu(int index) {
        double[] d=sheetRecord(index);double r=Math.hypot(d[3]-d[1],d[4]-d[2]);
        String[] items={"Średnica Ø","Promień R","Obróć odnośnik o 45°","Obróć odnośnik o −45°","Tolerancja…","Usuń wymiar"};
        ws.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle("Wymiar okręgu · "+TechnicalSheet.label(d)).setItems(items,(dialog,which)->{
            if(which==4){showSheetTolerance(index);return;}
            if(which==5){storeSheetDimension(index,null,"Usunięto wymiar arkusza");return;}
            double angle=d[6]+(which==2?45:which==3?-45:0);if(angle>180)angle-=360;if(angle<-180)angle+=360;
            double[] next=ws.technicalSheet.withAnchors(TechnicalSheet.circleDimension((int)d[0],d[1],d[2],r,which==0||(which>1&&(int)d[5]==TechnicalSheet.DIAMETER),angle));
            next[9]=d[9];next[10]=d[10];
            storeSheetDimension(index,next,"Zmieniono wymiar okręgu");
        }).setNegativeButton("Zamknij",null).show();
    }
    void showSheetDimensionMenu(int index) {
        double[] d=sheetRecord(index);
        if((int)d[5]==TechnicalSheet.DIAMETER||(int)d[5]==TechnicalSheet.RADIUS){showSheetCircleMenu(index);return;}
        if((int)d[5]==TechnicalSheet.ANGLE) {
            ws.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle("Kąt na arkuszu · "+TechnicalSheet.label(d)).setItems(new String[]{"Łuk dalej (+4 mm)","Łuk bliżej (−4 mm)","Tolerancja…","Usuń wymiar"},(dialog,which)->{
                if(which==2){showSheetTolerance(index);return;}
                if(which==3){storeSheetDimension(index,null,"Usunięto wymiar arkusza");return;}
                double[] next=d.clone();next[6]=Math.max(3,Math.min(100,d[6]+(which==0?4:-4)));storeSheetDimension(index,next,"Zmieniono wymiar kąta");
            }).setNegativeButton("Zamknij",null).show();return;
        }
        String[] items={"Poziomy","Pionowy","Równoległy do punktów","Odsuń dalej (+4 mm)","Przysuń bliżej (−4 mm)","Odwróć stronę","Tolerancja…","Usuń wymiar"};
        ws.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle("Wymiar arkusza · "+TechnicalSheet.label(d)).setItems(items,(dialog,which)->{
            if(which==6){showSheetTolerance(index);return;}
            if(which==7){storeSheetDimension(index,null,"Usunięto wymiar arkusza");return;}
            double[] next=d.clone();
            if(which<=2)next[5]=which==0?TechnicalSheet.HORIZONTAL:which==1?TechnicalSheet.VERTICAL:TechnicalSheet.ALIGNED;
            else if(which==3)next[6]=Math.min(100,d[6]+4);else if(which==4)next[6]=Math.max(-100,d[6]-4);else if(which==5)next[6]=-d[6];
            storeSheetDimension(index,next,"Zmieniono wymiar: "+TechnicalSheet.label(next));
        }).setNegativeButton("Zamknij",null).show();
    }

}
