package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Hole tool: picking the face and centre, diameter, depth, counterbore / countersink, preview. */
final class HoleTools {
    private final CadWorkspaceView ws;
    HoleTools(CadWorkspaceView view){ws=view;}
    void begin() {
        if(!ws.prepareSolidTool("Otwór"))return;
        ws.mode="Otwór";ws.holeEditIndex=-1;ws.holeReference=null;
        if(ws.selectedFace>=0)tap(ws.selectedFace);
        else {ws.operationMessage="Otwór: dotknij płaskiej ściany w miejscu środka otworu. Położenie można potem wpisać dokładnie.";ws.closePropertyEditor();ws.invalidate();}
    }
    void tap(int face) {
        if(face<0){ws.clearViewportSelection();if(ws.holeEditIndex<0){ws.holeReference=null;}return;}
        if(!ws.isFacePlanar(face)){ws.operationMessage="Otwór można umieścić tylko na płaskiej ścianie";ws.invalidate();return;}
        Integer origin=ws.faceFeatures.get(face);if(origin==null){ws.operationMessage="Nie można ustalić operacji, która utworzyła tę ścianę";ws.invalidate();return;}
        ws.selectedFace=face;
        double l=Math.sqrt(ws.selectedNormalX*ws.selectedNormalX+ws.selectedNormalY*ws.selectedNormalY+ws.selectedNormalZ*ws.selectedNormalZ);
        ws.holeReference=new double[]{ws.selectedPointX,ws.selectedPointY,ws.selectedPointZ,ws.selectedNormalX/l,ws.selectedNormalY/l,ws.selectedNormalZ/l,origin};
        showPanel();ws.invalidate();
    }
    void showPanel() {
        if(ws.holeReference==null)return;
        double[] original=ws.holeEditIndex>=0?ws.project.committedFeatures.get(ws.holeEditIndex):null;
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(ws.getContext());
        info.setText("Środek leży na wskazanej ścianie. Odległości od krawędzi A i B są zapamiętywane – po zmianie modelu środek zachowuje te odległości. Dotknij innej płaskiej ściany, aby ją zmienić.");form.addView(info);
        EditText x=ws.labeledCoordinate(form,"Środek X [mm]",ws.holeReference[0]),y=ws.labeledCoordinate(form,"Środek Y [mm]",ws.holeReference[1]),z=ws.labeledCoordinate(form,"Środek Z [mm]",ws.holeReference[2]);
        // Exact distances from the nearest straight face edge (A) and a perpendicular one (B).
        HolePlacement placement=null;
        try{String src=ws.project.importedStep?ws.importedSourceFile().getAbsolutePath():null;placement=HolePlacement.find(new double[]{ws.holeReference[0],ws.holeReference[1],ws.holeReference[2]},new double[]{ws.holeReference[3],ws.holeReference[4],ws.holeReference[5]},
            NativeCadEngine.edgeMeasurements(ws.modelWidth,ws.modelHeight,ws.modelDepth,ws.project.importedStep?0:ws.project.filletRadius,ws.evaluationBase(),ws.kernelFeatures(),src));}catch(IllegalArgumentException ignored){placement=null;}
        double[] holeFaces=new double[0];
        if(placement!=null)try{String src=ws.project.importedStep?ws.importedSourceFile().getAbsolutePath():null;holeFaces=NativeCadEngine.faceAnalysis(ws.modelWidth,ws.modelHeight,ws.modelDepth,ws.project.importedStep?0:ws.project.filletRadius,ws.evaluationBase(),ws.kernelFeatures(),src);}catch(IllegalArgumentException ignored){holeFaces=new double[0];}
        final double[] faceRows=holeFaces;
        final HolePlacement place=placement;final EditText fromA=place==null?null:ws.labeledCoordinate(form,"Odległość od krawędzi A (najbliższej) [mm]",place.distanceA),
            fromB=place==null||!place.hasB()?null:ws.labeledCoordinate(form,"Odległość od krawędzi B (prostopadłej) [mm]",place.distanceB);
        if(place!=null) {
            final boolean[] syncing={false};
            Runnable fromEdges=()->{if(syncing[0])return;try{double[] q=place.at(ws.parseCoordinate(fromA),fromB==null?0:ws.parseCoordinate(fromB));syncing[0]=true;ws.setLengthText(x,q[0]);ws.setLengthText(y,q[1]);ws.setLengthText(z,q[2]);}catch(IllegalArgumentException ignored){}finally{syncing[0]=false;}};
            android.text.TextWatcher watcher=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void onTextChanged(CharSequence s,int a,int b,int c){fromEdges.run();}public void afterTextChanged(android.text.Editable s){}};
            fromA.addTextChangedListener(watcher);if(fromB!=null)fromB.addTextChangedListener(watcher);
        }
        EditText diameter=ws.labeledCoordinate(form,"Średnica [mm]",original!=null?original[9]:6.5);if(ws.holeEditIndex>=0)ws.bound(diameter,"hole:"+ws.holeEditIndex+":d");
        android.widget.CheckBox through=new android.widget.CheckBox(ws.getContext());through.setText("Na wylot (przez całą bryłę)");through.setChecked(original==null||original[1]==1);form.addView(through);
        EditText depth=ws.labeledCoordinate(form,"Głębokość [mm]",original!=null&&original[1]==0?original[10]:10);if(ws.holeEditIndex>=0)ws.bound(depth,"hole:"+ws.holeEditIndex+":h");
        EditText tip=ws.labeledCoordinate(form,"Kąt wierzchołkowy wiertła [°] (0 = płaskie dno)",original!=null&&original[1]==0?original[14]:118);
        android.widget.TextView headLabel=new android.widget.TextView(ws.getContext());headLabel.setText("Pogłębienie");form.addView(headLabel);
        android.widget.Spinner head=new android.widget.Spinner(ws.getContext());head.setAdapter(new android.widget.ArrayAdapter<>(ws.getContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"Brak (otwór prosty)","Walcowe (pod łeb walcowy)","Stożkowe (pod łeb stożkowy)"}));
        if(original!=null)head.setSelection((int)original[11]);form.addView(head);
        EditText headDiameter=ws.labeledCoordinate(form,"Średnica pogłębienia [mm]",original!=null&&original[11]!=0?original[12]:11);
        EditText headValue=ws.labeledCoordinate(form,"Głębokość pogłębienia [mm] / kąt stożka [°]",original!=null&&original[11]!=0?original[13]:6);
        Runnable states=()->{ws.fieldVisible(depth,!through.isChecked());ws.fieldVisible(tip,!through.isChecked());boolean headed=head.getSelectedItemPosition()!=0;ws.fieldVisible(headDiameter,headed);ws.fieldVisible(headValue,headed);android.widget.TextView label=ws.fieldLabel(headValue);if(label!=null)label.setText(head.getSelectedItemPosition()==HoleFeature.COUNTERSINK?"Kąt pogłębienia [°]":Units.label("Głębokość pogłębienia [mm]",ws.project.units));};
        through.setOnCheckedChangeListener((b,c)->states.run());
        // The second head value is a length for a counterbore and an angle for a countersink.
        if(original!=null&&original[11]==HoleFeature.COUNTERSINK){headValue.setTag(null);headValue.setText(Units.input(original[13],"mm"));}
        final int[] shownHead={original!=null?(int)original[11]:0};
        head.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(position!=shownHead[0]) {
                    shownHead[0]=position;
                    if(position==HoleFeature.COUNTERSINK){headValue.setTag(null);headValue.setText("90");}
                    else {headValue.setTag(ws.LENGTH_INPUT);ws.setLengthText(headValue,6);}
                }
                states.run();}
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}});
        states.run();
        android.widget.TextView status=new android.widget.TextView(ws.getContext());form.addView(status);
        final int editIndex=ws.holeEditIndex;final double[] reference=ws.holeReference.clone();
        java.util.function.Supplier<CadProject> candidate=()-> {
            int kind=head.getSelectedItemPosition();
            double[] point={ws.parseCoordinate(x),ws.parseCoordinate(y),ws.parseCoordinate(z)};
            double headSecond=kind==HoleFeature.COUNTERSINK?Units.parseNumber(headValue.getText().toString()):kind==HoleFeature.COUNTERBORE?ws.parseCoordinate(headValue):0;
            double[] record=HoleFeature.create(point,new double[]{reference[3],reference[4],reference[5]},(int)reference[6],ws.parseCoordinate(diameter),through.isChecked(),
                through.isChecked()?0:ws.parseCoordinate(depth),kind,kind==0?0:ws.parseCoordinate(headDiameter),headSecond,through.isChecked()?0:ws.parseCoordinate(tip));
            CadProject next=ws.copyProject(ws.project);
            int body=ws.selectedFace>=0?ws.faceBodies.getOrDefault(ws.selectedFace,1):ws.project.selectedBody>0?ws.project.selectedBody:1;
            if(editIndex>=0)next.committedFeatures.set(editIndex,record);
            else {next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(body);next.featureBodyOutputs.add(body);}
            int at=editIndex>=0?editIndex:next.committedFeatures.size()-1;
            // Keep the centre associative with the reference edges (the faces meeting the hole face there).
            double[] refs=place==null?null:HoleAssociation.references(place,point,faceRows);if(refs!=null)next.holeReferences.put(at,refs);else next.holeReferences.remove(at);
            ws.bindField(next,"hole:"+at+":d",diameter);if(through.isChecked())next.parameterBindings.remove("hole:"+at+":h");else ws.bindField(next,"hole:"+at+":h",depth);
            return next;
        };
        ws.new PropertyBuilder().setTitle(ws.holeEditIndex>=0?"Edytuj otwór":"Otwór").setView(form)
            .setNegativeButton("Anuluj",(d,w)->{ws.mode="Wybierz";ws.holeReference=null;ws.holeEditIndex=-1;})
            .setPositiveButton(ws.holeEditIndex>=0?"Zastosuj":"Utwórz otwór",(d,w)->{
                try {
                    String baseline=ws.project.toJson().toString();CadProject next=candidate.get();
                    double[] record=next.committedFeatures.get(editIndex>=0?editIndex:next.committedFeatures.size()-1);
                    ws.holeEditIndex=-1;ws.holeReference=null;
                    ws.commitProjectAsync(next,baseline,(editIndex>=0?"Zaktualizowano: ":"Utworzono: ")+HoleFeature.describe(record));
                }catch(org.json.JSONException|IllegalArgumentException error){ws.operationMessage=error.getMessage();ws.invalidate();}
            }).show();
        ws.attachLivePreview(form,candidate,status,"Podgląd otworu na bryle. Przeciągnij strzałkę głębokości lub Zastosuj.");
        ws.attachFieldHandle(new double[]{reference[0],reference[1],reference[2]},new double[]{-reference[3],-reference[4],-reference[5]},depth,.1,5000,"Głębokość");
    }

    // ---- Draft (taper) of planar faces ----
}
