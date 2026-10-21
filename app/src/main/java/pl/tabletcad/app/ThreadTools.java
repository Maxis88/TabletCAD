package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Threads in holes: picking bores (one or many) and the thread dialog. */
final class ThreadTools {
    private final CadWorkspaceView v;
    ThreadTools(CadWorkspaceView view){v=view;}
    boolean fromMaximum(HoleThread.Cylinder hole) {
        double[] a=hole.end(false),b=hole.end(true),pa=v.projectWorldPoint(a[0],a[1],a[2]),pb=v.projectWorldPoint(b[0],b[1],b[2]);return pb[2]>pa[2];
    }
    void begin() {
        if(v.projectRepository.readOnly()){v.operationMessage=v.projectRepository.loadNotice();v.invalidate();return;}
        if(v.inSketch()){v.finishSketchThenRetry();return;}
        if(v.project.importedMeshSource!=null||!v.project.hasSolid()){v.operationMessage="Gwint wymaga otworu w bryle B-Rep";v.invalidate();return;}
        v.closePropertyEditor();v.preserveExtrudedBase();v.commitActiveFeature();BodyGraph.begin(v.project);v.mode="Gwint";v.threadSelections.clear();
        for(HoleThread.Cylinder hole:v.holeCylinders)if(hole.face==v.selectedFace&&!HoleThread.threaded(hole,v.project.committedFeatures))v.threadSelections.add(hole.face);showPanel();
    }
    void tap(int face) {
        if(face<0){v.clearViewportSelection();return;}
        for(HoleThread.Cylinder hole:v.holeCylinders)if(hole.face==face){if(HoleThread.threaded(hole,v.project.committedFeatures)){v.operationMessage="Ten otwór ma już gwint – zmień go z historii operacji";v.invalidate();return;}if(!v.threadSelections.add(face))v.threadSelections.remove(face);showPanel();v.invalidate();return;}
        v.operationMessage="Dotknij wewnętrznej, walcowej ściany otworu. Obróć model, jeśli nie jest widoczna.";v.invalidate();
    }
    void showPanel() {
        List<HoleThread.Cylinder> picked=new ArrayList<>();for(HoleThread.Cylinder hole:v.holeCylinders)if(v.threadSelections.contains(hole.face)&&!HoleThread.threaded(hole,v.project.committedFeatures))picked.add(hole);
        List<HoleThread.Cylinder> selected=HoleThread.unique(picked);
        if(selected.isEmpty()) {android.widget.TextView message=new android.widget.TextView(v.getContext());message.setText("Dotknij walcowych ścian jednego lub kilku otworów. Ponowne dotknięcie usuwa wybór.");v.new PropertyBuilder().setTitle("Gwint · wybierz otwory").setView(message).setNegativeButton("Anuluj",(d,w)->{v.mode="Wybierz";v.threadSelections.clear();}).show();return;}
        List<HoleThread.Preset> presets=HoleThread.nearest(selected.get(0).radius*2);
        if(presets.isEmpty()){v.operationMessage="Brak pasującego presetu dla średnicy wskazanego otworu";v.invalidate();return;}
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(v.getContext());info.setText(String.format(java.util.Locale.US,"Wybrane otwory: %d · pierwszy Ø %s. Rzeczywisty profil V 60°.",selected.size(),v.formatMm(selected.get(0).radius*2)));form.addView(info);
        android.widget.Spinner preset=new android.widget.Spinner(v.getContext());List<String> labels=new ArrayList<>();for(HoleThread.Preset p:presets)labels.add(p.label());preset.setAdapter(new android.widget.ArrayAdapter<>(v.getContext(),android.R.layout.simple_spinner_dropdown_item,labels));form.addView(preset);
        android.widget.CheckBox automatic=new android.widget.CheckBox(v.getContext());automatic.setText("Dobierz preset osobno do każdego otworu");automatic.setChecked(selected.size()>1);form.addView(automatic);
        double min=Double.POSITIVE_INFINITY;for(HoleThread.Cylinder hole:selected)min=Math.min(min,hole.max-hole.min);
        EditText depth=v.labeledCoordinate(form,"Głębokość gwintu [mm]",min);v.slider(form,depth,.5,Math.max(1,min),.5);
        android.widget.CheckBox full=new android.widget.CheckBox(v.getContext());full.setText("Pełna długość każdego otworu");full.setChecked(true);form.addView(full);v.fieldVisible(depth,false);full.setOnCheckedChangeListener((button,checked)->v.fieldVisible(depth,!checked));
        android.widget.CheckBox left=new android.widget.CheckBox(v.getContext());left.setText("Gwint lewy");form.addView(left);
        android.widget.CheckBox otherEnd=new android.widget.CheckBox(v.getContext());otherEnd.setText("Od przeciwnego końca otworu");form.addView(otherEnd);
        android.content.DialogInterface.OnClickListener apply=(d,w)->{
                try {
                    String baseline=v.project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                    for(HoleThread.Cylinder hole:selected) {
                        List<HoleThread.Preset> choices=automatic.isChecked()?HoleThread.nearest(hole.radius*2):presets;if(choices.isEmpty())throw new IllegalArgumentException("Brak presetu dla jednego z otworów");
                        HoleThread.Preset chosen=choices.get(automatic.isChecked()?0:preset.getSelectedItemPosition());
                        double[] feature=HoleThread.create(hole,chosen,full.isChecked()?hole.max-hole.min:v.parseCoordinate(depth),fromMaximum(hole)^otherEnd.isChecked(),left.isChecked());
                        next.committedFeatures.add(feature);next.committedProfileIds.add("");next.featureBodyTargets.add(hole.body);next.featureBodyOutputs.add(hole.body);
                    }
                    v.commitProjectAsync(next,baseline,"Utworzono gwinty: "+selected.size());
                }catch(org.json.JSONException|IllegalArgumentException error){v.operationMessage=error.getMessage();v.invalidate();}
            };
        v.new PropertyBuilder().setTitle("Gwint · "+selected.size()+" otworów").setView(form).setNegativeButton("Anuluj",(d,w)->{v.mode="Wybierz";v.threadSelections.clear();})
            .setPositiveButton("Utwórz gwinty",apply).show();
        v.capturedPreview(form,apply,"Podgląd gwintów.");
    }
}
