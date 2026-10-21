package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Body split (plane, surface, surface through curves) and body move / rotate / scale dialogs. */
final class BodyTools {
    private final CadWorkspaceView v;
    BodyTools(CadWorkspaceView view){v=view;}
    void showSplit(int editIndex) {
        double[] original=editIndex>=0?v.project.committedFeatures.get(editIndex):null;final double[] face=v.featurePatternTools.selectedPlanarFace();
        final boolean loftOriginal=original!=null&&SurfaceSplit.isLoft(original);
        final boolean surfaceOriginal=original!=null&&(SurfaceSplit.is(original)||loftOriginal);
        List<Integer> ids=new ArrayList<>(BodyGraph.bodies(v.project));if(original!=null)ids.remove(Integer.valueOf(surfaceOriginal?(int)original[1]:BodySplit.created(original)));
        if(ids.isEmpty()){v.operationMessage="Brak bryły do podziału";v.invalidate();return;}
        String[] names=new String[ids.size()];for(int i=0;i<names.length;i++)names[i]=BodyGraph.name(v.project,ids.get(i));
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(v.getContext());info.setText("Płaszczyzna: część za nią pozostaje w bryle, część po stronie normalnej staje się nową bryłą. Powierzchnia z krzywej szkicu (linia, łuk, otwarty splajn — także falisty) przechodzi prostopadle do szkicu przez całą bryłę; część po lewej stronie kierunku krzywej zostaje.");form.addView(info);
        android.widget.Spinner body=new android.widget.Spinner(v.getContext());body.setAdapter(new android.widget.ArrayAdapter<>(v.getContext(),android.R.layout.simple_spinner_dropdown_item,names));form.addView(body);
        body.setSelection(Math.max(0,ids.indexOf(original!=null?v.featureBody(v.project,editIndex):v.project.selectedBody)));if(original!=null)body.setEnabled(false);
        List<String> choices=new ArrayList<>(java.util.Arrays.asList("Płaszczyzna YZ (normalna X)","Płaszczyzna XZ (normalna Y)","Płaszczyzna XY (normalna Z)"));if(face!=null)choices.add("Płaszczyzna zaznaczonej ściany (stała)");
        final List<Datum> splitPlanes=v.datumsOf(Datum.PLANE);for(Datum d:splitPlanes)choices.add("Konstrukcja: "+d.name+" (odsunięcie)");
        final int surfaceStart=choices.size();final List<SweepLoft.Placed> curves=new ArrayList<>();
        for(SweepLoft.Placed e:SweepLoft.elements(v.project))if(SurfaceSplit.usable(e.shape)){curves.add(e);choices.add("Powierzchnia z krzywej: "+e.sketchName+" · "+v.sketchEntityName(e.shape));}
        String originalRef=surfaceOriginal&&editIndex<v.project.committedProfileIds.size()?v.project.committedProfileIds.get(editIndex):null;
        final int loftChoice=choices.size();if(curves.size()>=2)choices.add("Powierzchnia przez kilka krzywych (zaznacz poniżej, w kolejności)");
        final List<android.widget.CheckBox> loftBoxes=new ArrayList<>();final LinearLayout loftList=new LinearLayout(v.getContext());loftList.setOrientation(LinearLayout.VERTICAL);
        for(SweepLoft.Placed e:curves){android.widget.CheckBox box=new android.widget.CheckBox(v.getContext());box.setText(e.sketchName+" · "+v.sketchEntityName(e.shape));
            if(loftOriginal&&originalRef!=null)for(String part:originalRef.substring(SurfaceSplit.LOFT_REF.length()).split("\\|"))if(part.equals(e.ref()))box.setChecked(true);
            loftBoxes.add(box);loftList.addView(box);}
        android.widget.Spinner plane=new android.widget.Spinner(v.getContext());plane.setAdapter(new android.widget.ArrayAdapter<>(v.getContext(),android.R.layout.simple_spinner_dropdown_item,choices));form.addView(plane);
        int axis=original==null||surfaceOriginal?0:Math.abs(original[5])>.5?0:Math.abs(original[6])>.5?1:2;plane.setSelection(original==null&&face!=null?3:axis);
        if(surfaceOriginal&&!loftOriginal)for(int i=0;i<curves.size();i++)if(originalRef!=null&&originalRef.equals(SurfaceSplit.REF+curves.get(i).ref()))plane.setSelection(surfaceStart+i);
        if(loftOriginal)plane.setSelection(loftChoice);
        form.addView(loftList);loftList.setVisibility(plane.getSelectedItemPosition()==loftChoice?View.VISIBLE:View.GONE);
        plane.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){loftList.setVisibility(position==loftChoice?View.VISIBLE:View.GONE);}
            public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        double[] centre={v.modelCenterX,v.modelCenterY,v.modelCenterZ};
        EditText offset=v.labeledCoordinate(form,"Położenie płaszczyzny [mm] (dla ściany: odsunięcie)",original!=null&&!surfaceOriginal?original[2+axis]:face!=null?0:centre[0]);
        android.widget.CheckBox flip=new android.widget.CheckBox(v.getContext());flip.setText("Odwróć strony podziału");flip.setChecked(surfaceOriginal?SurfaceSplit.flipped(original):original!=null&&original[5+axis]<0);form.addView(flip);
        android.content.DialogInterface.OnClickListener apply=(d,w)->{
            try {
                int choice=plane.getSelectedItemPosition();
                if(choice==loftChoice&&curves.size()>=2) {
                    List<SweepLoft.Placed> chosen=new ArrayList<>();for(int i=0;i<curves.size();i++)if(loftBoxes.get(i).isChecked())chosen.add(curves.get(i));
                    int target=ids.get(body.getSelectedItemPosition());
                    String baseline=v.project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                    double[] record;String ref=SurfaceSplit.loftRef(chosen);
                    if(editIndex>=0){record=SurfaceSplit.createLoft((int)original[1],chosen,flip.isChecked());next.committedFeatures.set(editIndex,record);next.committedProfileIds.set(editIndex,ref);}
                    else {record=SurfaceSplit.createLoft(BodyGraph.allocate(next),chosen,flip.isChecked());next.committedFeatures.add(record);next.committedProfileIds.add(ref);next.featureBodyTargets.add(target);next.featureBodyOutputs.add(target);}
                    BodyGraph.validate(next);v.commitProjectAsync(next,baseline,"Podział powierzchnią przez "+chosen.size()+" krzywe → nowa bryła: "+BodyGraph.name(next,(int)record[1]));
                    return;
                }
                if(choice>=surfaceStart&&choice<loftChoice) {
                    List<SweepLoft.Placed> chosen=java.util.Collections.singletonList(curves.get(choice-surfaceStart));
                    int target=ids.get(body.getSelectedItemPosition());double[] fo=curves.get(choice-surfaceStart).frame;double reach=v.modelRadius+Math.sqrt(Math.pow(fo[0]-v.modelCenterX,2)+Math.pow(fo[1]-v.modelCenterY,2)+Math.pow(fo[2]-v.modelCenterZ,2));
                    double depth=Math.min(20000,Math.max(10,Math.max(4*v.modelExtent(),2.4*reach)));// covers every body, wherever the sketch plane lies
                    String baseline=v.project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                    double[] record;String ref=SurfaceSplit.ref(chosen);
                    if(editIndex>=0){int created=surfaceOriginal?(int)original[1]:BodySplit.created(original);record=SurfaceSplit.create(created,chosen,depth,flip.isChecked());next.committedFeatures.set(editIndex,record);next.committedProfileIds.set(editIndex,ref);}
                    else {record=SurfaceSplit.create(BodyGraph.allocate(next),chosen,depth,flip.isChecked());next.committedFeatures.add(record);next.committedProfileIds.add(ref);next.featureBodyTargets.add(target);next.featureBodyOutputs.add(target);}
                    BodyGraph.validate(next);v.commitProjectAsync(next,baseline,SurfaceSplit.describe(record)+" → nowa bryła: "+BodyGraph.name(next,SurfaceSplit.created(record)));
                    return;
                }
                double value=v.parseCoordinate(offset),sign=flip.isChecked()?-1:1;double[] point=new double[3],normal=new double[3];
                double[] reference=choice<3?null:face!=null&&choice==3?face:splitPlanes.get(choice-3-(face!=null?1:0)).frame();
                if(choice<3){point[choice]=value;normal[choice]=sign;}else for(int i=0;i<3;i++){point[i]=reference[i]+reference[3+i]*value;normal[i]=reference[3+i]*sign;}
                int target=ids.get(body.getSelectedItemPosition());
                String baseline=v.project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                double[] record;
                if(editIndex>=0){record=BodySplit.create(surfaceOriginal?(int)original[1]:BodySplit.created(original),point,normal);next.committedFeatures.set(editIndex,record);next.committedProfileIds.set(editIndex,"");}
                else {record=BodySplit.create(BodyGraph.allocate(next),point,normal);next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(target);next.featureBodyOutputs.add(target);}
                BodyGraph.validate(next);v.commitProjectAsync(next,baseline,BodySplit.describe(record)+" → nowa bryła: "+BodyGraph.name(next,BodySplit.created(record)));
            }catch(org.json.JSONException|IllegalArgumentException error){v.operationMessage=error.getMessage();v.invalidate();}
        };
        v.new PropertyBuilder().setTitle(editIndex>=0?"Edytuj podział bryły":"Podziel bryłę").setView(form).setNegativeButton("Anuluj",null).setPositiveButton(editIndex>=0?"Zastosuj":"Podziel",apply).show();
        v.capturedPreview(form,apply,"Podgląd podziału.");
    }

    // ---- Move / rotate / scale a body ----
    void showTransform(int kind,int editIndex) {
        double[] original=editIndex>=0?v.project.committedFeatures.get(editIndex):null;
        List<Integer> ids=new ArrayList<>(BodyGraph.bodies(v.project));if(ids.isEmpty()){v.operationMessage="Brak bryły do przekształcenia";v.invalidate();return;}
        String[] names=new String[ids.size()];for(int i=0;i<names.length;i++)names[i]=BodyGraph.name(v.project,ids.get(i));
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView bodyLabel=new android.widget.TextView(v.getContext());bodyLabel.setText("Bryła");form.addView(bodyLabel);
        android.widget.Spinner body=new android.widget.Spinner(v.getContext());body.setAdapter(new android.widget.ArrayAdapter<>(v.getContext(),android.R.layout.simple_spinner_dropdown_item,names));form.addView(body);
        body.setSelection(Math.max(0,ids.indexOf(original!=null?v.featureBody(v.project,editIndex):v.project.selectedBody)));if(original!=null)body.setEnabled(false);
        double[] o=original!=null?original:new double[16];
        EditText a=v.labeledCoordinate(form,kind==BodyTransform.MOVE?"Przesunięcie X [mm]":kind==BodyTransform.ROTATE?"Punkt osi X [mm]":"Środek skalowania X [mm]",o[2]);
        EditText b=v.labeledCoordinate(form,kind==BodyTransform.MOVE?"Przesunięcie Y [mm]":kind==BodyTransform.ROTATE?"Punkt osi Y [mm]":"Środek skalowania Y [mm]",o[3]);
        EditText c=v.labeledCoordinate(form,kind==BodyTransform.MOVE?"Przesunięcie Z [mm]":kind==BodyTransform.ROTATE?"Punkt osi Z [mm]":"Środek skalowania Z [mm]",o[4]);
        android.widget.Spinner axis=new android.widget.Spinner(v.getContext());
        EditText value=null;
        if(kind==BodyTransform.ROTATE) {
            android.widget.TextView axisLabel=new android.widget.TextView(v.getContext());axisLabel.setText("Oś obrotu");form.addView(axisLabel);
            axis.setAdapter(new android.widget.ArrayAdapter<>(v.getContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"X","Y","Z"}));form.addView(axis);
            axis.setSelection(original==null?2:Math.abs(o[5])>.5?0:Math.abs(o[6])>.5?1:2);
            value=v.labeledCoordinate(form,"Kąt [°]",original!=null?(o[5]+o[6]+o[7]<0?-o[8]:o[8]):90);
        } else if(kind==BodyTransform.SCALE)value=v.labeledCoordinate(form,"Współczynnik skali",original!=null?o[8]:2);
        final EditText fValue=value;
        String title=kind==BodyTransform.MOVE?"Przesuń bryłę":kind==BodyTransform.ROTATE?"Obróć bryłę":"Skaluj bryłę";
        android.content.DialogInterface.OnClickListener apply=(d,w)->{
            try {
                double x=v.parseCoordinate(a),y=v.parseCoordinate(b),z=v.parseCoordinate(c);double[] record;
                if(kind==BodyTransform.MOVE)record=BodyTransform.move(x,y,z);
                else if(kind==BodyTransform.ROTATE){double[] dir=new double[3];dir[axis.getSelectedItemPosition()]=1;record=BodyTransform.rotate(new double[]{x,y,z},dir,v.parseCoordinate(fValue));}
                else record=BodyTransform.scale(new double[]{x,y,z},v.parseCoordinate(fValue));
                int target=ids.get(body.getSelectedItemPosition());
                String baseline=v.project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                if(editIndex>=0)next.committedFeatures.set(editIndex,record);
                else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(target);next.featureBodyOutputs.add(target);}
                v.commitProjectAsync(next,baseline,BodyTransform.describe(record)+" · "+BodyGraph.name(next,target));
            }catch(org.json.JSONException|IllegalArgumentException error){v.operationMessage=error.getMessage();v.invalidate();}
        };
        v.new PropertyBuilder().setTitle(editIndex>=0?"Edytuj: "+title:title).setView(form).setNegativeButton("Anuluj",null).setPositiveButton(editIndex>=0?"Zastosuj":"Wykonaj",apply).show();
        v.capturedPreview(form,apply,"Podgląd przekształcenia bryły.");
    }

    // ---- Shell (hollow body) ----
}
