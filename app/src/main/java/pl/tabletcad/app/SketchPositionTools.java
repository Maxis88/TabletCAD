package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Sketch element and group positioning (X/Y from references, alignment), path pattern */
final class SketchPositionTools {
    private final CadWorkspaceView ws;
    SketchPositionTools(CadWorkspaceView view){ws=view;}
    /** Element being positioned while the user points at a reference (sketch point, element or body edge). */
    /** Element waiting for its path in "Szyk po ścieżce". */
    /** Multi-selection in a sketch: tap toggles, dragging a member moves the whole group. */
    /** "Położenie" chosen without a selection: the next touched element opens it. */
    String sketchEntityName(SketchPrimitive p) {
        if(p.type.equals("Okrąg")&&p.vertices==null&&p.curves==null)return "Okrąg Ø"+ws.formatMm(2*Math.hypot(p.x2-p.x1,p.y2-p.y1));
        if(p.type.equals("Prostokąt")&&p.vertices==null)return "Prostokąt "+ws.formatMm(Math.abs(p.x2-p.x1))+" × "+ws.formatMm(Math.abs(p.y2-p.y1));
        return p.type;
    }
    /**
     * "Położenie": puts the element at exact X/Y distances from the sketch origin or from a point of
     * another element (also an edge projected from the body), or aligns it with that element.
     */
    void showSketchPosition(String id,int pointChoice,String refId,int refAnchor) {
        if(ws.GROUP_ID.equals(id)){showGroupPosition(pointChoice,refId,refAnchor);return;}
        SketchPrimitive element=null,reference=null;
        for(SketchPrimitive p:ws.project.sketch){if(p.id.equals(id))element=p;if(p.id.equals(refId))reference=p;}
        if(element==null){ws.operationMessage="Zaznacz element szkicu";ws.invalidate();return;}
        if(reference==null)refId=null;
        final SketchPrimitive source=element,ref=reference;final String refKey=refId;
        // A projected face is a group of edges; alignment uses the whole outline.
        final List<String> group=ref!=null&&ws.positionGroup!=null&&ws.positionGroup.contains(ref.id)?new ArrayList<>(ws.positionGroup):java.util.Collections.singletonList(ref==null?"":ref.id);ws.positionGroup=null;
        final int refPoint=ref!=null&&refAnchor>=0&&refAnchor<SketchConstraints.anchorCount(ref)?refAnchor:-1;
        ws.beginSketchWork();ws.mode="Edytuj szkic";ws.selectedSketchId=id;
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView what=new android.widget.TextView(ws.getContext());what.setText("Element: "+sketchEntityName(source));form.addView(what);
        List<String> points=SketchPosition.pointNames(source);
        android.widget.TextView pointLabel=new android.widget.TextView(ws.getContext());pointLabel.setText("Punkt elementu");form.addView(pointLabel);
        android.widget.Spinner point=new android.widget.Spinner(ws.getContext());point.setAdapter(new android.widget.ArrayAdapter<>(ws.getContext(),android.R.layout.simple_spinner_dropdown_item,points));
        point.setSelection(Math.max(0,Math.min(pointChoice,points.size()-1)));form.addView(point);
        String refText=group.size()>1?"Ściana bryły (rzut, "+group.size()+" krawędzi)":ref==null?"Początek układu szkicu (0, 0)":refPoint>=0?SketchPosition.pointNames(ref).get(Math.min(SketchPosition.pointNames(ref).size()-1,ref.type.equals("Prostokąt")&&ref.vertices==null?refPoint+1:refPoint))+" · "+sketchEntityName(ref):sketchEntityName(ref);
        android.widget.TextView refLabel=new android.widget.TextView(ws.getContext());refLabel.setText("Odniesienie: "+refText);form.addView(refLabel);
        android.widget.Button pick=new android.widget.Button(ws.getContext());pick.setText("Wskaż odniesienie na szkicu lub bryle…");form.addView(pick);
        pick.setOnClickListener(v->{ws.closePropertyEditor();ws.sketchGhost.clear();ws.positionPickId=source.id;ws.positionPickPoint=point.getSelectedItemPosition();
            if(ws.project.hasSolid()&&ws.project.importedMeshSource==null&&ws.projectionEdges.length==0)ws.loadProjectionEdges();
            ws.operationMessage="Dotknij punktu lub elementu szkicu, krawędzi bryły (zostanie zrzutowana) albo początku układu";ws.invalidate();});
        if(ref!=null){android.widget.Button origin=new android.widget.Button(ws.getContext());origin.setText("Odniesienie: początek układu");form.addView(origin);
            origin.setOnClickListener(v->{ws.closePropertyEditor();showSketchPosition(source.id,point.getSelectedItemPosition(),null,0);});}
        final boolean pointRef=ref==null||refPoint>=0;
        int anchorNow=SketchPosition.anchorOf(source,point.getSelectedItemPosition());
        double[] offset=SketchPosition.offset(ws.project.sketch,source.id,anchorNow,pointRef?refKey:null,Math.max(0,refPoint));
        EditText fx=ws.labeledCoordinate(form,"Odległość X [mm]",offset[0]),fy=ws.labeledCoordinate(form,"Odległość Y [mm]",offset[1]);
        android.widget.CheckBox lock=new android.widget.CheckBox(ws.getContext());lock.setText("Zablokuj wymiarami położenia (sterujące)");
        lock.setChecked(SketchPosition.lockable(source,anchorNow));lock.setEnabled(SketchPosition.lockable(source,anchorNow));form.addView(lock);
        if(!pointRef){android.widget.TextView note=new android.widget.TextView(ws.getContext());note.setText("Ten element nie ma punktów — odległości X/Y liczone od początku układu.");form.addView(note);}
        if(ref!=null) {
            android.widget.TextView alignLabel=new android.widget.TextView(ws.getContext());alignLabel.setText("Wyrównaj do: "+(group.size()>1?"obrysu ściany":sketchEntityName(ref)));form.addView(alignLabel);
            for(int i=0;i<SketchPosition.ALIGN.length;i++) {
                final int alignMode=i;android.widget.Button align=new android.widget.Button(ws.getContext());align.setText(SketchPosition.ALIGN[i]);form.addView(align);
                align.setOnClickListener(v->{ws.closePropertyEditor();ws.sketchGhost.clear();
                    try{SketchPosition.Result r=SketchPosition.align(ws.project.sketch,ws.project.coincidences,source.id,group,alignMode,lock.isChecked());
                        if(ws.applySolvedSketch(r.sketch,r.links))ws.operationMessage="Wyrównano: "+SketchPosition.ALIGN[alignMode].toLowerCase(java.util.Locale.ROOT)+(r.added>0?" · zablokowano wymiarem":"");}
                    catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
                    ws.invalidate();});
            }
        }
        java.util.function.Supplier<SketchPosition.Result> compute=()->{
            int anchor=SketchPosition.anchorOf(source,point.getSelectedItemPosition());boolean canLock=lock.isChecked()&&SketchPosition.lockable(source,anchor);
            return SketchPosition.place(ws.project.sketch,ws.project.coincidences,source.id,anchor,pointRef?refKey:null,Math.max(0,refPoint),ws.parseCoordinate(fx),ws.parseCoordinate(fy),canLock,canLock);
        };
        point.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            boolean first=true;
            public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long rowId){
                if(first){first=false;return;}
                int anchor=SketchPosition.anchorOf(source,position);double[] o=SketchPosition.offset(ws.project.sketch,source.id,anchor,pointRef?refKey:null,Math.max(0,refPoint));
                fx.setText(Units.input(o[0],ws.project.units));fy.setText(Units.input(o[1],ws.project.units));
                lock.setEnabled(SketchPosition.lockable(source,anchor));if(!lock.isEnabled())lock.setChecked(false);}
            public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        ws.new PropertyBuilder().setTitle("Położenie elementu").setView(form).setNegativeButton("Anuluj",(d,w)->{ws.sketchGhost.clear();ws.invalidate();}).setPositiveButton("Zastosuj",(d,w)->{
            ws.sketchGhost.clear();
            try{SketchPosition.Result r=compute.get();
                if(ws.applySolvedSketch(r.sketch,r.links))ws.operationMessage="Położenie: X "+ws.formatMm(ws.parseCoordinate(fx))+", Y "+ws.formatMm(ws.parseCoordinate(fy))+(r.added>0?" · wymiary sterujące widoczne na szkicu":"");}
            catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
            ws.invalidate();
        }).show();
        Runnable ghost=()->{ws.sketchGhost.clear();try{ws.showSolvedGhost(compute.get().sketch);}catch(IllegalArgumentException ignored){}ws.invalidate();};
        ws.watchFields(form,ghost);
    }
    void pathPatternTap(float x,float y) {
        String id=ws.pathPatternSource;SketchPrimitive path=ws.findSketchAt(x,y);
        if(path==null||path.id.equals(id)){ws.operationMessage="Dotknij ścieżki (innego elementu niż powielany)";ws.invalidate();return;}
        ws.pathPatternSource=null;showPathPattern(id,path.id);
    }
    /** Copies of an element at equal steps along a path, with live preview. */
    void showPathPattern(String id,String pathId) {
        SketchPrimitive source=null,path=null;for(SketchPrimitive p:ws.project.sketch){if(p.id.equals(id))source=p;if(p.id.equals(pathId))path=p;}
        if(source==null||path==null)return;final SketchPrimitive src=source,way=path;
        double length;try{length=SketchPathPattern.path(way).length;}catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();ws.invalidate();return;}
        ws.beginSketchWork();ws.mode="Edytuj szkic";
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(ws.getContext());info.setText("Ścieżka: "+sketchEntityName(way)+" · długość "+ws.formatMm(length)+(way.closedGeometry()?" (zamknięta: elementy równo dookoła)":" (otwarta: elementy od początku do końca)"));form.addView(info);
        EditText count=ws.coordinateInput("Liczba elementów",5);android.widget.TextView label=new android.widget.TextView(ws.getContext());label.setText("Liczba elementów (z oryginałem)");form.addView(label);form.addView(count);
        android.widget.CheckBox start=new android.widget.CheckBox(ws.getContext());start.setText("Przenieś element na początek ścieżki");start.setChecked(true);form.addView(start);
        android.widget.CheckBox turn=new android.widget.CheckBox(ws.getContext());turn.setText("Obracaj zgodnie z kierunkiem ścieżki");turn.setChecked(false);form.addView(turn);
        java.util.function.Supplier<List<SketchPrimitive>> compute=()->{
            double n=ws.parseCoordinate(count);if(n!=Math.rint(n))throw new IllegalArgumentException("Liczba elementów musi być całkowita");
            SketchPrimitive base=src;if(start.isChecked()){double[] d=SketchPathPattern.snapToStart(src,way);base=SketchTransform.move(src,d[0],d[1]);}
            List<SketchPrimitive> result=new ArrayList<>();result.add(base);result.addAll(SketchPathPattern.copies(base,way,(int)n,turn.isChecked()));return result;
        };
        ws.new PropertyBuilder().setTitle("Szyk po ścieżce").setView(form).setNegativeButton("Anuluj",(d,w)->{ws.sketchGhost.clear();ws.invalidate();}).setPositiveButton("Utwórz",(d,w)->{
            ws.sketchGhost.clear();
            try{List<SketchPrimitive> made=compute.get();SketchPrimitive base=made.get(0);
                List<SketchPrimitive> geometry=base==src?new ArrayList<>(ws.project.sketch):SketchConstraints.edit(ws.project.sketch,ws.project.coincidences,src.id,base);
                geometry.addAll(made.subList(1,made.size()));
                if(ws.applySolvedSketch(geometry,new ArrayList<>(ws.project.coincidences)))ws.operationMessage="Szyk po ścieżce: "+made.size()+" el. (kopie są niezależne)";}
            catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
            ws.invalidate();}).show();
        Runnable ghost=()->{ws.sketchGhost.clear();try{ws.sketchGhost.addAll(compute.get());}catch(IllegalArgumentException ignored){}ws.invalidate();};
        ws.watchFields(form,ghost);ghost.run();
    }
    /** "Położenie" of the multi-selection: a point of the common box at X/Y from a reference, or alignment. */
    void showGroupPosition(int pointChoice,String refId,int refAnchor) {
        ws.sketchMulti.removeIf(id->{for(SketchPrimitive p:ws.project.sketch)if(p.id.equals(id))return false;return true;});
        if(ws.sketchMulti.isEmpty()){ws.operationMessage="Zaznacz elementy (Zaznacz wiele)";ws.invalidate();return;}
        SketchPrimitive reference=null;for(SketchPrimitive p:ws.project.sketch)if(p.id.equals(refId))reference=p;
        final SketchPrimitive ref=reference;final String refKey=ref==null?null:ref.id;
        final int refPoint=ref!=null&&refAnchor>=0&&refAnchor<SketchConstraints.anchorCount(ref)?refAnchor:-1;
        final List<String> group=ref!=null&&ws.positionGroup!=null&&ws.positionGroup.contains(ref.id)?new ArrayList<>(ws.positionGroup):java.util.Collections.singletonList(ref==null?"":ref.id);ws.positionGroup=null;
        ws.beginSketchWork();ws.mode="Edytuj szkic";
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView what=new android.widget.TextView(ws.getContext());what.setText("Zaznaczone elementy: "+ws.sketchMulti.size()+" (przesuwane razem)");form.addView(what);
        android.widget.Spinner point=new android.widget.Spinner(ws.getContext());point.setAdapter(new android.widget.ArrayAdapter<>(ws.getContext(),android.R.layout.simple_spinner_dropdown_item,SketchPosition.GROUP_POINTS));
        point.setSelection(Math.max(0,Math.min(pointChoice,SketchPosition.GROUP_POINTS.length-1)));form.addView(point);
        boolean pointRef=ref==null||refPoint>=0;
        android.widget.TextView refLabel=new android.widget.TextView(ws.getContext());refLabel.setText("Odniesienie: "+(group.size()>1?"ściana bryły (rzut)":ref==null?"początek układu szkicu":sketchEntityName(ref)));form.addView(refLabel);
        android.widget.Button pick=new android.widget.Button(ws.getContext());pick.setText("Wskaż odniesienie na szkicu lub bryle…");form.addView(pick);
        pick.setOnClickListener(v->{ws.closePropertyEditor();ws.sketchGhost.clear();ws.positionPickId=ws.GROUP_ID;ws.positionPickPoint=point.getSelectedItemPosition();
            if(ws.project.hasSolid()&&ws.project.importedMeshSource==null&&ws.projectionEdges.length==0)ws.loadProjectionEdges();
            ws.operationMessage="Dotknij punktu lub elementu szkicu, krawędzi lub ściany bryły albo początku układu";ws.invalidate();});
        double[] now=SketchPosition.groupPoint(ws.project.sketch,ws.sketchMulti,point.getSelectedItemPosition()),r=SketchPosition.reference(ws.project.sketch,pointRef?refKey:null,Math.max(0,refPoint));
        EditText fx=ws.labeledCoordinate(form,"Odległość X [mm]",now[0]-r[0]),fy=ws.labeledCoordinate(form,"Odległość Y [mm]",now[1]-r[1]);
        if(ref!=null) {
            android.widget.TextView alignLabel=new android.widget.TextView(ws.getContext());alignLabel.setText("Wyrównaj zaznaczenie do: "+(group.size()>1?"obrysu ściany":sketchEntityName(ref)));form.addView(alignLabel);
            for(int i=0;i<SketchPosition.ALIGN.length;i++) {
                final int alignMode=i;android.widget.Button align=new android.widget.Button(ws.getContext());align.setText(SketchPosition.ALIGN[i]);form.addView(align);
                align.setOnClickListener(v->{ws.closePropertyEditor();ws.sketchGhost.clear();
                    try{SketchPosition.Result res=SketchPosition.alignGroup(ws.project.sketch,ws.project.coincidences,ws.sketchMulti,group,alignMode);
                        if(ws.applySolvedSketch(res.sketch,res.links))ws.operationMessage="Wyrównano zaznaczenie: "+SketchPosition.ALIGN[alignMode].toLowerCase(java.util.Locale.ROOT);}
                    catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
                    ws.invalidate();});
            }
        }
        java.util.function.Supplier<SketchPosition.Result> compute=()->SketchPosition.placeGroup(ws.project.sketch,ws.project.coincidences,ws.sketchMulti,point.getSelectedItemPosition(),pointRef?refKey:null,Math.max(0,refPoint),ws.parseCoordinate(fx),ws.parseCoordinate(fy));
        point.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            boolean first=true;
            public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long rowId){
                if(first){first=false;return;}
                double[] n=SketchPosition.groupPoint(ws.project.sketch,ws.sketchMulti,position),rr=SketchPosition.reference(ws.project.sketch,pointRef?refKey:null,Math.max(0,refPoint));
                fx.setText(Units.input(n[0]-rr[0],ws.project.units));fy.setText(Units.input(n[1]-rr[1],ws.project.units));}
            public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        ws.new PropertyBuilder().setTitle("Położenie zaznaczenia").setView(form).setNegativeButton("Anuluj",(d,w)->{ws.sketchGhost.clear();ws.invalidate();}).setPositiveButton("Zastosuj",(d,w)->{
            ws.sketchGhost.clear();
            try{SketchPosition.Result res=compute.get();if(ws.applySolvedSketch(res.sketch,res.links))ws.operationMessage="Przesunięto zaznaczenie ("+ws.sketchMulti.size()+" el.)";}
            catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
            ws.invalidate();
        }).show();
        Runnable ghost=()->{ws.sketchGhost.clear();try{SketchPosition.Result res=compute.get();for(SketchPrimitive p:res.sketch)if(ws.sketchMulti.contains(p.id))ws.sketchGhost.add(p);}catch(IllegalArgumentException ignored){}ws.invalidate();};
        ws.watchFields(form,ghost);
    }
    /** Shift of the multi-selection by exact X/Y. */
    void showGroupMove() {
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        EditText fx=ws.labeledCoordinate(form,"Przesunięcie X [mm]",0),fy=ws.labeledCoordinate(form,"Przesunięcie Y [mm]",0);
        java.util.function.Supplier<SketchPosition.Result> compute=()->SketchPosition.moveGroup(ws.project.sketch,ws.project.coincidences,ws.sketchMulti,ws.parseCoordinate(fx),ws.parseCoordinate(fy));
        ws.new PropertyBuilder().setTitle("Przesuń zaznaczenie").setView(form).setNegativeButton("Anuluj",(d,w)->{ws.sketchGhost.clear();ws.invalidate();}).setPositiveButton("Przesuń",(d,w)->{
            ws.sketchGhost.clear();try{SketchPosition.Result res=compute.get();if(ws.applySolvedSketch(res.sketch,res.links))ws.operationMessage="Przesunięto "+ws.sketchMulti.size()+" el.";}catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}ws.invalidate();}).show();
        ws.watchFields(form,()->{ws.sketchGhost.clear();try{for(SketchPrimitive p:compute.get().sketch)if(ws.sketchMulti.contains(p.id))ws.sketchGhost.add(p);}catch(IllegalArgumentException ignored){}ws.invalidate();});
    }
    /** Reference pick of "Położenie": origin, a point, an element or a body edge (projected first). */
    void positionPickTap(float x,float y) {
        String id=ws.positionPickId;int choice=ws.positionPickPoint;ws.positionPickId=null;
        PointF origin=ws.projectSketchPoint(0,0);
        if(Math.hypot(x-origin.x,y-origin.y)<ws.dp(18)){showSketchPosition(id,choice,null,0);return;}
        SketchPrimitive best=null;int anchor=-1;double distance=ws.dp(18);
        for(SketchPrimitive p:ws.project.sketch){if(p.id.equals(id)||!ws.sketchEntityVisible(p)||ws.GROUP_ID.equals(id)&&ws.sketchMulti.contains(p.id))continue;
            for(int i=0;i<SketchConstraints.anchorCount(p);i++){double[] a=SketchConstraints.anchor(p,i);PointF s=ws.projectSketchPoint(a[0],a[1]);double d=Math.hypot(x-s.x,y-s.y);if(d<distance){distance=d;best=p;anchor=i;}}}
        if(best!=null){showSketchPosition(id,choice,best.id,anchor);return;}
        SketchPrimitive entity=ws.findSketchAt(x,y);
        if(entity!=null&&!entity.id.equals(id)&&!(ws.GROUP_ID.equals(id)&&ws.sketchMulti.contains(entity.id))){showSketchPosition(id,choice,entity.id,ws.nearestAnchor(entity,x,y));return;}
        // A body edge: ws.project it into the sketch (fixed reference geometry), then use it.
        int edge=-1;double edgeDistance=ws.dp(18);
        for(int i=0;i+15<ws.projectionEdges.length;i+=16) {
            double[] a=ws.projectWorldPoint(ws.projectionEdges[i+9],ws.projectionEdges[i+10],ws.projectionEdges[i+11]),b=ws.projectWorldPoint(ws.projectionEdges[i+12],ws.projectionEdges[i+13],ws.projectionEdges[i+14]);
            double d=ws.sketchScreenSegmentDistance(x,y,a[0],a[1],b[0],b[1]);if(d<edgeDistance){edgeDistance=d;edge=(int)ws.projectionEdges[i+15];}
        }
        if(edge>=0) {
            int before=ws.project.sketch.size();java.util.Set<String> known=new java.util.HashSet<>();for(SketchPrimitive p:ws.project.sketch)known.add(p.id);
            ws.applyProjection(java.util.Collections.singletonList(edge));
            for(SketchPrimitive p:ws.project.sketch)if(!known.contains(p.id)){showSketchPosition(id,choice,p.id,ws.nearestAnchor(p,x,y));ws.operationMessage="Zrzutowano krawędź bryły jako odniesienie";return;}
            if(ws.project.sketch.size()==before){
                // Already projected earlier: use the matching sketch element.
                SketchPrimitive again=ws.findSketchAt(x,y);if(again!=null&&!again.id.equals(id)){showSketchPosition(id,choice,again.id,ws.nearestAnchor(again,x,y));return;}
            }
            ws.invalidate();return;
        }
        int face=ws.project.hasSolid()?ws.findFaceAt(x,y):-1;
        if(face>=0) {
            List<String> created=ws.projectFace(face);
            if(!created.isEmpty()){ws.positionGroup=created;showSketchPosition(id,choice,created.get(0),0);ws.operationMessage="Zrzutowano ścianę bryły ("+created.size()+" kr.) jako odniesienie";return;}
        }
        ws.operationMessage="Nie wskazano odniesienia. Dotknij punktu, elementu, krawędzi lub ściany bryły albo początku układu.";
        ws.positionPickId=id;ws.positionPickPoint=choice;ws.invalidate();
    }
}
