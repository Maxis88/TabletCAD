package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Sketch patterns (linear, rectangular, circular) with linked copies, and their editing. */
final class SketchPatternTools {
    private final CadWorkspaceView ws;
    SketchPatternTools(CadWorkspaceView view){ws=view;}
    /** Linear/rectangular or circular pattern of the selected sketch element (independent copies). */
    void show(boolean circular){show(circular,null,null,null);}
    /** Linked copies of one pattern: link group id → source id; null when the entity is in no pattern. */
    SketchConstraints.Link patternLink(String entity) {
        for(SketchConstraints.Link l:ws.project.coincidences)if(l.kind.equals("rigid")&&NonlinearConstraints.meta(l.axis,"g")!=null&&(l.a.equals(entity)||l.b.equals(entity)))return l;
        return null;
    }
    /** "Edytuj szyk": reopens the pattern of the selected source or copy with its parameters and regenerates the copies. */
    void edit() {
        SketchConstraints.Link link=ws.selectedSketchId==null?null:patternLink(ws.selectedSketchId);
        if(link==null){ws.operationMessage="Zaznacz element powiązanego szyku albo lustra (Edytuj), a następnie wybierz Edytuj szyk";ws.invalidate();return;}
        String[] p=NonlinearConstraints.meta(link.axis,"p").split(",");String group=NonlinearConstraints.meta(link.axis,"g");
        SketchPrimitive source=null;for(SketchPrimitive e:ws.project.sketch)if(e.id.equals(link.a))source=e;if(source==null)return;
        if(p[0].equals("M")){ws.operationMessage="Lustro nie ma parametrów do edycji – usuń kopię i utwórz lustro ponownie";ws.invalidate();return;}
        double[] values=new double[4];for(int i=0;i<4;i++)values[i]=Double.parseDouble(p[i+1]);
        ws.selectedSketchId=source.id;show(p[0].equals("C"),source,group,values);
    }
    void show(boolean circular,SketchPrimitive edited,String group,double[] previous) {
        SketchPrimitive source=edited;
        if(source==null)for(SketchPrimitive entity:ws.project.sketch)if(entity.id.equals(ws.selectedSketchId))source=entity;
        if(source==null){ws.operationMessage="Zaznacz element szkicu (Edytuj), a następnie wybierz szyk";return;}
        final SketchPrimitive selected=source;
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        final EditText first,second,third,fourth;
        double[] v=previous!=null?previous:circular?new double[]{6,360,0,0}:new double[]{3,20,1,20};
        if(circular) {
            first=ws.coordinateInput("Liczba elementów (z oryginałem)",v[0]);second=ws.coordinateInput("Kąt całkowity [°]",v[1]);
            third=ws.coordinateInput("Środek X [mm]",v[2]);fourth=ws.coordinateInput("Środek Y [mm]",v[3]);
        }else {
            first=ws.coordinateInput("Liczba w kierunku X",v[0]);second=ws.coordinateInput("Odstęp X [mm]",v[1]);
            third=ws.coordinateInput("Liczba w kierunku Y",v[2]);fourth=ws.coordinateInput("Odstęp Y [mm]",v[3]);
        }
        form.addView(first);form.addView(second);form.addView(third);form.addView(fourth);
        if(circular){ws.slider(form,first,2,36,1);ws.slider(form,second,1,360,1);}
        else {ws.slider(form,first,1,30,1);ws.slider(form,second,-2*ws.modelExtent(),2*ws.modelExtent(),.5);ws.slider(form,third,1,30,1);ws.slider(form,fourth,-2*ws.modelExtent(),2*ws.modelExtent(),.5);}
        android.widget.TextView status=new android.widget.TextView(ws.getContext());
        status.setText(circular?"Kąt 360° dzieli okrąg równo. Środek w układzie szkicu.":"Liczby obejmują oryginał. Ujemny odstęp zmienia kierunek.");form.addView(status);
        ws.new PropertyBuilder().setTitle((group!=null?"Edytuj szyk · ":circular?"Szyk kołowy · ":"Szyk liniowy · ")+selected.type).setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try {
                List<SketchPrimitive> copies;
                double a=ws.parseCoordinate(first),b=ws.parseCoordinate(second),c=ws.parseCoordinate(third),e=ws.parseCoordinate(fourth);
                if(a!=Math.rint(a)||(!circular&&c!=Math.rint(c)))throw new IllegalArgumentException("Liczba elementów musi być całkowita");
                copies=circular?SketchPattern.circular(selected,(int)a,c,e,b):SketchPattern.grid(selected,(int)a,(int)c,b,e);
                ws.checkpoint();
                if(group!=null) {
                    // Regenerate: the old copies of this pattern (and their links) are replaced.
                    java.util.Set<String> old=new java.util.HashSet<>();for(SketchConstraints.Link l:ws.project.coincidences)if(l.kind.equals("rigid")&&group.equals(NonlinearConstraints.meta(l.axis,"g")))old.add(l.b);
                    for(String id:old)if(ws.project.committedProfileIds.contains(id)||id.equals(ws.project.featureProfileId)||id.equals(ws.project.featureBaseProfileId))throw new IllegalArgumentException("Kopia szyku jest profilem operacji – nie można jej zastąpić");
                    ws.project.sketch.removeIf(p->old.contains(p.id));ws.project.pruneCoincidences();
                }
                ws.project.sketch.addAll(copies);ws.mode="Edytuj szkic";
                final double pcx=c,pcy=e;final String patternGroup=group!=null?group:UUID.randomUUID().toString();
                final String parameters=(circular?"C,":"L,")+a+","+b+","+c+","+e;
                int linked=ws.linkCopies(selected,copies,copy->{double[] p0=ws.firstPoint(selected),q0=ws.firstPoint(copy);
                    if(!circular)return NonlinearConstraints.withGroup(NonlinearConstraints.translation(q0[0]-p0[0],q0[1]-p0[1]),patternGroup,parameters);
                    double[] p=p0,q=q0;if(Math.hypot(p[0]-pcx,p[1]-pcy)<1e-9){p=ws.secondPoint(selected);q=ws.secondPoint(copy);}
                    return NonlinearConstraints.withGroup(NonlinearConstraints.rotation(pcx,pcy,Math.toDegrees(Math.atan2(q[1]-pcy,q[0]-pcx)-Math.atan2(p[1]-pcy,p[0]-pcx))),patternGroup,parameters);});
                ws.projectRepository.save(ws.project);
                ws.operationMessage="Utworzono szyk: "+(copies.size()+1)+" elementów"+(linked==copies.size()?"; kopie powiązane ze źródłem ⧉.":linked>0?"; powiązano "+linked+" kopii.":"; kopie niezależne (tego typu geometrii nie można powiązać).")+" Zakończ zatwierdza szkic.";
            }catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
            ws.invalidate();
        }).show();
        // Live preview of the copies (dashed green) while the values change.
        Runnable ghost=()->{ws.sketchGhost.clear();try{double a=ws.parseCoordinate(first),b=ws.parseCoordinate(second),c=ws.parseCoordinate(third),e=ws.parseCoordinate(fourth);
            if(a==Math.rint(a)&&(circular||c==Math.rint(c)))ws.sketchGhost.addAll(circular?SketchPattern.circular(selected,(int)a,c,e,b):SketchPattern.grid(selected,(int)a,(int)c,b,e));}catch(IllegalArgumentException ignored){}ws.invalidate();};
        ws.watchFields(form,ghost);ghost.run();
    }

}
