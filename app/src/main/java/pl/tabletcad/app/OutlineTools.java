package pl.tabletcad.app;

import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.UUID;
import pl.tabletcad.app.model.SketchPrimitive;

/** Outline (offset) of a sketch element or of a whole closed chain: copy or replace in place. */
final class OutlineTools {
    private final CadWorkspaceView v;
    OutlineTools(CadWorkspaceView view){v=view;}
    /** Offset copy of the selected sketch element; the copy is independent and editable. */
    void show() {
        SketchPrimitive source=null;
        for(SketchPrimitive entity:v.project.sketch)if(entity.id.equals(v.selectedSketchId))source=entity;
        if(source==null){v.operationMessage="Zaznacz element szkicu (Edytuj), a następnie wybierz Odsunięcie";return;}
        // A line or arc of a closed chain: the whole loop is outlined (sharp corners, concentric arcs).
        SketchContours.Result chainFound=null;
        if(!source.closedGeometry()&&(source.type.equals("Linia")||source.type.equals("Łuk"))){try{chainFound=SketchContours.join(v.project.sketch,source.id);}catch(IllegalArgumentException notClosed){chainFound=null;}}
        final SketchContours.Result chain=chainFound;
        if(chain!=null)source=chain.profile;
        final SketchPrimitive selected=source;
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        EditText distance=v.coordinateInput("Odległość [mm]",2);form.addView(distance);
        android.widget.CheckBox inward=new android.widget.CheckBox(v.getContext());
        inward.setText(selected.closedGeometry()?"Do wewnątrz":selected.type.equals("Linia")||selected.type.equals("Pomocnicza")?"Na drugą stronę linii":"Zmniejsz promień");
        form.addView(inward);
        android.widget.CheckBox replace=new android.widget.CheckBox(v.getContext());replace.setText("Zastąp oryginał (obrys: powiększ / pomniejsz kształt)");replace.setChecked(false);form.addView(replace);
        if(chain!=null){android.widget.TextView about=new android.widget.TextView(v.getContext());about.setText("Cały zamknięty kontur: "+chain.consumed.size()+" elementów");form.addView(about);}
        android.widget.TextView status=new android.widget.TextView(v.getContext());status.setText("Kopia odsunięta o stałą odległość. Narożniki ostre, łuki współśrodkowe.");form.addView(status);
        v.slider(form,distance,.1,Math.max(20,v.modelExtent()),.1);
        Runnable ghost=()->{v.sketchGhost.clear();try{double value=v.parseCoordinate(distance);if(inward.isChecked())value=-value;v.sketchGhost.add(SketchOffset.offset(selected,"ghost",value));status.setText("Kopia odsunięta o stałą odległość. Narożniki ostre, łuki współśrodkowe.");}catch(IllegalArgumentException error){status.setText(error.getMessage());}v.invalidate();};
        v.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle("Odsunięcie · "+(chain!=null?"kontur":selected.type)).setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)->{
            try {
                double value=v.parseCoordinate(distance);if(inward.isChecked())value=-value;
                SketchPrimitive copy=SketchOffset.offset(selected,UUID.randomUUID().toString(),value);
                if(replace.isChecked()&&chain!=null) {
                    for(SketchConstraints.Link link:v.project.coincidences)if(chain.consumed.contains(link.a)||chain.consumed.contains(link.b))throw new IllegalArgumentException("Usuń więzy linii konturu przed zastąpieniem go obrysem (albo odznacz „Zastąp oryginał”)");
                    org.json.JSONObject json=copy.toJson();json.put("id",chain.profile.id);SketchPrimitive outline=SketchPrimitive.fromJson(json);
                    v.checkpoint();SketchTransactions.join(v.project,chain);v.preserveExtrudedBase();v.commitActiveFeature();
                    v.project.sketch.removeIf(p->chain.consumed.contains(p.id));v.project.sketch.add(outline);
                    v.project.featureProfileId=null;v.project.sketchExtruded=false;v.selectedSketchId=outline.id;v.mode="Edytuj szkic";v.projectRepository.save(v.project);
                    v.operationMessage=(value>0?"Powiększono":"Pomniejszono")+" zamknięty kontur ("+chain.consumed.size()+" elementów) o "+v.formatMm(Math.abs(value))+". Zakończ zatwierdza szkic.";v.invalidate();return;
                }
                if(replace.isChecked()) {
                    // Same identity: operations built on this profile follow the new outline.
                    org.json.JSONObject json=copy.toJson();json.put("id",selected.id);SketchPrimitive outline=SketchPrimitive.fromJson(json);
                    v.replaceSketchEntity(selected,outline,true);v.selectedSketchId=selected.id;v.mode="Edytuj szkic";
                    boolean applied=false;for(SketchPrimitive e:v.project.sketch)if(e.id.equals(selected.id)&&Math.abs(e.x1-outline.x1)<1e-6&&Math.abs(e.y2-outline.y2)<1e-6)applied=true;
                    if(applied)v.operationMessage=(value>0?"Powiększono":"Pomniejszono")+" obrys o "+v.formatMm(Math.abs(value))+(selected.driving!=null?" (wymiary elementu usunięte – nadaj je ponownie)":"")+". Zakończ zatwierdza szkic.";
                    v.invalidate();return;
                }
                v.checkpoint();v.project.sketch.add(copy);v.selectedSketchId=copy.id;v.mode="Edytuj szkic";
                v.projectRepository.save(v.project);
                v.operationMessage="Utworzono odsunięcie "+v.formatMm(Math.abs(value))+". Zakończ zatwierdza szkic.";
            }catch(IllegalArgumentException|org.json.JSONException error){v.operationMessage=error.getMessage();}
            v.invalidate();
        }).show();
        v.watchFields(form,ghost);ghost.run();
    }

}
