package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Assembly: lay flat, components and instances, interference, distances, bill of materials. */
final class AssemblyTools {
    private final CadWorkspaceView ws;
    AssemblyTools(CadWorkspaceView view){ws=view;}
    /** Rotates the body of the selected planar face so the face lies on the build plate (Z = 0), as history operations. */
    void layFlatSelectedFace() {
        double[] face=ws.featurePatternTools.selectedPlanarFace();
        if(face==null||ws.project.importedMeshSource!=null){ws.operationMessage="Zaznacz płaską ścianę bryły, na której ma leżeć na stole drukarki";ws.invalidate();return;}
        try {
            int body=ws.faceBodies.getOrDefault(ws.selectedFace,1);String baseline=ws.project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
            for(double[] record:PrintExport.layFlat(new double[]{face[0],face[1],face[2]},new double[]{face[3],face[4],face[5]})){next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(body);next.featureBodyOutputs.add(body);}
            BodyGraph.validate(next);ws.commitProjectAsync(next,baseline,"Położono "+BodyGraph.name(next,body)+" wskazaną ścianą na stole (Z = 0)");
        }catch(org.json.JSONException|IllegalArgumentException error){ws.operationMessage=error.getMessage();ws.invalidate();}
    }

    // ---- Assembly: components, instances, face placement, interference, bill of materials ----
    void showNewComponent() {
        List<Integer> ids=new ArrayList<>(BodyGraph.bodies(ws.project));if(ids.isEmpty()){ws.operationMessage="Brak brył do zgrupowania";ws.invalidate();return;}
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        EditText name=new EditText(ws.getContext());name.setSingleLine(true);name.setText(Component.nextName(ws.project.components));form.addView(name);
        List<android.widget.CheckBox> boxes=new ArrayList<>();
        for(int id:ids){Component c=Component.of(ws.project.components,id);android.widget.CheckBox box=new android.widget.CheckBox(ws.getContext());
            box.setText(BodyGraph.name(ws.project,id)+(c!=null?" · w: "+c.name:""));box.setChecked(id==ws.project.selectedBody&&c==null);box.setEnabled(c==null||!c.instance());boxes.add(box);form.addView(box);}
        ws.new PropertyBuilder().setTitle("Nowy komponent").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Utwórz",(d,w)->{
            List<Integer> chosen=new ArrayList<>();for(int i=0;i<ids.size();i++)if(boxes.get(i).isChecked())chosen.add(ids.get(i));
            try{final String value=name.getText().toString();if(ws.commitModelChange(()->Assembly.group(ws.project,value,chosen)))ws.operationMessage="Utworzono komponent z "+chosen.size()+" brył";}
            catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}ws.invalidate();
        }).show();
    }
    /** New instance of a component (or of a single body, grouped first), or a new placement of an existing instance. */
    void showInstanceDialog(Component edited) {
        List<Component> sources=new ArrayList<>();for(Component c:ws.project.components)if(!c.instance()&&!c.bodies.isEmpty())sources.add(c);
        List<Integer> loose=new ArrayList<>();for(int id:BodyGraph.bodies(ws.project))if(Component.of(ws.project.components,id)==null)loose.add(id);
        if(edited==null&&sources.isEmpty()&&loose.isEmpty()){ws.operationMessage="Brak bryły do wstawienia";ws.invalidate();return;}
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        List<String> labels=new ArrayList<>();for(Component c:sources)labels.add("Komponent: "+c.name);for(int id:loose)labels.add("Bryła: "+BodyGraph.name(ws.project,id));
        android.widget.Spinner source=edited==null?ws.datumTools.choice(form,"Źródło",labels,0):null;
        android.widget.TextView info=new android.widget.TextView(ws.getContext());info.setText(edited==null?"Wystąpienie jest powiązaną kopią: zmiany źródła są przenoszone. Położenie: przesunięcie i obrót.":"Zmiana położenia wystąpienia „"+edited.name+"” względem bieżącego: przesunięcie i obrót.");form.addView(info);
        EditText dx=ws.labeledCoordinate(form,"Przesunięcie X [mm]",edited==null?Math.max(20,ws.modelWidth*1.2):0),dy=ws.labeledCoordinate(form,"Przesunięcie Y [mm]",0),dz=ws.labeledCoordinate(form,"Przesunięcie Z [mm]",0);
        android.widget.Spinner axis=ws.datumTools.choice(form,"Oś obrotu",java.util.Arrays.asList("Z","X","Y"),0);
        EditText angle=ws.labeledCoordinate(form,"Kąt obrotu [°]",0),px=ws.labeledCoordinate(form,"Punkt osi X [mm]",ws.modelCenterX),py=ws.labeledCoordinate(form,"Punkt osi Y [mm]",ws.modelCenterY),pz=ws.labeledCoordinate(form,"Punkt osi Z [mm]",ws.modelCenterZ);
        android.widget.TextView status=new android.widget.TextView(ws.getContext());form.addView(status);
        java.util.function.Supplier<CadProject> candidate=()-> {
            CadProject next=ws.copyProject(ws.project);int a=axis.getSelectedItemPosition();double[] ax=a==0?new double[]{0,0,1}:a==1?new double[]{1,0,0}:new double[]{0,1,0};
            double[] t={ws.parseCoordinate(dx),ws.parseCoordinate(dy),ws.parseCoordinate(dz)},pt={ws.parseCoordinate(px),ws.parseCoordinate(py),ws.parseCoordinate(pz)};double deg=ws.parseCoordinate(angle);
            double[] delta=Assembly.instance(2,t,ax,deg,pt);
            if(edited!=null) {
                Component c=Assembly.find(next,edited.id);if(c==null)throw new IllegalArgumentException("Komponent nie istnieje");
                for(int index:Assembly.instanceRecords(next,c))next.committedFeatures.set(index,Assembly.compose(delta,next.committedFeatures.get(index)));
                return next;
            }
            int pick=source.getSelectedItemPosition();Component src;
            if(pick<sources.size())src=Assembly.find(next,sources.get(pick).id);
            else src=Assembly.group(next,BodyGraph.name(next,loose.get(pick-sources.size())),java.util.Collections.singletonList(loose.get(pick-sources.size())));
            Component inst=Component.create(src.name+" · wystąpienie "+(1+(int)next.components.stream().filter(x->x.instanceOf.equals(src.id)).count()));inst.instanceOf=src.id;
            for(int body:new ArrayList<>(src.bodies)) {
                int created=BodyGraph.allocate(next);double[] record=Assembly.compose(delta,BodyInstance.create(created,new double[3],new double[3],0,new double[3]));
                next.committedFeatures.add(record);next.committedProfileIds.add("");next.featureBodyTargets.add(body);next.featureBodyOutputs.add(body);inst.bodies.add(created);
                next.bodyNames.put(created,BodyGraph.name(next,body)+" (kopia)");
            }
            next.components.add(inst);BodyGraph.validate(next);return next;
        };
        ws.new PropertyBuilder().setTitle(edited==null?"Wstaw wystąpienie":"Położenie · "+edited.name).setView(form).setNegativeButton("Anuluj",null).setPositiveButton(edited==null?"Wstaw":"Zastosuj",(d,w)->{
            try{String baseline=ws.project.toJson().toString();ws.commitProjectAsync(candidate.get(),baseline,edited==null?"Wstawiono wystąpienie (powiązana kopia)":"Zmieniono położenie wystąpienia");}
            catch(org.json.JSONException|IllegalArgumentException error){ws.operationMessage=error.getMessage();ws.invalidate();}
        }).show();
        ws.attachLivePreview(form,candidate,status,"Podgląd wystąpienia. Zastosuj zapisuje je w historii.");
    }
    /** Assembly mates: face-to-face, flush, coaxial and angle mates and their editor. */
    void showComponentActions(Component c) {
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        labels.add("Zmień nazwę");actions.add(()->{EditText name=new EditText(ws.getContext());name.setSingleLine(true);name.setText(c.name);
            ws.new PropertyBuilder().setTitle("Nazwa komponentu").setView(name).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(d,w)->{String v=name.getText().toString().trim();if(!v.isEmpty()&&ws.commitModelChange(()->{Component x=Assembly.find(ws.project,c.id);if(x!=null)x.name=v;}))ws.operationMessage="Zmieniono nazwę";ws.invalidate();}).show();});
        if(c.instance()){labels.add("Zmień położenie…");actions.add(()->showInstanceDialog(c));labels.add("Dosuń ściany…");actions.add(ws.mateTools::begin);
            labels.add("Usuń wystąpienie");actions.add(()->removeInstance(c));}
        else {labels.add("Wstaw wystąpienie…");actions.add(()->showInstanceDialog(null));labels.add("Rozgrupuj (bryły zostają)");actions.add(()->{
            for(Component x:ws.project.components)if(x.instanceOf.equals(c.id)){ws.operationMessage="Komponent ma wystąpienia – usuń je najpierw";ws.invalidate();return;}
            if(ws.commitModelChange(()->ws.project.components.removeIf(x->x.id.equals(c.id))))ws.operationMessage="Rozgrupowano komponent";ws.invalidate();});}
        ws.new PropertyBuilder().setTitle(c.name).setItems(labels.toArray(new String[0]),(d,w)->actions.get(w).run()).setNegativeButton("Zamknij",null).show();
    }
    void removeInstance(Component c) {
        try {
            String baseline=ws.project.toJson().toString();CadProject next=ws.copyProject(ws.project);Component x=Assembly.find(next,c.id);
            // Mates of the instance (moving it, or holding something against it) go first.
            int mates=Assembly.removeMates(next,x.bodies);
            List<Integer> records=Assembly.instanceRecords(next,x);java.util.Collections.sort(records,java.util.Collections.reverseOrder());
            for(int index:records)FeatureRemoval.apply(next,FeatureRemoval.plan(next,index));
            next.components.removeIf(y->y.id.equals(c.id));BodyGraph.validate(next);ws.commitProjectAsync(next,baseline,"Usunięto wystąpienie „"+c.name+"”"+(mates>0?" i jego dosunięcia ("+mates+")":""));
        }catch(org.json.JSONException|IllegalArgumentException error){ws.operationMessage="Nie usunięto: "+error.getMessage();ws.invalidate();}
    }
    void showInterference() {
        if(!ws.project.hasSolid()||ws.project.importedMeshSource!=null){ws.operationMessage="Analiza kolizji wymaga brył B-Rep";ws.invalidate();return;}
        List<Integer> ids=new ArrayList<>();for(int id:BodyGraph.bodies(ws.project))if(!ws.project.hiddenBodies.contains(id))ids.add(id);
        if(ids.size()<2){ws.operationMessage="Kolizje sprawdza się między co najmniej dwiema widocznymi bryłami";ws.invalidate();return;}
        if(ids.size()>24){ws.operationMessage="Za dużo brył naraz (limit 24) – ukryj część brył";ws.invalidate();return;}
        StringBuilder text=new StringBuilder();int collisions=0;String src=ws.project.importedStep?ws.importedSourceFile().getAbsolutePath():null;
        for(int i=0;i<ids.size();i++)for(int j=i+1;j<ids.size();j++) {
            double[] d=NativeCadEngine.bodyDistance(ws.modelWidth,ws.modelHeight,ws.modelDepth,ws.project.importedStep?0:ws.project.filletRadius,ws.evaluationBase(),ws.kernelFeatures(),src,ids.get(i),ids.get(j));
            if(d.length!=8)continue;
            if(d[7]>1e-9){collisions++;text.append("KOLIZJA: ").append(BodyGraph.name(ws.project,ids.get(i))).append(" ↔︎ ").append(BodyGraph.name(ws.project,ids.get(j))).append(" · część wspólna ").append(Units.volume(d[7],ws.project.units,ws.project.displayDecimals)).append('\n');}
            else if(d[0]<1e-9)text.append("Styk: ").append(BodyGraph.name(ws.project,ids.get(i))).append(" ↔︎ ").append(BodyGraph.name(ws.project,ids.get(j))).append('\n');
        }
        android.widget.TextView view=new android.widget.TextView(ws.getContext());view.setTextSize(15);
        view.setText((collisions==0?"Brak kolizji między "+ids.size()+" bryłami.":"Kolizje: "+collisions)+(text.length()>0?"\n\n"+text:"")+"\n\nWynik dokładny (BRepExtrema + część wspólna B-Rep).");
        android.widget.ScrollView scroll=new android.widget.ScrollView(ws.getContext());scroll.addView(view);
        ws.new PropertyBuilder().setTitle("Kolizje w złożeniu").setView(scroll).setNegativeButton("Zamknij",null).show();
    }
    List<BodyAnalysis.Body> analyseBodies() {
        return BodyAnalysis.read(ws.project,NativeCadEngine.bodyInertia(ws.modelWidth,ws.modelHeight,ws.modelDepth,ws.project.importedStep?0:ws.project.filletRadius,ws.evaluationBase(),ws.kernelFeatures(),ws.project.importedStep?ws.importedSourceFile().getAbsolutePath():null));
    }
    void showBillOfMaterials() {
        if(!ws.project.hasSolid()||ws.project.importedMeshSource!=null){ws.operationMessage="Lista części wymaga brył B-Rep";ws.invalidate();return;}
        String text;try{text=Assembly.bom(ws.project,analyseBodies(),false);}catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();ws.invalidate();return;}
        android.widget.TextView view=new android.widget.TextView(ws.getContext());view.setTextSize(15);view.setText(text);
        ws.new PropertyBuilder().setTitle("Lista części").setView(view).setNegativeButton("Zamknij",null)
            .setNeutralButton("Eksport CSV",(d,w)->{if(ws.fileRequestListener!=null)ws.fileRequestListener.requestExport("csv");}).show();
    }
    public void writeBomCsv(OutputStream output) throws IOException {
        try{Writer writer=new OutputStreamWriter(output,StandardCharsets.UTF_8);writer.write('﻿');writer.write(Assembly.bom(ws.project,analyseBodies(),true));writer.flush();}
        catch(IllegalArgumentException error){throw new IOException(error.getMessage());}
    }

    /** Minimum distance and interference (overlap volume) between two bodies. */
    void showBodyDistance() {
        if(!ws.project.hasSolid()||ws.project.importedMeshSource!=null){ws.operationMessage="Analiza odległości wymaga brył B-Rep";ws.invalidate();return;}
        List<Integer> ids=new ArrayList<>(BodyGraph.bodies(ws.project));if(ids.size()<2){ws.operationMessage="Odległość mierzy się między dwiema bryłami dokumentu";ws.invalidate();return;}
        String[] names=new String[ids.size()];for(int i=0;i<names.length;i++)names[i]=BodyGraph.name(ws.project,ids.get(i));
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.Spinner a=ws.datumTools.choice(form,"Bryła A",java.util.Arrays.asList(names),0),b=ws.datumTools.choice(form,"Bryła B",java.util.Arrays.asList(names),1);
        android.widget.TextView result=new android.widget.TextView(ws.getContext());result.setTextSize(15);result.setPadding(0,(int)ws.dp(12),0,0);form.addView(result);
        Runnable compute=()-> {
            int first=ids.get(a.getSelectedItemPosition()),second=ids.get(b.getSelectedItemPosition());
            if(first==second){result.setText("Wybierz dwie różne bryły");return;}
            double[] d=NativeCadEngine.bodyDistance(ws.modelWidth,ws.modelHeight,ws.modelDepth,ws.project.importedStep?0:ws.project.filletRadius,ws.evaluationBase(),ws.kernelFeatures(),ws.project.importedStep?ws.importedSourceFile().getAbsolutePath():null,first,second);
            if(d.length!=8){result.setText(ws.previewFailure("Nie można wyznaczyć odległości"));return;}
            String u=ws.project.units;
            result.setText("Odległość minimalna: "+ws.formatMm(d[0])+(d[7]>1e-9?"\n\nKOLIZJA · objętość wspólna: "+Units.volume(d[7],u,ws.project.displayDecimals):d[0]<1e-9?"\n\nBryły stykają się":"")
                +"\n\nNajbliższy punkt A: "+Units.number(d[1],u,ws.project.displayDecimals)+"; "+Units.number(d[2],u,ws.project.displayDecimals)+"; "+Units.number(d[3],u,ws.project.displayDecimals)
                +"\nNajbliższy punkt B: "+Units.number(d[4],u,ws.project.displayDecimals)+"; "+Units.number(d[5],u,ws.project.displayDecimals)+"; "+Units.number(d[6],u,ws.project.displayDecimals)+"\n\nWynik dokładny (BRepExtrema).");
        };
        android.widget.AdapterView.OnItemSelectedListener listener=new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long id){compute.run();}public void onNothingSelected(android.widget.AdapterView<?> parent){}};
        a.setOnItemSelectedListener(listener);b.setOnItemSelectedListener(listener);
        ws.new PropertyBuilder().setTitle("Odległość i kolizja brył").setView(form).setNegativeButton("Zamknij",null).show();compute.run();
    }

    void showMaterialDensityEditor() {
        if(ws.projectRepository.readOnly()){ws.operationMessage=ws.projectRepository.loadNotice();ws.invalidate();return;}
        if(SketchTransactions.active(ws.project)){ws.operationMessage="Najpierw zakończ lub anuluj aktywną edycję szkicu";ws.invalidate();return;}
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding((int)ws.dp(20),(int)ws.dp(8),(int)ws.dp(20),0);
        android.widget.TextView hint=new android.widget.TextView(ws.getContext());
        hint.setText("Jednakowa gęstość wszystkich brył [kg/m³]. Wpisz wartość dla używanego materiału. Puste pole usuwa przypisanie.");form.addView(hint);
        EditText density=new EditText(ws.getContext());density.setSingleLine(true);density.setSelectAllOnFocus(true);density.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        density.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        density.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        density.setHint("Gęstość [kg/m³]");
        density.setText(ws.project.materialDensityKgM3==0?"":java.math.BigDecimal.valueOf(ws.project.materialDensityKgM3).stripTrailingZeros().toPlainString());form.addView(density);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(ws.getContext()).setTitle("Gęstość materiału")
            .setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",null).create();
        dialog.setOnShowListener(d->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                double value=MaterialDensity.parse(density.getText().toString());
                if(value!=ws.project.materialDensityKgM3) {
                    String baseline=ws.project.toJson().toString();
                    CadProject candidate=CadProject.fromJson(new org.json.JSONObject(baseline));candidate.materialDensityKgM3=value;
                    ws.projectRepository.save(candidate);
                    ws.project=candidate;ws.undoStack.push(baseline);while(ws.undoStack.size()>50)ws.undoStack.removeLast();ws.redoStack.clear();
                }
                dialog.dismiss();ws.materialTools.showBodyProperties();ws.invalidate();
            }catch(IllegalArgumentException error){density.setError(error.getMessage());}
            catch(Exception error){android.widget.Toast.makeText(ws.getContext(),"Nie zapisano gęstości; poprzedni stan zachowano",android.widget.Toast.LENGTH_LONG).show();}
        }));
        dialog.show();
    }

}
