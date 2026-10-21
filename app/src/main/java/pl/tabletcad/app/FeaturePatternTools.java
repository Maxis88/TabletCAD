package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Patterns and mirrors of history operations: sources, direction from a face, edge or axis, parameters, editing. */
final class FeaturePatternTools {
    private final CadWorkspaceView ws;
    FeaturePatternTools(CadWorkspaceView view){ws=view;}
    void begin(String tool) {
        final int kind=tool.startsWith("Lustro")?FeaturePattern.MIRROR:tool.startsWith("Szyk liniowy")?FeaturePattern.LINEAR:FeaturePattern.CIRCULAR;
        String name=kind==FeaturePattern.MIRROR?"Lustro":kind==FeaturePattern.LINEAR?"Szyk liniowy":"Szyk kołowy";
        if(!ws.prepareSolidTool(name))return;
        List<Integer> eligible=new ArrayList<>();List<String> titles=new ArrayList<>();
        for(CadTimeline.Item item:CadTimeline.entries(ws.project))if(item.kind==CadTimeline.Kind.FEATURE&&FeaturePattern.eligible(ws.project.committedFeatures.get(item.featureIndex))) {
            eligible.add(item.featureIndex);titles.add((item.featureIndex+1)+". "+item.title+" · "+BodyGraph.name(ws.project,ws.featureBody(ws.project,item.featureIndex)));
        }
        final int bodyKind=kind==FeaturePattern.MIRROR?1:kind==FeaturePattern.LINEAR?2:3;
        if(eligible.isEmpty()){ws.operationMessage=name+": w historii nie ma operacji do powielenia – powielana jest cała bryła.";ws.showBodyPatternDialog(bodyKind,-1);return;}
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(ws.getContext());info.setText("Zaznacz operacje z historii (np. otwór, wycięcie) albo powiel całą bryłę. Kopie operacji śledzą późniejsze zmiany źródeł.");form.addView(info);
        android.widget.Button whole=new android.widget.Button(ws.getContext());whole.setText("Powiel całą bryłę…");form.addView(whole);
        List<android.widget.CheckBox> boxes=new ArrayList<>();
        for(int i=0;i<eligible.size();i++){android.widget.CheckBox box=new android.widget.CheckBox(ws.getContext());box.setText(titles.get(i));box.setChecked(eligible.get(i)==ws.highlightFeature||(eligible.size()==1));form.addView(box);boxes.add(box);}
        ws.new PropertyBuilder().setTitle(name+" · operacje źródłowe").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Dalej",(d,w)->{
            List<Integer> chosen=new ArrayList<>();for(int i=0;i<boxes.size();i++)if(boxes.get(i).isChecked())chosen.add(eligible.get(i));
            if(chosen.isEmpty()){ws.operationMessage="Zaznacz co najmniej jedną operację";ws.invalidate();return;}
            int body=ws.featureBody(ws.project,chosen.get(0));for(int source:chosen)if(ws.featureBody(ws.project,source)!=body){ws.operationMessage="Wybrane operacje należą do różnych brył – utwórz osobne szyki";ws.invalidate();return;}
            int[] sources=new int[chosen.size()];for(int i=0;i<sources.length;i++)sources[i]=chosen.get(i);
            showParameters(kind,sources,-1);
        }).show();
        whole.setOnClickListener(v->ws.showBodyPatternDialog(bodyKind,-1));
    }
    /** Planar face currently selected in the viewport, as point + outward normal, or null. */
    double[] selectedPlanarFace() {
        if(ws.selectedFace<0||!ws.isFacePlanar(ws.selectedFace))return null;
        double l=Math.sqrt(ws.selectedNormalX*ws.selectedNormalX+ws.selectedNormalY*ws.selectedNormalY+ws.selectedNormalZ*ws.selectedNormalZ);if(l<1e-9)return null;
        return new double[]{ws.selectedPointX,ws.selectedPointY,ws.selectedPointZ,ws.selectedNormalX/l,ws.selectedNormalY/l,ws.selectedNormalZ/l};
    }
    HoleThread.Cylinder selectedCylinder() {
        for(HoleThread.Cylinder hole:ws.holeCylinders)if(hole.face==ws.selectedFace)return hole;return null;
    }
    void showParameters(int kind,int[] sources,int editIndex) {
        double[] original=editIndex>=0?ws.project.committedFeatures.get(editIndex):null;
        final double[] face=selectedPlanarFace();final HoleThread.Cylinder cylinder=selectedCylinder();
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        List<String> choices=new ArrayList<>();List<double[]> frames=new ArrayList<>();
        if(original!=null){choices.add("Bieżące odniesienie");frames.add(new double[]{original[3],original[4],original[5],original[6],original[7],original[8]});}
        if(kind==FeaturePattern.MIRROR) {
            choices.add("Płaszczyzna YZ (normalna X)");frames.add(new double[]{0,0,0,1,0,0});
            choices.add("Płaszczyzna XZ (normalna Y)");frames.add(new double[]{0,0,0,0,1,0});
            choices.add("Płaszczyzna XY (normalna Z)");frames.add(new double[]{0,0,0,0,0,1});
            if(face!=null){choices.add("Płaszczyzna zaznaczonej ściany (stała)");frames.add(face);}
        } else {
            String noun=kind==FeaturePattern.LINEAR?"Kierunek":"Oś";
            choices.add(noun+" X");frames.add(new double[]{0,0,0,1,0,0});
            choices.add(noun+" Y");frames.add(new double[]{0,0,0,0,1,0});
            choices.add(noun+" Z");frames.add(new double[]{0,0,0,0,0,1});
            if(kind==FeaturePattern.LINEAR&&face!=null){choices.add("Normalna zaznaczonej ściany");frames.add(face);}
            if(kind==FeaturePattern.CIRCULAR&&cylinder!=null){choices.add("Oś zaznaczonej ściany walcowej");frames.add(new double[]{cylinder.origin[0],cylinder.origin[1],cylinder.origin[2],cylinder.axis[0],cylinder.axis[1],cylinder.axis[2]});}
        }
        for(Datum d:ws.datumsOf(kind==FeaturePattern.MIRROR?Datum.PLANE:Datum.AXIS)){choices.add("Konstrukcja: "+d.name);frames.add(d.frame());}
        android.widget.TextView reference=new android.widget.TextView(ws.getContext());reference.setText(kind==FeaturePattern.MIRROR?"Płaszczyzna lustra":kind==FeaturePattern.LINEAR?"Kierunek szyku":"Oś obrotu");form.addView(reference);
        android.widget.Spinner frame=new android.widget.Spinner(ws.getContext());frame.setAdapter(new android.widget.ArrayAdapter<>(ws.getContext(),android.R.layout.simple_spinner_dropdown_item,choices));form.addView(frame);
        if(original==null&&((kind==FeaturePattern.MIRROR&&face!=null)||(kind==FeaturePattern.LINEAR&&face!=null)||(kind==FeaturePattern.CIRCULAR&&cylinder!=null)))frame.setSelection(choices.size()-1);
        EditText offset=null,px=null,py=null,pz=null,count=null,value=null;android.widget.CheckBox reverse=null;
        if(kind==FeaturePattern.MIRROR) offset=ws.labeledCoordinate(form,"Odsunięcie płaszczyzny wzdłuż normalnej [mm]",0);
        else {
            if(kind==FeaturePattern.CIRCULAR) {
                double[] o=original!=null?new double[]{original[3],original[4],original[5]}:new double[3];
                px=ws.labeledCoordinate(form,"Punkt osi X [mm] (dla osi X/Y/Z)",o[0]);py=ws.labeledCoordinate(form,"Punkt osi Y [mm]",o[1]);pz=ws.labeledCoordinate(form,"Punkt osi Z [mm]",o[2]);
            }
            count=ws.labeledCoordinate(form,"Liczba wystąpień (z oryginałem)",original!=null?original[9]:kind==FeaturePattern.LINEAR?3:6);
            value=ws.labeledCoordinate(form,kind==FeaturePattern.LINEAR?"Odstęp [mm]":"Kąt całkowity [°] (360 = równo na obwodzie)",original!=null?Math.abs(original[10]):kind==FeaturePattern.LINEAR?20:360);
            ws.slider(form,count,2,50,1);if(kind==FeaturePattern.LINEAR)ws.slider(form,value,.5,2*ws.modelExtent(),.5);else ws.slider(form,value,1,360,1);
            reverse=new android.widget.CheckBox(ws.getContext());reverse.setText(kind==FeaturePattern.LINEAR?"Przeciwny kierunek":"Przeciwny kierunek obrotu");reverse.setChecked(original!=null&&original[10]<0);
            if(kind==FeaturePattern.LINEAR)form.addView(reverse);
        }
        final EditText fOffset=offset,fx=px,fy=py,fz=pz,fCount=count,fValue=value;final android.widget.CheckBox fReverse=reverse;
        final android.widget.CheckBox flipAxis=new android.widget.CheckBox(ws.getContext());
        if(kind==FeaturePattern.CIRCULAR){flipAxis.setText("Przeciwny kierunek obrotu");form.addView(flipAxis);}
        String title=(kind==FeaturePattern.MIRROR?"Lustro":kind==FeaturePattern.LINEAR?"Szyk liniowy":"Szyk kołowy")+" · "+sources.length+(sources.length==1?" operacja":" operacje");
        android.widget.TextView status=new android.widget.TextView(ws.getContext());form.addView(status);
        java.util.function.Supplier<CadProject> candidate=()-> {
            double[] selected=frames.get(frame.getSelectedItemPosition()).clone();
            double[] origin={selected[0],selected[1],selected[2]},direction={selected[3],selected[4],selected[5]};
            int n=0;double v=0;
            if(kind==FeaturePattern.MIRROR) {
                double shift=ws.parseCoordinate(fOffset),l=Math.sqrt(direction[0]*direction[0]+direction[1]*direction[1]+direction[2]*direction[2]);
                for(int i=0;i<3;i++)origin[i]+=direction[i]/l*shift;
            } else {
                double countValue=ws.parseCoordinate(fCount);if(countValue!=Math.floor(countValue))throw new IllegalArgumentException("Liczba wystąpień musi być całkowita");n=(int)countValue;
                v=ws.parseCoordinate(fValue);
                if(kind==FeaturePattern.LINEAR){if(fReverse.isChecked())v=-Math.abs(v);else v=Math.abs(v);}
                else {
                    // The typed point defines the axis unless the axis of a picked cylinder is used.
                    if(!choices.get(frame.getSelectedItemPosition()).startsWith("Oś zaznaczonej")&&!choices.get(frame.getSelectedItemPosition()).startsWith("Konstrukcja: ")){origin[0]=ws.parseCoordinate(fx);origin[1]=ws.parseCoordinate(fy);origin[2]=ws.parseCoordinate(fz);}
                    if(flipAxis.isChecked())for(int i=0;i<3;i++)direction[i]=-direction[i];
                }
            }
            double[] record=FeaturePattern.create(kind,sources,origin,direction,n,v);
            CadProject next=ws.copyProject(ws.project);
            int body=ws.featureBody(next,sources[0]);
            if(editIndex>=0)next.committedFeatures.set(editIndex,record);
            else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(body);next.featureBodyOutputs.add(body);}
            FeaturePattern.validateSources(next.committedFeatures,editIndex>=0?editIndex:next.committedFeatures.size()-1);
            return next;
        };
        ws.new PropertyBuilder().setTitle(title).setView(form).setNegativeButton("Anuluj",null).setPositiveButton(editIndex>=0?"Zastosuj":"Utwórz",(d,w)->{
            try {
                String baseline=ws.project.toJson().toString();CadProject next=candidate.get();
                double[] record=next.committedFeatures.get(editIndex>=0?editIndex:next.committedFeatures.size()-1);
                ws.commitProjectAsync(next,baseline,(editIndex>=0?"Zaktualizowano: ":"Utworzono: ")+FeaturePattern.describe(record));
            }catch(org.json.JSONException|IllegalArgumentException error){ws.operationMessage=error.getMessage();ws.invalidate();}
        }).show();
        ws.attachLivePreview(form,candidate,status,"Podgląd szyku na bryle. Przeciągnij strzałkę odstępu lub Zastosuj.");
        if(kind==FeaturePattern.LINEAR){double[] dir=frames.get(frame.getSelectedItemPosition());ws.attachFieldHandle(new double[]{ws.modelCenterX,ws.modelCenterY,ws.modelCenterZ},new double[]{dir[3],dir[4],dir[5]},fValue,.01,5000,"Odstęp");
            // The arrow follows the chosen direction and the reverse switch.
            if(ws.fieldHandle!=null)ws.fieldHandle.axisSource=()->{double[] d=frames.get(frame.getSelectedItemPosition());double sign=fReverse.isChecked()?-1:1;return new double[]{d[3]*sign,d[4]*sign,d[5]*sign};};}
    }
    void showEditor(int index) {
        double[] f=ws.project.committedFeatures.get(index);
        StringBuilder info=new StringBuilder(FeaturePattern.describe(f)).append("\nŹródła:");
        for(int source:FeaturePattern.sources(f))for(CadTimeline.Item item:CadTimeline.entries(ws.project))if(item.kind==CadTimeline.Kind.FEATURE&&item.featureIndex==source)info.append("\n· ").append(source+1).append(". ").append(item.title);
        android.widget.TextView text=new android.widget.TextView(ws.getContext());text.setText(info);
        ws.new PropertyBuilder().setTitle(FeaturePattern.label(f)).setView(text).setNegativeButton("Zamknij",null)
            .setNeutralButton("Pokaż źródła",(d,w)->{ws.highlightFeature=FeaturePattern.sources(f)[0];ws.operationMessage="Podświetlono pierwszą operację źródłową";ws.invalidate();})
            .setPositiveButton("Edytuj parametry",(d,w)->showParameters(FeaturePattern.kind(f),FeaturePattern.sources(f),index)).show();
    }

    // ---- Boolean between document bodies ----
}
