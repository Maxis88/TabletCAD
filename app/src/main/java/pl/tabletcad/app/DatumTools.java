package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Construction elements: offset / angled / three-point planes, axes and points, references, editing, vertex picking, drawing and touch. */
final class DatumTools {
    private final CadWorkspaceView ws;
    DatumTools(CadWorkspaceView view){ws=view;}
    /** Vertex picking for a datum: command, edited datum, picked points (model coordinates). */
    private String datumPickCommand;private Datum datumPickEdited;private final List<double[]> datumPickPoints=new ArrayList<>();private int datumPickNeeded;
    List<Datum> datumsOf(String kind){List<Datum> r=new ArrayList<>();for(Datum d:ws.project.datums)if(d.kind.equals(kind))r.add(d);return r;}
    Datum datum(String id){for(Datum d:ws.project.datums)if(d.id.equals(id))return d;return null;}
    String commandOf(Datum d) {
        if(d.plane())return d.method.equals("offset")?ws.DATUM_COMMANDS[0]:d.method.equals("angle")?ws.DATUM_COMMANDS[1]:ws.DATUM_COMMANDS[2];
        return d.axis()?ws.DATUM_COMMANDS[3]:ws.DATUM_COMMANDS[4];
    }
    /** Plane references for offset/angle planes: label → {origin xyz, normal xyz}. */
    void planeReferences(List<String> labels,List<double[]> frames,Datum excluded) {
        labels.add("Płaszczyzna XY");frames.add(new double[]{0,0,0,0,0,1});
        labels.add("Płaszczyzna XZ");frames.add(new double[]{0,0,0,0,1,0});
        labels.add("Płaszczyzna YZ");frames.add(new double[]{0,0,0,1,0,0});
        double[] face=ws.featurePatternTools.selectedPlanarFace();if(face!=null){labels.add("Zaznaczona ściana (wartości stałe)");frames.add(face);}
        for(Datum d:datumsOf(Datum.PLANE))if(d!=excluded){labels.add(d.name);frames.add(d.frame());}
    }
    void axisReferences(List<String> labels,List<double[]> frames,Datum excluded) {
        labels.add("Oś X");frames.add(new double[]{0,0,0,1,0,0});labels.add("Oś Y");frames.add(new double[]{0,0,0,0,1,0});labels.add("Oś Z");frames.add(new double[]{0,0,0,0,0,1});
        double[] row=selectedSurfaceAxis();if(row!=null){labels.add("Oś zaznaczonej ściany walcowej/stożkowej");frames.add(row);}
        for(Datum d:datumsOf(Datum.AXIS))if(d!=excluded){labels.add(d.name);frames.add(d.frame());}
    }
    /** Axis {point, direction} of the selected cylindrical/conical face from the exact face analysis, or null. */
    double[] selectedSurfaceAxis() {
        if(ws.selectedFace<0||!ws.project.hasSolid()||ws.project.importedMeshSource!=null)return null;
        String source=ws.project.importedStep?ws.importedSourceFile().getAbsolutePath():null;
        double[] row=GeometryMeasure.face(NativeCadEngine.faceAnalysis(ws.modelWidth,ws.modelHeight,ws.modelDepth,ws.project.importedStep?0:ws.project.filletRadius,ws.evaluationBase(),ws.kernelFeatures(),source),ws.selectedFace);
        if(row==null||(row[3]!=1&&row[3]!=2))return null;return new double[]{row[4],row[5],row[6],row[7],row[8],row[9]};
    }
    android.widget.Spinner choice(LinearLayout form,String label,List<String> items,int selected) {
        android.widget.TextView title=new android.widget.TextView(ws.getContext());title.setText(label);form.addView(title);
        android.widget.Spinner spinner=new android.widget.Spinner(ws.getContext());spinner.setAdapter(new android.widget.ArrayAdapter<>(ws.getContext(),android.R.layout.simple_spinner_dropdown_item,items));
        spinner.setSelection(Math.max(0,Math.min(items.size()-1,selected)));form.addView(spinner);return spinner;
    }
    private android.widget.Spinner offsetBase;private List<String> offsetLabels;private List<double[]> offsetFrames;private EditText offsetDistance;private int offsetFirstFace;
    private static final String FACE_REFERENCE="Zaznaczona ściana (wartości stałe)";
    /** Puts the tapped planar face into the base-plane choice of an open construction panel and selects it. */
    @SuppressWarnings("unchecked")
    void useFaceAsBase(android.widget.Spinner base,List<String> labels,List<double[]> frames,int at,Runnable changed) {
        double[] face=ws.featurePatternTools.selectedPlanarFace();if(face==null)return;
        int index=labels.indexOf(FACE_REFERENCE);if(index<0){index=Math.min(at,labels.size());labels.add(index,FACE_REFERENCE);frames.add(index,face);}else frames.set(index,face);
        ((android.widget.ArrayAdapter<String>)base.getAdapter()).notifyDataSetChanged();base.setSelection(index);changed.run();
        ws.operationMessage="Płaszczyzna bazowa: wskazana ściana bryły";
    }
    void showEditor(String command,Datum existing,double[] picked) {
        offsetBase=null;offsetLabels=null;offsetFrames=null;offsetDistance=null;
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        String kind=command.startsWith("Płaszczyzna")?Datum.PLANE:command.startsWith("Oś")?Datum.AXIS:Datum.POINT;
        EditText name=new EditText(ws.getContext());name.setSingleLine(true);name.setHint("Nazwa");name.setText(existing!=null?existing.name:Datum.nextName(ws.project.datums,kind));form.addView(name);
        android.widget.TextView info=new android.widget.TextView(ws.getContext());form.addView(info);
        java.util.function.Supplier<Datum> build;
        double[] v=existing!=null?existing.values:null;
        if(command.equals(ws.DATUM_COMMANDS[0])) {
            List<String> labels=new ArrayList<>();List<double[]> frames=new ArrayList<>();
            if(existing!=null){labels.add("Bieżące odniesienie: "+(existing.reference.isEmpty()?"zapisane":existing.reference));frames.add(java.util.Arrays.copyOf(v,6));}
            planeReferences(labels,frames,existing);
            android.widget.Spinner base=choice(form,"Płaszczyzna bazowa",labels,existing==null&&ws.featurePatternTools.selectedPlanarFace()!=null?3:0);
            EditText distance=ws.labeledCoordinate(form,"Odsunięcie wzdłuż normalnej [mm]",existing!=null?v[6]:10);ws.slider(form,distance,-2*ws.modelExtent(),2*ws.modelExtent(),.5);
            info.setText("Dotknij płaskiej ściany bryły, aby odsunąć płaszczyznę od tej ściany, albo wybierz płaszczyznę z listy. Przeciągnij strzałkę lub suwak, aby zmienić odległość.");
            offsetBase=base;offsetLabels=labels;offsetFrames=frames;offsetDistance=distance;offsetFirstFace=existing!=null?4:3;
            build=()->{double[] f=frames.get(base.getSelectedItemPosition());String ref=labels.get(base.getSelectedItemPosition()).replace("Bieżące odniesienie: ","");
                double[] values=java.util.Arrays.copyOf(f,7);values[6]=ws.parseCoordinate(distance);return existing!=null?new Datum(existing.id,kind,"offset",ref,values,name.getText().toString()):Datum.create(kind,"offset",ref,values,name.getText().toString());};
        } else if(command.equals(ws.DATUM_COMMANDS[1])) {
            List<String> axisLabels=new ArrayList<>(),planeLabels=new ArrayList<>();List<double[]> axes=new ArrayList<>(),planes=new ArrayList<>();
            if(existing!=null){axisLabels.add("Bieżąca oś");axes.add(java.util.Arrays.copyOf(v,6));planeLabels.add("Bieżąca płaszczyzna bazowa");planes.add(new double[]{v[0],v[1],v[2],v[6],v[7],v[8]});}
            axisReferences(axisLabels,axes,null);planeReferences(planeLabels,planes,existing);
            android.widget.Spinner axis=choice(form,"Oś obrotu (musi leżeć w płaszczyźnie bazowej)",axisLabels,0);
            android.widget.Spinner base=choice(form,"Płaszczyzna bazowa",planeLabels,0);
            EditText angle=ws.labeledCoordinate(form,"Kąt [°]",existing!=null?v[9]:45);ws.slider(form,angle,-180,180,1);
            offsetBase=base;offsetLabels=planeLabels;offsetFrames=planes;offsetDistance=null;offsetFirstFace=existing!=null?4:3;
            info.setText("Płaszczyzna zawiera oś i jest obrócona względem płaszczyzny bazowej o podany kąt.");
            build=()->{double[] a=axes.get(axis.getSelectedItemPosition()),b=planes.get(base.getSelectedItemPosition());
                double[] values={a[0],a[1],a[2],a[3],a[4],a[5],b[3],b[4],b[5],ws.parseCoordinate(angle)};
                String ref=planeLabels.get(base.getSelectedItemPosition());return existing!=null?new Datum(existing.id,kind,"angle",ref,values,name.getText().toString()):Datum.create(kind,"angle",ref,values,name.getText().toString());};
        } else {
            int points=command.equals(ws.DATUM_COMMANDS[2])?3:command.equals(ws.DATUM_COMMANDS[3])?2:1;
            double[] initial=picked!=null?picked:existing!=null?existing.values:null;
            android.widget.Spinner method=null;List<double[]> axes=new ArrayList<>();List<String> axisLabels=new ArrayList<>();
            if(kind.equals(Datum.AXIS)) {
                List<String> methods=new ArrayList<>(java.util.Arrays.asList("Przez punkt A w kierunku X","Przez punkt A w kierunku Y","Przez punkt A w kierunku Z","Przez punkty A i B"));
                double[] surface=selectedSurfaceAxis();if(surface!=null){methods.add("Oś zaznaczonej ściany walcowej/stożkowej");axes.add(surface);}
                int selected=picked!=null?3:existing!=null&&existing.method.equals("points")?3:0;
                if(existing!=null&&existing.method.equals("line")){double[] d=existing.direction;selected=Math.abs(d[0])>1-1e-12?0:Math.abs(d[1])>1-1e-12?1:Math.abs(d[2])>1-1e-12?2:-1;
                    if(selected<0){methods.add("Bieżący kierunek");axes.add(existing.frame());selected=methods.size()-1;}}
                method=choice(form,"Sposób",methods,selected);
            }
            final android.widget.Spinner fMethod=method;final int fixedMethods=4;
            String[] letters={"A","B","C"};List<EditText> fields=new ArrayList<>();
            for(int i=0;i<points;i++)for(int k=0;k<3;k++){double value=initial!=null&&initial.length>=i*3+3?initial[i*3+k]:i==1&&kind.equals(Datum.AXIS)&&k==2?10:i==1&&k==0?10:i==2&&k==1?10:0;
                fields.add(ws.labeledCoordinate(form,"Punkt "+letters[i]+" "+"XYZ".charAt(k)+" [mm]",value));}
            info.setText(points==3?"Trzy niewspółliniowe punkty. „Wskaż” wybiera wierzchołki B-Rep na modelu.":points==2?"Kierunek X/Y/Z przez punkt A albo prosta przez punkty A i B. „Wskaż” wybiera wierzchołki.":"Punkt w układzie modelu. „Wskaż” wybiera wierzchołek B-Rep.");
            build=()->{
                double[] values=new double[points*3];for(int i=0;i<values.length;i++)values[i]=ws.parseCoordinate(fields.get(i));
                String m=kind.equals(Datum.PLANE)?"points":kind.equals(Datum.POINT)?"coords":"points";String ref="";
                if(kind.equals(Datum.AXIS)) {
                    int s=fMethod.getSelectedItemPosition();
                    if(s<3){double[] d=new double[3];d[s]=1;values=new double[]{values[0],values[1],values[2],d[0],d[1],d[2]};m="line";ref="Oś "+"XYZ".charAt(s)+" przez punkt";}
                    else if(s>=fixedMethods){values=axes.get(s-fixedMethods).clone();m="line";ref=s-fixedMethods==0&&selectedSurfaceAxis()!=null?"Oś ściany walcowej":"Oś";}
                }
                return existing!=null?new Datum(existing.id,kind,m,ref,values,name.getText().toString()):Datum.create(kind,m,ref,values,name.getText().toString());
            };
        }
        final java.util.function.Supplier<Datum> candidate=build;
        CadWorkspaceView.PropertyBuilder dialog=ws.new PropertyBuilder().setTitle(existing!=null?"Edytuj: "+existing.name:command).setView(form).setNegativeButton("Anuluj",null)
            .setPositiveButton(existing!=null?"Zastosuj":"Utwórz",(d,w)->{
                try {
                    Datum created=candidate.get();created.hidden=existing!=null&&existing.hidden;
                    if(ws.commitModelChange(()->{int at=existing==null?-1:ws.project.datums.indexOf(datum(existing.id));if(at>=0)ws.project.datums.set(at,created);else ws.project.datums.add(created);})) {
                        ws.selectedTreeKey="datum:"+created.id;ws.operationMessage=(existing!=null?"Zaktualizowano: ":"Utworzono: ")+created.name+" · "+created.describe();
                    }
                }catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
                ws.invalidate();
            });
        if(command.equals(ws.DATUM_COMMANDS[2])||command.equals(ws.DATUM_COMMANDS[3])||command.equals(ws.DATUM_COMMANDS[4]))
            dialog.setNeutralButton("Wskaż na modelu",(d,w)->beginPick(command,existing,command.equals(ws.DATUM_COMMANDS[2])?3:command.equals(ws.DATUM_COMMANDS[3])?2:1));
        else if(existing!=null)dialog.setNeutralButton("Usuń",(d,w)->remove(existing));
        dialog.show();
        Runnable ghost=()->{try{ws.datumGhost=candidate.get();}catch(IllegalArgumentException error){ws.datumGhost=null;}ws.invalidate();};
        ws.watchFields(form,ghost);ghost.run();
        if(offsetBase!=null) {
            final android.widget.Spinner base=offsetBase;final List<String> labels=offsetLabels;final List<double[]> frames=offsetFrames;final int at=offsetFirstFace;
            ws.datumFaceListener=face->useFaceAsBase(base,labels,frames,at,ghost);
            if(offsetDistance!=null) {
                double[] f0=frames.get(base.getSelectedItemPosition());ws.attachFieldHandle(new double[]{f0[0],f0[1],f0[2]},new double[]{f0[3],f0[4],f0[5]},offsetDistance,-5000,5000,"Odsunięcie");
                if(ws.fieldHandle!=null){ws.fieldHandle.originSource=()->{double[] f=frames.get(base.getSelectedItemPosition());return new double[]{f[0],f[1],f[2]};};ws.fieldHandle.axisSource=()->{double[] f=frames.get(base.getSelectedItemPosition());return new double[]{f[3],f[4],f[5]};};}
            }
        }
    }
    void remove(Datum d) {
        if(ws.commitModelChange(()->ws.project.datums.removeIf(x->x.id.equals(d.id)))){if(ws.selectedTreeKey.equals("datum:"+d.id))ws.selectedTreeKey="";ws.operationMessage="Usunięto: "+d.name+". Szkice na tej płaszczyźnie zachowują swój układ.";}
        ws.invalidate();
    }
    void beginPick(String command,Datum existing,int needed) {
        if(!ws.project.hasSolid()||ws.project.importedMeshSource!=null){ws.operationMessage="Wskazywanie wymaga bryły B-Rep; wpisz współrzędne";ws.invalidate();return;}
        datumPickCommand=command;datumPickEdited=existing;datumPickNeeded=needed;datumPickPoints.clear();ws.mode="Konstrukcja";ws.measureTools.loadMeasurementGeometry();
        ws.operationMessage="Dotknij wierzchołka bryły ("+1+"/"+needed+")";ws.invalidate();
    }
    void pickTap(float x,float y) {
        CadWorkspaceView.MeasurePoint picked=ws.measureTools.nearestBrepVertex(x,y);
        if(picked==null){ws.operationMessage="Dotknij bliżej widocznego wierzchołka ("+(datumPickPoints.size()+1)+"/"+datumPickNeeded+")";ws.invalidate();return;}
        datumPickPoints.add(new double[]{picked.x+ws.meshCenterX,picked.y+ws.meshCenterY,picked.z+ws.meshCenterZ});
        if(datumPickPoints.size()<datumPickNeeded){ws.operationMessage="Wybrano "+datumPickPoints.size()+"/"+datumPickNeeded+". Dotknij kolejnego wierzchołka.";ws.invalidate();return;}
        double[] values=new double[datumPickNeeded*3];for(int i=0;i<datumPickNeeded;i++)System.arraycopy(datumPickPoints.get(i),0,values,i*3,3);
        ws.mode="Wybierz";String command=datumPickCommand;Datum edited=datumPickEdited;datumPickCommand=null;datumPickPoints.clear();
        showEditor(command,edited,values);ws.invalidate();
    }
    /** Starts a sketch on a datum plane with the same frame convention as a planar face. */
    void sketchOn(Datum d) {
        ws.selectedNormalX=d.direction[0];ws.selectedNormalY=d.direction[1];ws.selectedNormalZ=d.direction[2];
        ws.selectedPointX=d.origin[0];ws.selectedPointY=d.origin[1];ws.selectedPointZ=d.origin[2];ws.selectedFace=-1;
        ws.chooseSketchPlane(d.name);
    }
    void showPlanePicker() {
        List<Datum> planes=datumsOf(Datum.PLANE);if(planes.isEmpty()){ws.operationMessage="Brak płaszczyzn konstrukcyjnych (Bryła → Konstrukcja)";ws.invalidate();return;}
        String[] names=new String[planes.size()];for(int i=0;i<names.length;i++)names[i]=planes.get(i).name+" · "+planes.get(i).describe();
        ws.new PropertyBuilder().setTitle("Szkic na płaszczyźnie konstrukcyjnej").setItems(names,(d,w)->sketchOn(planes.get(w))).setNegativeButton("Anuluj",null).show();
    }
    /** Screen geometry of the drawn construction elements, for touch selection: id → xy pairs (plane 4, axis 2, point 1). */
    private final java.util.Map<String,float[]> datumScreen=new java.util.LinkedHashMap<>();
    Datum at(float x,float y,boolean faceUnderneath) {
        Datum best=null;double bestDistance=ws.dp(16);
        for(java.util.Map.Entry<String,float[]> e:datumScreen.entrySet()) {
            Datum d=datum(e.getKey());if(d==null||d.hidden)continue;float[] q=e.getValue();double distance;
            if(q.length==2)distance=Math.hypot(x-q[0],y-q[1]);
            else if(q.length==4)distance=ws.sketchScreenSegmentDistance(x,y,q[0],q[1],q[2],q[3]);
            else {
                distance=Double.POSITIVE_INFINITY;for(int i=0;i<8;i+=2){int j=(i+2)%8;distance=Math.min(distance,ws.sketchScreenSegmentDistance(x,y,q[i],q[i+1],q[j],q[j+1]));}
                if(!faceUnderneath&&distance>bestDistance&&inside(q,x,y))distance=bestDistance-.5;
            }
            if(distance<bestDistance){bestDistance=distance;best=d;}
        }
        return best;
    }
    boolean inside(float[] q,float x,float y) {
        boolean in=false;for(int i=0,j=q.length-2;i<q.length;j=i,i+=2)if((q[i+1]>y)!=(q[j+1]>y)&&x<(q[j]-q[i])*(y-q[i+1])/(q[j+1]-q[i+1])+q[i])in=!in;return in;
    }
    /** Tap on a construction element: select it, or start a sketch on a plane while choosing the sketch plane. */
    boolean tap(float x,float y) {
        if(ws.project.datums.isEmpty())return false;Datum d=at(x,y,ws.findFaceAt(x,y)>=0);if(d==null)return false;
        if(ws.mode.equals("Szkic")&&!ws.sketchPlaneChosen){if(!d.plane()){ws.operationMessage="Szkic wymaga płaszczyzny – dotknij płaszczyzny konstrukcyjnej albo ściany";ws.invalidate();return true;}sketchOn(d);return true;}
        long now=android.os.SystemClock.uptimeMillis();boolean repeat=ws.selectedTreeKey.equals("datum:"+d.id)&&now-ws.treeLastTap<android.view.ViewConfiguration.getDoubleTapTimeout();ws.treeLastTap=now;
        ws.selectedTreeKey="datum:"+d.id;ws.selectedFace=-1;ws.highlightBody=0;ws.highlightFeature=Integer.MIN_VALUE;ws.highlightSketch=false;
        if(repeat){showEditor(commandOf(d),d,null);return true;}
        ws.operationMessage="Zaznaczono: "+d.name+" · "+d.describe()+". Dotknij ponownie, aby edytować"+(d.plane()?"; Szkic → dotknij płaszczyzny, aby na niej rysować.":".");ws.invalidate();return true;
    }
    void draw(Canvas canvas) {
        datumScreen.clear();
        List<Datum> shown=new ArrayList<>(ws.project.datums);final Datum ghost=ws.propertyEditor!=null?ws.datumGhost:null;
        if(ghost!=null){shown.removeIf(x->x.id.equals(ghost.id));shown.add(ghost);}
        if(shown.isEmpty())return;
        double size=Math.max(20,.6*Math.max(ws.modelWidth,Math.max(ws.modelHeight,ws.modelDepth)));double[] c={ws.modelCenterX,ws.modelCenterY,ws.modelCenterZ};
        ws.paint.setStrokeWidth(ws.dp(1.5f));
        for(Datum d:shown) {
            boolean isGhost=d==ghost;if(d.hidden&&!isGhost)continue;boolean selected=isGhost||ws.selectedTreeKey.equals("datum:"+d.id);int color=isGhost?Color.rgb(39,174,96):selected?Color.rgb(230,126,34):Color.rgb(214,137,16);
            if(d.plane()) {
                double[] n=d.direction,f=d.sketchFrame();double t=(c[0]-d.origin[0])*n[0]+(c[1]-d.origin[1])*n[1]+(c[2]-d.origin[2])*n[2];
                double[] centre={c[0]-n[0]*t,c[1]-n[1]*t,c[2]-n[2]*t};android.graphics.Path path=new android.graphics.Path();boolean visible=true;
                double[][] corners={{-1,-1},{1,-1},{1,1},{-1,1}};
                for(int i=0;i<4;i++){double[] q=ws.projectWorldPoint(centre[0]+(f[3]*corners[i][0]+f[6]*corners[i][1])*size,centre[1]+(f[4]*corners[i][0]+f[7]*corners[i][1])*size,centre[2]+(f[5]*corners[i][0]+f[8]*corners[i][1])*size);
                    if(q[2]<=0)visible=false;if(i==0)path.moveTo((float)q[0],(float)q[1]);else path.lineTo((float)q[0],(float)q[1]);}
                if(!visible)continue;path.close();
                {float[] q=new float[8];for(int i=0;i<4;i++){double[] r=ws.projectWorldPoint(centre[0]+(f[3]*corners[i][0]+f[6]*corners[i][1])*size,centre[1]+(f[4]*corners[i][0]+f[7]*corners[i][1])*size,centre[2]+(f[5]*corners[i][0]+f[8]*corners[i][1])*size);q[2*i]=(float)r[0];q[2*i+1]=(float)r[1];}if(!isGhost)datumScreen.put(d.id,q);}
                ws.paint.setStyle(Paint.Style.FILL);ws.paint.setColor(Color.argb(selected?70:40,Color.red(color),Color.green(color),Color.blue(color)));canvas.drawPath(path,ws.paint);
                ws.paint.setStyle(Paint.Style.STROKE);ws.paint.setColor(color);canvas.drawPath(path,ws.paint);
                double[] label=ws.projectWorldPoint(centre[0]+(f[3]-f[6])*size,centre[1]+(f[4]-f[7])*size,centre[2]+(f[5]-f[8])*size);ws.paint.setStyle(Paint.Style.FILL);
                ws.text(canvas,d.name,(float)label[0]+ws.dp(4),(float)label[1]-ws.dp(4),10,color,true);
            } else if(d.axis()) {
                double[] a=ws.projectWorldPoint(d.origin[0]-d.direction[0]*size,d.origin[1]-d.direction[1]*size,d.origin[2]-d.direction[2]*size),b=ws.projectWorldPoint(d.origin[0]+d.direction[0]*size,d.origin[1]+d.direction[1]*size,d.origin[2]+d.direction[2]*size);
                if(a[2]<=0||b[2]<=0)continue;if(!isGhost)datumScreen.put(d.id,new float[]{(float)a[0],(float)a[1],(float)b[0],(float)b[1]});ws.paint.setStyle(Paint.Style.STROKE);ws.paint.setColor(color);ws.paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{ws.dp(10),ws.dp(4),ws.dp(2),ws.dp(4)},0));
                canvas.drawLine((float)a[0],(float)a[1],(float)b[0],(float)b[1],ws.paint);ws.paint.setPathEffect(null);ws.paint.setStyle(Paint.Style.FILL);ws.text(canvas,d.name,(float)b[0]+ws.dp(4),(float)b[1],10,color,true);
            } else {
                double[] p=ws.projectWorldPoint(d.origin[0],d.origin[1],d.origin[2]);if(p[2]<=0)continue;if(!isGhost)datumScreen.put(d.id,new float[]{(float)p[0],(float)p[1]});ws.paint.setStyle(Paint.Style.STROKE);ws.paint.setColor(color);
                canvas.drawCircle((float)p[0],(float)p[1],ws.dp(5),ws.paint);canvas.drawLine((float)p[0]-ws.dp(9),(float)p[1],(float)p[0]+ws.dp(9),(float)p[1],ws.paint);canvas.drawLine((float)p[0],(float)p[1]-ws.dp(9),(float)p[0],(float)p[1]+ws.dp(9),ws.paint);
                ws.paint.setStyle(Paint.Style.FILL);ws.text(canvas,d.name,(float)p[0]+ws.dp(8),(float)p[1]-ws.dp(8),10,color,true);
            }
        }
        ws.paint.setStyle(Paint.Style.FILL);
    }

    // ---- Split a body by a plane ----
}
