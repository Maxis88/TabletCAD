package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Shell: picking open faces, wall thickness with preview, editing. */
final class ShellTools {
    private final CadWorkspaceView v;
    ShellTools(CadWorkspaceView view){v=view;}
    void begin(int editIndex) {
        if(!v.prepareSolidTool("Powłoka"))return;
        v.mode="Powłoka";v.shellEditIndex=editIndex;v.shellSelections.clear();
        if(v.selectedFace>=0)tap(v.selectedFace);else showPanel();
    }
    void tap(int face) {
        if(face<0){v.clearViewportSelection();return;}
        if(v.shellSelections.remove(face)!=null){showPanel();v.invalidate();return;}
        if(!v.isFacePlanar(face)){v.operationMessage="Powłoka usuwa tylko płaskie ściany. Dotknij płaskiej ściany.";v.invalidate();return;}
        Integer origin=v.faceFeatures.get(face);if(origin==null){v.operationMessage="Nie można ustalić operacji, która utworzyła tę ścianę";v.invalidate();return;}
        try {
            v.shellSelections.put(face,new ShellFeature.Face(new double[]{v.selectedPointX,v.selectedPointY,v.selectedPointZ},new double[]{v.selectedNormalX,v.selectedNormalY,v.selectedNormalZ},origin));
        }catch(IllegalArgumentException error){v.operationMessage=error.getMessage();}
        showPanel();v.invalidate();
    }
    void showPanel() {
        double[] original=v.shellEditIndex>=0&&v.shellEditIndex<v.project.committedFeatures.size()?v.project.committedFeatures.get(v.shellEditIndex):null;
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(v.getContext());
        info.setText(v.shellSelections.isEmpty()?"Dotknij płaskich ścian, które mają zostać otwarte. Bez wskazanych ścian bryła zostanie wydrążona z zamkniętą pustką.":"Usuwane ściany: "+v.shellSelections.size()+". Ponowne dotknięcie usuwa wybór.");form.addView(info);
        EditText thickness=v.labeledCoordinate(form,"Grubość ścianki [mm]",original!=null?original[2]:2);v.slider(form,thickness,.1,Math.max(5,v.modelExtent()/2),.1);
        android.widget.CheckBox outside=new android.widget.CheckBox(v.getContext());outside.setText("Grubość na zewnątrz bryły");outside.setChecked(original!=null&&original[3]==1);form.addView(outside);
        android.widget.TextView status=new android.widget.TextView(v.getContext());form.addView(status);
        final int editIndex=v.shellEditIndex;
        java.util.function.Supplier<CadProject> candidate=()-> {
            double[] record=ShellFeature.create(new ArrayList<>(v.shellSelections.values()),v.parseCoordinate(thickness),outside.isChecked());
            CadProject next=v.copyProject(v.project);
            int body=1;
            if(!v.shellSelections.isEmpty()){Integer first=v.shellSelections.keySet().iterator().next();body=v.faceBodies.getOrDefault(first,1);for(Integer face:v.shellSelections.keySet())if(v.faceBodies.getOrDefault(face,1)!=body)throw new IllegalArgumentException("Wskazane ściany należą do różnych brył");}
            else body=v.project.selectedBody>0?v.project.selectedBody:1;
            if(editIndex>=0){next.committedFeatures.set(editIndex,record);if(!v.shellSelections.isEmpty()&&v.featureBody(next,editIndex)!=body)throw new IllegalArgumentException("Ściany należą do innej bryły niż edytowana powłoka");}
            else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(body);next.featureBodyOutputs.add(body);}
            return next;
        };
        v.new PropertyBuilder().setTitle(v.shellEditIndex>=0?"Powłoka · wskaż ściany ponownie":"Powłoka").setView(form)
            .setNegativeButton("Anuluj",(d,w)->{v.mode="Wybierz";v.shellSelections.clear();v.shellEditIndex=-1;})
            .setPositiveButton(v.shellEditIndex>=0?"Zastosuj":"Utwórz powłokę",(d,w)->{
                try {
                    String baseline=v.project.toJson().toString();CadProject next=candidate.get();
                    double[] record=next.committedFeatures.get(editIndex>=0?editIndex:next.committedFeatures.size()-1);
                    v.shellEditIndex=-1;v.commitProjectAsync(next,baseline,(original!=null?"Zaktualizowano: ":"Utworzono: ")+ShellFeature.describe(record));
                }catch(org.json.JSONException|IllegalArgumentException error){v.operationMessage=error.getMessage();v.invalidate();}
            }).show();
        v.attachLivePreview(form,candidate,status,"Podgląd powłoki. Przeciągnij strzałkę grubości, dotknij kolejnych ścian lub Zastosuj.");
        if(!v.shellSelections.isEmpty()){ShellFeature.Face face=v.shellSelections.values().iterator().next();v.attachFieldHandle(face.point,new double[]{-face.normal[0],-face.normal[1],-face.normal[2]},thickness,.05,500,"Grubość");}
    }
    void showEditor(int index) {
        double[] f=v.project.committedFeatures.get(index);LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(v.getContext());info.setText(ShellFeature.describe(f));form.addView(info);
        EditText thickness=v.labeledCoordinate(form,"Grubość ścianki [mm]",f[2]);v.slider(form,thickness,.1,Math.max(5,v.modelExtent()/2),.1);
        android.widget.CheckBox outside=new android.widget.CheckBox(v.getContext());outside.setText("Grubość na zewnątrz bryły");outside.setChecked(f[3]==1);form.addView(outside);
        android.widget.TextView status=new android.widget.TextView(v.getContext());form.addView(status);
        java.util.function.Supplier<CadProject> candidate=()->{CadProject next=v.copyProject(v.project,false);next.committedFeatures.set(index,ShellFeature.withThickness(f,v.parseCoordinate(thickness),outside.isChecked()));return next;};
        v.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle("Edytuj powłokę").setView(form).setNegativeButton("Anuluj",null)
            .setNeutralButton("Wskaż ściany ponownie",(d,w)->begin(index))
            .setPositiveButton("Zastosuj",(d,w)->{
                try {
                    String baseline=v.project.toJson().toString();CadProject next=candidate.get();
                    v.commitProjectAsync(next,baseline,"Zaktualizowano: "+ShellFeature.describe(next.committedFeatures.get(index)));
                }catch(org.json.JSONException|IllegalArgumentException error){v.operationMessage=error.getMessage();v.invalidate();}
            }).show();
        v.attachLivePreview(form,candidate,status,"Podgląd zmienionej grubości ścianek.");
    }

}
