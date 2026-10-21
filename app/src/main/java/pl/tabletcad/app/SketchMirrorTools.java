package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Sketch mirror (about lines and axes) and the geometry tool menu */
final class SketchMirrorTools {
    private final CadWorkspaceView ws;
    SketchMirrorTools(CadWorkspaceView view){ws=view;}
    void showSketchMirror() {
        List<SketchPrimitive> sources=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(SketchPrimitive entity:ws.project.sketch) {
            if(entity.id.equals(ws.selectedSketchId)){showSketchMirrorAxes(entity);return;}
            sources.add(entity);labels.add("Element "+sources.size()+" · "+entity.type);
        }
        if(sources.isEmpty()){ws.operationMessage="Najpierw narysuj element szkicu do odbicia";ws.invalidate();return;}
        if(sources.size()==1){showSketchMirrorAxes(sources.get(0));return;}
        ws.new PropertyBuilder().setTitle("Lustro · wybierz element do skopiowania")
            .setItems(labels.toArray(new String[0]),(d,w)->showSketchMirrorAxes(sources.get(w)))
            .setNegativeButton("Anuluj",null).show();
    }

    void showSketchMirrorAxes(SketchPrimitive source) {
        List<double[]> axes=new ArrayList<>();List<String> labels=new ArrayList<>();
        axes.add(new double[]{0,0,1,0});labels.add("Oś X szkicu");
        axes.add(new double[]{0,0,0,1});labels.add("Oś Y szkicu");
        final List<String> axisIds=new ArrayList<>(java.util.Arrays.asList("@X","@Y"));
        for(SketchPrimitive p:ws.project.sketch)if((p.type.equals("Pomocnicza")||p.type.equals("Linia"))&&!p.id.equals(source.id)) {
            axes.add(new double[]{p.x1,p.y1,p.x2,p.y2});labels.add("Oś z linii "+(axes.size()-2));axisIds.add(p.id);
        }
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.Spinner axisChoice=ws.datumTools.choice(form,"Oś odbicia",labels,0);
        android.widget.TextView hint=new android.widget.TextView(ws.getContext());hint.setText("Podgląd kopii (zielona, przerywana) zmienia się razem z wyborem osi.");form.addView(hint);
        ws.new PropertyBuilder().setTitle("Kopia lustrzana · "+source.type).setView(form).setNegativeButton("Anuluj",null)
            .setPositiveButton("Zastosuj",(d,button)-> {int w=axisChoice.getSelectedItemPosition();
                try {
                    double[] axis=axes.get(w);
                    SketchPrimitive copy=SketchMirror.copy(source,UUID.randomUUID().toString(),axis[0],axis[1],axis[2],axis[3]);
                    ws.checkpoint();ws.project.sketch.add(copy);ws.selectedSketchId=copy.id;ws.mode="Edytuj szkic";
                    // Lines and circles: symmetry relation to the axis entity (follows a moved axis line); other geometry: fixed mirror transform.
                    int linked=0;
                    if(source.type.equals("Linia")||source.type.equals("Okrąg")) {
                        for(int order=0;order<2&&linked==0;order++)try{SketchConstraints.Link rel=new SketchConstraints.Link("symmetric",source.id,0,copy.id,order,0,0,axisIds.get(w));NonlinearConstraints.validate(ws.project.sketch,rel);ws.project.coincidences.add(rel);linked=1;}catch(IllegalArgumentException ignored){linked=0;}
                    }
                    if(linked==0)linked=ws.linkCopies(source,java.util.Collections.singletonList(copy),c->NonlinearConstraints.withGroup(NonlinearConstraints.mirror(axis[0],axis[1],axis[2],axis[3]),UUID.randomUUID().toString(),"M,0,0,0,0"));
                    ws.projectRepository.save(ws.project);
                    ws.operationMessage=linked>0?(source.type.equals("Linia")||source.type.equals("Okrąg")?"Utworzono kopię lustrzaną z więzem symetrii ↔︎ – podąża za źródłem i za osią. Zakończ zatwierdza szkic.":"Utworzono powiązaną kopię lustrzaną ⧉ – zmiana źródła zmienia kopię (oś stała). Zakończ zatwierdza szkic."):"Utworzono niezależną kopię lustrzaną (tego typu geometrii nie można powiązać). Zakończ zatwierdza szkic.";
                }catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
                ws.invalidate();
            }).show();
        Runnable ghost=()->{ws.sketchGhost.clear();try{double[] a=axes.get(axisChoice.getSelectedItemPosition());ws.sketchGhost.add(SketchMirror.copy(source,"ghost",a[0],a[1],a[2],a[3]));}catch(IllegalArgumentException ignored){}ws.invalidate();};
        ws.watchFields(form,ghost);ghost.run();
    }

    void showSketchGeometryTools() {
        SketchPrimitive selected=null;
        for(SketchPrimitive p:ws.project.sketch)if(p.id.equals(ws.selectedSketchId))selected=p;
        if(selected==null) {
            ws.operationMessage="Zaznacz element szkicu, a następnie wybierz Geometria";
            ws.invalidate();return;
        }
        SketchPrimitive source=selected;
        List<String> labels=new ArrayList<>();
        List<Runnable> actions=new ArrayList<>();
        if(ws.project.hasCoincidence(source.id)) {
            labels.add("Zarządzaj więzami tego elementu");
            actions.add(()->ws.sketchConstraintTools.showEntityConstraints(source));
        }
        {
            labels.add(source.construction?"Użyj jako zwykła geometria":"Ustaw jako geometrię pomocniczą");
            actions.add(()->{
                if(ws.isProfileUsed(source.id)&&!SketchTransactions.active(ws.project)){ws.operationMessage="Element jest używany przez bryłę";ws.invalidate();return;}
                ws.replaceSketchEntity(source,SketchEditing.toggleConstruction(source),true);
            });
        }
        if(source.vertices!=null||source.type.equals("Prostokąt")||source.type.equals("Kontur krzywy")) {
            labels.add("Rozbij na osobne odcinki");
            actions.add(()->{
                if(ws.project.hasCoincidence(source.id)){ws.operationMessage="Najpierw usuń połączenia punktów tej figury";ws.invalidate();return;}
                if(ws.isProfileUsed(source.id)&&!SketchTransactions.active(ws.project)) {
                    ws.operationMessage="Profil jest używany przez bryłę — edytuj jego wierzchołki zamiast rozbijać";
                    ws.invalidate();return;
                }
                try {
                    List<SketchPrimitive> edges=SketchEditing.explode(source);
                    int index=ws.project.sketch.indexOf(source);if(index<0)return;
                    ws.checkpoint();SketchTransactions.replace(ws.project,source.id,edges);
                    ws.project.sketch.remove(index);ws.project.sketch.addAll(index,edges);
                    ws.project.coincidences.removeIf(l->l.kind.equals("rigid")&&l.uses(source.id));ws.project.pruneCoincidences();
                    ws.selectedSketchId=edges.get(0).id;
                    ws.projectRepository.save(ws.project);
                    ws.operationMessage="Rozbito figurę: "+edges.size()+" odcinków. Możesz je edytować, przycinać i usuwać.";
                    ws.invalidate();
                }catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();ws.invalidate();}
            });
        }
        labels.add(0,"Położenie (odległość / wyrównanie)…");actions.add(0,()->ws.sketchPositionTools.showSketchPosition(source.id,0,null,0));
        labels.add("Edytuj wymiary / kształt");actions.add(()->ws.showSketchEntityEditor(source));
        if(SketchConstraints.anchorCount(source)>0){labels.add(source.type.equals("Okrąg")?"Wymiaruj położenie środka (X/Y od początku)":"Wymiaruj położenie punktu 1 (X/Y od początku)");actions.add(()->ws.addOriginPositionDimensions(source));}
        labels.add("Usuń element");actions.add(()->ws.deleteSketchEntity(source.id));
        ws.new PropertyBuilder().setTitle("Geometria · "+source.type)
            .setItems(labels.toArray(new String[0]),(dialog,which)->actions.get(which).run())
            .setNegativeButton("Zamknij",null).show();
    }

}
