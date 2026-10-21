package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Sketch relations and constraints, position dimensions and the coordinate / dimension editors */
final class SketchConstraintTools {
    private final CadWorkspaceView ws;
    SketchConstraintTools(CadWorkspaceView view){ws=view;}
    void coincidenceTap(float x,float y) {
        if(NonlinearConstraints.relation(ws.constraintKind)){relationTap(x,y);return;}
        if(ws.constraintKind.equals("horizontal")||ws.constraintKind.equals("vertical")) {axisConstraintTap(x,y);return;}
        SketchPrimitive picked=null;int anchor=-1;double best=ws.dp(18);
        for(SketchPrimitive p:ws.project.sketch)for(int i=0;i<SketchConstraints.anchorCount(p);i++) {
            if(p.id.equals(ws.coincidenceFirst))continue;
            double[] a=SketchConstraints.anchor(p,i);if(!ws.sketchPointVisible(a[0],a[1]))continue;
            PointF point=ws.projectSketchPoint(a[0],a[1]);double distance=Math.hypot(x-point.x,y-point.y);
            if(distance<best){best=distance;picked=p;anchor=i;}
        }
        if(SketchConstraints.position(ws.constraintKind)&&(picked!=null||ws.coincidenceFirst!=null)){positionDimensionTap(x,y,picked,anchor);return;}
        if(picked==null){ws.operationMessage="Dotknij punktu innego elementu; punkty są oznaczone kółkami";ws.invalidate();return;}
        if(ws.constraintKind.equals("fixed")) {
            addSketchConstraint(SketchConstraints.Link.fixed(picked,anchor));return;
        }
        if(ws.coincidenceFirst==null) {
            ws.coincidenceFirst=picked.id;ws.coincidenceAnchor=anchor;
            ws.operationMessage="Wybrano pierwszy punkt. Dotknij punktu drugiego elementu — zostanie dołączony do pierwszego.";
        } else {
            try {
                SketchConstraints.Link link=new SketchConstraints.Link(ws.coincidenceFirst,ws.coincidenceAnchor,picked.id,anchor);
                List<SketchPrimitive> solved=SketchConstraints.connect(ws.project.sketch,ws.project.coincidences,link);
                List<SketchConstraints.Link> links=new ArrayList<>(ws.project.coincidences);links.add(link);
                if(ws.applySolvedSketch(solved,links))ws.operationMessage="Połączono punkty. Wybierz kolejną parę lub Edytuj, aby przesuwać.";
            }catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
            ws.coincidenceFirst=null;
        }
        ws.invalidate();
    }

    /** Position dimension picking: first point, then a second point or the sketch origin. */
    void positionDimensionTap(float x,float y,SketchPrimitive picked,int anchor) {
        PointF origin=ws.projectSketchPoint(0,0);boolean nearOrigin=Math.hypot(x-origin.x,y-origin.y)<ws.dp(18);
        int axis=ws.constraintKind.equals("dimX")?0:1;
        if(ws.coincidenceFirst==null) {
            ws.coincidenceFirst=picked.id;ws.coincidenceAnchor=anchor;ws.selectedSketchId=picked.id;
            ws.operationMessage="Wskaż drugi punkt albo początek układu szkicu (przecięcie osi)";ws.invalidate();return;
        }
        if(picked==null&&!nearOrigin){ws.operationMessage="Wskaż punkt innego elementu albo początek układu szkicu";ws.invalidate();return;}
        SketchPrimitive first=null;for(SketchPrimitive p:ws.project.sketch)if(p.id.equals(ws.coincidenceFirst))first=p;
        String firstId=ws.coincidenceFirst;int firstAnchor=ws.coincidenceAnchor;ws.coincidenceFirst=null;
        if(first==null){ws.invalidate();return;}
        SketchConstraints.Link link;
        if(nearOrigin||picked==null||(picked.id.equals(firstId)&&anchor==firstAnchor))link=new SketchConstraints.Link(ws.constraintKind,firstId,firstAnchor,firstId,firstAnchor,SketchConstraints.anchor(first,firstAnchor)[axis],0);
        else link=new SketchConstraints.Link(ws.constraintKind,firstId,firstAnchor,picked.id,anchor,SketchConstraints.anchor(picked,anchor)[axis]-SketchConstraints.anchor(first,firstAnchor)[axis],0);
        addSketchConstraint(link);
        if(ws.project.coincidences.contains(link))ws.operationMessage="Dodano wymiar położenia. Dwukrotnie dotknij wartości, aby ją zmienić.";
    }

    void addSketchConstraint(SketchConstraints.Link rule) {
        try {
            List<SketchPrimitive> solved=SketchConstraints.connect(ws.project.sketch,ws.project.coincidences,rule);
            List<SketchConstraints.Link> links=new ArrayList<>(ws.project.coincidences);links.add(rule);
            if(ws.applySolvedSketch(solved,links))ws.operationMessage=NonlinearConstraints.relation(rule.kind)?"Zapisano relację "+NonlinearConstraints.symbol(rule.kind):rule.kind.equals("fixed")?"Punkt unieruchomiony. Aby odblokować: Geometria → Usuń więzy.":"Zapisano kierunek odcinka. Edytuj pozwala zmienić długość bez zmiany kierunku.";
        }catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
        ws.invalidate();
    }

    void axisConstraintTap(float x,float y) {
        SketchPrimitive p=ws.findSketchAt(x,y);
        if(p==null){ws.operationMessage="Dotknij odcinka szkicu";ws.invalidate();return;}
        int first=0,second=1;
        if(p.vertices!=null) {
            double best=Double.POSITIVE_INFINITY;
            for(int i=0;i<p.vertices.length;i+=2) {
                int j=(i+2)%p.vertices.length;
                double d=ws.sketchSegmentDistance(x,y,p.vertices[i],p.vertices[i+1],p.vertices[j],p.vertices[j+1]);
                if(d<best){best=d;first=i/2;second=j/2;}
            }
        } else if(!p.type.equals("Linia")&&!p.type.equals("Pomocnicza")) {
            ws.operationMessage="Wybierz linię lub bok polilinii. Prostokąt ma już boki poziome i pionowe.";ws.invalidate();return;
        }
        addSketchConstraint(SketchConstraints.Link.axis(p.id,first,second,ws.constraintKind.equals("horizontal")));
    }

    void relationTap(float x,float y) {
        SketchPrimitive picked=ws.findSketchAt(x,y);int anchor=0;
        if(ws.constraintKind.equals("distance")&&ws.coincidenceFirst!=null) {
            // Second pick: a point of another element (point–point) or a line (perpendicular distance).
            SketchPrimitive first=null;for(SketchPrimitive p:ws.project.sketch)if(p.id.equals(ws.coincidenceFirst))first=p;
            SketchPrimitive other=null;int otherAnchor=-1;double best=ws.dp(18);
            for(SketchPrimitive p:ws.project.sketch){if(p.id.equals(ws.coincidenceFirst))continue;for(int i=0;i<SketchConstraints.anchorCount(p);i++){double[] point=SketchConstraints.anchor(p,i);PointF screen=ws.projectSketchPoint(point[0],point[1]);double distance=Math.hypot(screen.x-x,screen.y-y);if(distance<best){best=distance;other=p;otherAnchor=i;}}}
            String firstId=ws.coincidenceFirst;int firstAnchor=ws.coincidenceAnchor;
            if(first==null){ws.coincidenceFirst=null;ws.invalidate();return;}
            if(other!=null){ws.coincidenceFirst=null;ws.showDistanceEditor(-1,new SketchConstraints.Link("distance",firstId,firstAnchor,other.id,otherAnchor,0,1));return;}
            SketchPrimitive line=ws.findSketchAt(x,y);
            if(line!=null&&!line.id.equals(firstId)&&(line.type.equals("Linia")||line.type.equals("Pomocnicza"))){ws.coincidenceFirst=null;ws.showDistanceEditor(-1,new SketchConstraints.Link("distance",firstId,firstAnchor,line.id,0,0,0));return;}
            ws.operationMessage="Wskaż punkt innego elementu albo linię";ws.invalidate();return;
        }
        if((ws.constraintKind.equals("pointOn")||ws.constraintKind.equals("distance"))&&ws.coincidenceFirst==null) {
            double best=ws.dp(18);picked=null;
            for(SketchPrimitive p:ws.project.sketch)for(int i=0;i<SketchConstraints.anchorCount(p);i++){double[] point=SketchConstraints.anchor(p,i);PointF screen=ws.projectSketchPoint(point[0],point[1]);double distance=Math.hypot(screen.x-x,screen.y-y);if(distance<best){best=distance;picked=p;anchor=i;}}
        }
        if(picked==null){ws.operationMessage="Wskaż geometrię szkicu; przy Punkcie na obiekcie najpierw wskaż punkt";ws.invalidate();return;}
        if(ws.coincidenceFirst==null){ws.coincidenceFirst=picked.id;ws.coincidenceAnchor=anchor;ws.selectedSketchId=picked.id;ws.operationMessage="Wskaż drugi element relacji "+NonlinearConstraints.symbol(ws.constraintKind);ws.invalidate();return;}
        if(ws.coincidenceFirst.equals(picked.id)){ws.operationMessage="Wybierz drugi, różny element";ws.invalidate();return;}
        SketchPrimitive first=null;for(SketchPrimitive p:ws.project.sketch)if(p.id.equals(ws.coincidenceFirst))first=p;
        if(first==null){ws.coincidenceFirst=null;return;}final SketchPrimitive a=first,b=picked;final int ai=ws.coincidenceAnchor;final String kind=ws.constraintKind;ws.coincidenceFirst=null;
        if(kind.equals("symmetric")) {
            List<String> axes=new ArrayList<>(),labels=new ArrayList<>();axes.add("@X");labels.add("Oś X szkicu");axes.add("@Y");labels.add("Oś Y szkicu");
            for(SketchPrimitive p:ws.project.sketch)if((p.type.equals("Linia")||p.type.equals("Pomocnicza"))&&!p.id.equals(a.id)&&!p.id.equals(b.id)){axes.add(p.id);labels.add("Linia osi · "+p.id.substring(0,Math.min(6,p.id.length())));}
            ws.new PropertyBuilder().setTitle("Oś symetrii dwóch linii").setItems(labels.toArray(new String[0]),(d,w)->addSketchConstraint(new SketchConstraints.Link(kind,a.id,0,b.id,0,0,0,axes.get(w)))).setNegativeButton("Anuluj",null).show();
        } else if(kind.equals("angle")) {
            if(!(a.type.equals("Linia")||a.type.equals("Pomocnicza"))||!(b.type.equals("Linia")||b.type.equals("Pomocnicza"))){ws.operationMessage="Kąt wymaga dwóch pojedynczych linii (rozbij kontur, aby użyć jego boku)";ws.invalidate();return;}
            ws.showAngleEditor(-1,a,b);
        } else if(kind.equals("tangent")&&a.type.equals("Okrąg")&&b.type.equals("Okrąg")) {
            ws.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle("Styczność okręgów").setItems(new String[]{"Zewnętrzna","Wewnętrzna"},(d,w)->addSketchConstraint(new SketchConstraints.Link(kind,a.id,0,b.id,0,w==0?1:-1,0))).setNegativeButton("Anuluj",null).show();
        } else {
            double side=1;
            if(kind.equals("tangent")){SketchPrimitive circle=a.type.equals("Okrąg")?a:b,line=a.type.equals("Okrąg")?b:a;side=Math.copySign(1,(circle.x1-line.x1)*(line.y2-line.y1)-(circle.y1-line.y1)*(line.x2-line.x1));}
            addSketchConstraint(new SketchConstraints.Link(kind,a.id,ai,b.id,0,side,0));
        }
        ws.invalidate();
    }
    void showConstraintMenu() {
        ws.new PropertyBuilder().setTitle("Więzy szkicu")
            .setItems(new String[]{"Połącz dwa punkty","Odcinek poziomy (H)","Odcinek pionowy (V)","Unieruchom punkt","Równoległość ∥","Prostopadłość ⊥","Równa długość / promień =","Styczność T","Współśrodkowość ◎","Symetria względem osi ↔︎","Punkt na obiekcie","Wymiar położenia X ↔︎","Wymiar położenia Y ↕︎","Współliniowość ⫽","Kąt między liniami ∠","Odległość punkt–linia / punkt–punkt ↦"},(d,which)-> {
                ws.constraintKind=new String[]{"coincident","horizontal","vertical","fixed","parallel","perpendicular","equal","tangent","concentric","symmetric","pointOn","dimX","dimY","collinear","angle","distance"}[which];
                ws.coincidenceMode=true;ws.coincidenceFirst=null;
                ws.operationMessage=NonlinearConstraints.relation(ws.constraintKind)?"Wskaż dwa elementy relacji "+NonlinearConstraints.symbol(ws.constraintKind):which==0?"Wskaż dwa punkty różnych elementów":which==3?"Dotknij punktu, który ma pozostać na miejscu":"Dotknij odcinka lub boku konturu";
                ws.invalidate();
            }).setNegativeButton("Zamknij",null).show();
    }

    void showEntityConstraints(SketchPrimitive source) {
        List<SketchConstraints.Link> rules=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(SketchConstraints.Link rule:ws.project.coincidences)if(rule.uses(source.id)) {
            rules.add(rule);
            String label=NonlinearConstraints.relation(rule.kind)?"Relacja "+NonlinearConstraints.symbol(rule.kind):rule.kind.equals("fixed")?"Punkt stały "+(rule.ai+1):rule.kind.equals("horizontal")?"Poziomo H · "+(rule.ai+1)+"–"+(rule.bi+1)
                :rule.kind.equals("vertical")?"Pionowo V · "+(rule.ai+1)+"–"+(rule.bi+1):"Połączenie punktu "+(rule.a.equals(source.id)?rule.ai+1:rule.bi+1);
            labels.add("Usuń: "+label);
        }
        labels.add("Usuń wszystkie więzy tego elementu");
        ws.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle("Więzy · "+source.type+" · "+rules.size())
            .setItems(labels.toArray(new String[0]),(d,which)-> {
                ws.checkpoint();
                if(which==rules.size())ws.project.coincidences.removeIf(rule->rule.uses(source.id));
                else ws.project.coincidences.remove(rules.get(which));
                ws.projectRepository.save(ws.project);ws.operationMessage="Usunięto wybrany więz. Położenie geometrii nie zmieniło się.";ws.invalidate();
            }).setNegativeButton("Zamknij",null).show();
    }

    void showSketchCoordinateEditor(SketchPrimitive entity) {
        if(entity.curves!=null){ws.showCurveEditor(entity);return;}
        if(entity.driving!=null) {ws.operationMessage="Najpierw odblokuj wymiary elementu";ws.invalidate();return;}
        LinearLayout form = new LinearLayout(ws.getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)ws.dp(18);
        form.setPadding(padding, 0, padding, 0);
        EditText x1 = ws.coordinateInput("X1 [mm]", entity.x1);
        EditText y1 = ws.coordinateInput("Y1 [mm]", entity.y1);
        EditText x2 = ws.coordinateInput(entity.type.equals("Okrąg") ? "X na promieniu [mm]" : "X2 [mm]", entity.x2);
        EditText y2 = ws.coordinateInput(entity.type.equals("Okrąg") ? "Y na promieniu [mm]" : "Y2 [mm]", entity.y2);
        for(EditText input:new EditText[]{x1,y1,x2,y2}) {
            android.widget.TextView label=new android.widget.TextView(ws.getContext());
            label.setText(input.getHint());label.setTextSize(12);form.addView(label);form.addView(input);
        }
        ws.new PropertyBuilder()
            .setTitle("Edytuj: " + entity.type)
            .setView(form)
            .setNeutralButton("Usuń", (dialog, which) -> ws.deleteSketchEntity(entity.id))
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    SketchPrimitive changed = new SketchPrimitive(entity.id, entity.type,
                        ws.parseCoordinate(x1), ws.parseCoordinate(y1), ws.parseCoordinate(x2), ws.parseCoordinate(y2));
                    ws.replaceSketchEntity(entity, changed);
                } catch (NumberFormatException ignored) {
                    ws.operationMessage = "Niepoprawne współrzędne szkicu";
                    ws.invalidate();
                }
            }).show();
    }

    void showSketchDimensionEditor(SketchPrimitive entity) {
        if(entity.isText()){ws.showTextEditor(entity,entity.textParams()[0],entity.textParams()[1]);return;}
        if(entity.isPoint()){ws.sketchPositionTools.showSketchPosition(entity.id,0,null,0);return;}
        if(entity.curves!=null){ws.showCurveEditor(entity);return;}
        if(entity.vertices!=null){ws.showSketchEntityEditor(entity);return;}
        boolean rectangle=entity.type.equals("Prostokąt"),circle=entity.type.equals("Okrąg");
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding((int)ws.dp(8),0,(int)ws.dp(8),0);
        android.widget.TextView firstLabel=new android.widget.TextView(ws.getContext());
        firstLabel.setText(rectangle?"Szerokość [mm]":circle?"Promień R [mm]":"Długość [mm]");form.addView(firstLabel);
        EditText first=ws.coordinateInput(firstLabel.getText().toString(),rectangle?Math.abs(entity.x2-entity.x1):SketchDimensions.length(entity));form.addView(first);
        EditText second=ws.coordinateInput(rectangle?"Wysokość [mm]":"Kąt do osi X [°]",rectangle?Math.abs(entity.y2-entity.y1):SketchDimensions.angle(entity));
        if(!circle) {
            android.widget.TextView label=new android.widget.TextView(ws.getContext());label.setText(second.getHint());form.addView(label);form.addView(second);
        }
        android.widget.CheckBox diameter=new android.widget.CheckBox(ws.getContext());
        if(circle) {
            diameter.setText("Podawaj średnicę Ø");form.addView(diameter);
            diameter.setOnCheckedChangeListener((button,checked)-> {
                try{double value=ws.parseCoordinate(first);ws.setLengthText(first,checked?value*2:value/2);}
                catch(NumberFormatException ignored){}
                firstLabel.setText(checked?"Średnica Ø [mm]":"Promień R [mm]");
            });
        }
        android.widget.CheckBox construction=new android.widget.CheckBox(ws.getContext());
        construction.setText("Geometria pomocnicza");construction.setChecked(entity.construction);form.addView(construction);
        android.widget.CheckBox lock=new android.widget.CheckBox(ws.getContext());
        lock.setText("Zablokuj wymiary");lock.setChecked(true);form.addView(lock);
        android.widget.TextView hint=new android.widget.TextView(ws.getContext());
        hint.setText("Początek elementu pozostaje na miejscu. Blokada zachowuje rozmiar przy przesuwaniu. Zakończ zatwierdza szkic i przebudowuje powiązaną bryłę.");form.addView(hint);
        CadWorkspaceView.PropertyBuilder builder=ws.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle("Wymiary · "+entity.type).setView(form);
        if(entity.type.equals("Linia")||entity.type.equals("Pomocnicza"))builder.setNeutralButton("Kąt do innej linii…",(d,w)->{
            ws.mode="Edytuj szkic";ws.coincidenceMode=true;ws.constraintKind="angle";ws.coincidenceFirst=entity.id;ws.coincidenceAnchor=0;ws.selectedSketchId=entity.id;
            ws.operationMessage="Dotknij drugiej linii — kąt zostanie zwymiarowany od tej linii";ws.invalidate();});
        builder.setNeutralButton("Położenie…",(d,w)->ws.sketchPositionTools.showSketchPosition(entity.id,0,null,0));
        builder
            .setNeutralButton(entity.driving==null?"Pozycja (opcjonalnie)":"Odblokuj",(d,w)-> {
                if(entity.driving!=null)ws.replaceSketchEntity(entity,SketchDimensions.unlocked(entity));
                else showSketchCoordinateEditor(entity);
            }).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)-> {
                try {
                    double a=ws.parseCoordinate(first);if(circle&&diameter.isChecked())a/=2;
                    SketchPrimitive changed=SketchDimensions.apply(entity,a,circle?0:ws.parseCoordinate(second),lock.isChecked()).withConstruction(construction.isChecked());
                    ws.replaceSketchEntity(entity,changed,true);
                }catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();ws.invalidate();}
            }).show();
        ws.watchFields(form,()->{ws.sketchGhost.clear();try{double a=ws.parseCoordinate(first);if(circle&&diameter.isChecked())a/=2;
            ws.showSolvedGhost(SketchConstraints.edit(ws.project.sketch,ws.project.coincidences,entity.id,SketchDimensions.apply(entity,a,circle?0:ws.parseCoordinate(second),lock.isChecked())));}catch(IllegalArgumentException ignored){}ws.invalidate();});
    }

}
