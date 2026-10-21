package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Extrusion dialogs: body choice, depth on a face, direction and symmetry options, base extrusion editor. */
final class ExtrusionDialogs {
    private final CadWorkspaceView ws;
    ExtrusionDialogs(CadWorkspaceView view){ws=view;}
    void showFaceFeature(SketchPrimitive profile) {
        ws.new PropertyBuilder().setTitle("Wyciągnięcie · operacja").setItems(new String[]{"Nowa bryła · osobny obiekt","Połącz z bryłą","Wytnij","Część wspólna"},(d,index)->chooseBody(profile,new String[]{"new","add","cut","common"}[index])).setNegativeButton("Anuluj",null).show();ws.showExtrusionSketch=true;
    }
    void chooseBody(SketchPrimitive profile,String operation) {
        List<Integer> bodies=new ArrayList<>(BodyGraph.bodies(ws.project));
        if(operation.equals("new")||bodies.size()<2){showFaceFeatureDepth(profile,operation);return;}
        String[] names=new String[bodies.size()];for(int i=0;i<names.length;i++)names[i]=BodyGraph.name(ws.project,bodies.get(i));
        ws.new PropertyBuilder().setTitle("Bryła docelowa").setItems(names,(d,w)->{ws.project.selectedBody=bodies.get(w);showFaceFeatureDepth(profile,operation);}).setNegativeButton("Anuluj",null).show();ws.showExtrusionSketch=true;
    }

    void showFaceFeatureDepth(SketchPrimitive profile, String operation) {
        BodyGraph.begin(ws.project);final int targetBody=ws.project.selectedBody;final boolean editActive=operation.equals(ws.project.featureOperation)&&profile.id.equals(ws.project.featureProfileId);
        if(ws.project.importedMeshSource!=null) {
            ws.operationMessage="Operacje bryłowe wymagają B-Rep, nie siatki STL";ws.invalidate();return;
        }
        ws.selectedSketchId=profile.id;
        double initialDepth=operation.equals(ws.project.featureOperation)&&profile.id.equals(ws.project.featureProfileId)
            ?ws.project.featureDepth:Math.abs(ws.project.featureDepth);
        EditText input = ws.lengthInput(Math.abs(ws.project.featureDepth));
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.addView(input);
        android.widget.CheckBox reverse=directionCheck(initialDepth);form.addView(reverse);
        android.widget.CheckBox symmetric=symmetricCheck(operation.equals(ws.project.featureOperation)&&profile.id.equals(ws.project.featureProfileId)&&ws.project.featureSymmetric,reverse);form.addView(symmetric);
        android.widget.TextView previewStatus=new android.widget.TextView(ws.getContext());
        previewStatus.setText("Podgląd nie zmienia dokumentu. Zastosuj zapisuje operację.");form.addView(previewStatus);
        ws.new PropertyBuilder()
            .setTitle(FeatureDirection.label(FeatureDirection.featureCode(operation,false))+" · długość [mm]")
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    double value = ws.parseCoordinate(input);
                    if (!Double.isFinite(value) || value < 0.1 || value > 5000.0) {
                        ws.operationMessage = "Głębokość musi mieścić się w zakresie 0,1–5000 mm";
                        ws.invalidate();
                        return;
                    }
                    final double signedDepth=FeatureDirection.signed(value,reverse.isChecked(),symmetric.isChecked());
                    if (ws.commitModelChange(() -> {
                        if(!editActive){ws.preserveExtrudedBase();ws.commitActiveFeature();}
                        ws.project.featureOperation = operation;ws.project.activeBodyTarget=targetBody;
                        ws.project.featureProfileId = profile.id;
                        ws.project.featureDepth = signedDepth;
                        ws.project.featureSymmetric=symmetric.isChecked();
                        ws.project.sketchExtruded = true;
                    })) {
                        ws.treeSelection = "Model";
                        ws.mode = "Wybierz";
                        ws.operationMessage = "Gotowe: "+FeatureDirection.label(FeatureDirection.featureCode(operation,false));
                    }
                    ws.invalidate();
                } catch (NumberFormatException ignored) {
                    ws.operationMessage = "Niepoprawna wartość głębokości";
                    ws.invalidate();
                }
            }).show();
        final android.widget.ScrollView owner=ws.propertyEditor;
        Runnable preview=()-> {
            if(owner==null || ws.propertyEditor!=owner) return;
            ws.invalidateGeometryPreview();
            try {
                double depth=ws.parseCoordinate(input);
                if(!Double.isFinite(depth)||depth<.1||depth>5000) throw new NumberFormatException();
                depth=FeatureDirection.signed(depth,reverse.isChecked(),symmetric.isChecked());
                double[] candidate=profile.appendVertices(new double[]{profile.featureType(),FeatureDirection.featureCode(operation,symmetric.isChecked()),
                    profile.x1,profile.y1,profile.x2,profile.y2,depth,
                    ws.project.sketchOriginX,ws.project.sketchOriginY,ws.project.sketchOriginZ,
                    ws.project.sketchUx,ws.project.sketchUy,ws.project.sketchUz,ws.project.sketchVx,ws.project.sketchVy,ws.project.sketchVz});
                int length=candidate.length;for(double[] feature:ws.project.committedFeatures) length+=feature.length;
                double[] values=new double[length];int offset=0;
                for(double[] feature:ws.project.committedFeatures) {System.arraycopy(feature,0,values,offset,feature.length);offset+=feature.length;}
                System.arraycopy(candidate,0,values,offset,candidate.length);
                final double proposedDepth=depth;final boolean proposedSymmetric=symmetric.isChecked();
                previewStatus.setText("Obliczanie podglądu…");
                ws.submitGeometryPreview(owner,ws.meshPreviewQuery(ws.project.featureBase,values,ws.project.filletRadius,ws.modelDepth),mesh->{
                    if(mesh.length>0)previewStatus.setText("Podgląd · "+Math.abs(proposedDepth)+" mm"+(proposedSymmetric?" · symetrycznie":proposedDepth<0?" · odwrotnie":"")+". Zastosuj zapisuje operację.");
                    else previewStatus.setText(ws.previewFailure("Brak poprawnego wyniku. Zmień głębokość lub profil."));
                });
            } catch(NumberFormatException error) {previewStatus.setText("Głębokość: 0,1–5000 mm");}
            ws.invalidate();
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                ws.invalidateGeometryPreview();ws.removeCallbacks(preview);ws.postDelayed(preview,350);
            }
            public void afterTextChanged(android.text.Editable text) {}
        });
        reverse.setOnCheckedChangeListener((button,checked)->{ws.invalidateGeometryPreview();ws.removeCallbacks(preview);ws.postDelayed(preview,100);});
        symmetric.setOnCheckedChangeListener((button,checked)->{reverse.setEnabled(!checked);ws.invalidateGeometryPreview();ws.removeCallbacks(preview);ws.postDelayed(preview,100);});
        ws.postDelayed(preview,100);
        double u=profile.type.equals("Okrąg")||profile.isEllipse()?profile.x1:(profile.x1+profile.x2)*.5;
        double v=profile.type.equals("Okrąg")||profile.isEllipse()?profile.y1:(profile.y1+profile.y2)*.5;
        double sign=operation.equals("cut")?-1:1;
        double[] origin={ws.project.sketchOriginX+u*ws.project.sketchUx+v*ws.project.sketchVx,
            ws.project.sketchOriginY+u*ws.project.sketchUy+v*ws.project.sketchVy,
            ws.project.sketchOriginZ+u*ws.project.sketchUz+v*ws.project.sketchVz};
        double[] axis={sign*(ws.project.sketchUy*ws.project.sketchVz-ws.project.sketchUz*ws.project.sketchVy),
            sign*(ws.project.sketchUz*ws.project.sketchVx-ws.project.sketchUx*ws.project.sketchVz),
            sign*(ws.project.sketchUx*ws.project.sketchVy-ws.project.sketchUy*ws.project.sketchVx)};
        ws.bindSourceExtrusion(profile.id,BodyGraph.profileBody(ws.project,profile.id),input,reverse,symmetric,origin,axis,preview);ws.showExtrusionSketch=true;
    }

    android.widget.CheckBox directionCheck(double depth) {
        android.widget.CheckBox result=new android.widget.CheckBox(ws.getContext());
        result.setText("Odwróć kierunek");result.setChecked(depth<0);return result;
    }

    android.widget.CheckBox symmetricCheck(boolean checked,android.widget.CheckBox reverse) {
        android.widget.CheckBox result=new android.widget.CheckBox(ws.getContext());
        result.setText("Symetrycznie (długość łączna)");result.setChecked(checked);reverse.setEnabled(!checked);return result;
    }

    void showBaseEditor(double[] base,String profileId) {
        if(ExtrusionPlan.placedBase(base)||ws.project.extrusionExtents.containsKey(-1)){ws.showExtrusionExtentEditor(-1);return;}
        EditText input=ws.coordinateInput("Głębokość [mm]",Math.abs(base[6]));
        android.widget.CheckBox reverse=directionCheck(base[6]);
        android.widget.CheckBox symmetric=symmetricCheck(FeatureDirection.baseSymmetric(base[5]),reverse);
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.addView(input);form.addView(reverse);form.addView(symmetric);
        android.widget.TextView status=new android.widget.TextView(ws.getContext());
        status.setText("Podgląd nie zmienia dokumentu.");form.addView(status);
        boolean initial=!ws.project.sketchExtruded&&ws.project.featureBase==null;
        ws.new PropertyBuilder().setTitle("Wyciągnięcie bazowe [mm]").setView(form)
            .setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(dialog,which)-> {
                try {
                    double signed=FeatureDirection.signed(ws.parseCoordinate(input),reverse.isChecked(),symmetric.isChecked());
                    double[] candidate=base.clone();candidate[6]=signed;
                    candidate[5]=FeatureDirection.baseCode(base[5],symmetric.isChecked());
                    if (ws.commitModelChange(() -> {
                        ws.project.featureBase=candidate;ws.project.featureBaseProfileId=profileId;
                        if(initial){ws.project.featureProfileId=profileId;ws.project.featureOperation="none";ws.project.featureSymmetric=false;ws.project.sketchExtruded=true;}
                        ws.project.depth=Math.abs(signed);
                        if(initial)ws.project.filletRadius=0;
                    })) {
                        ws.mode="Wybierz";
                        ws.operationMessage="Gotowe: wyciągnięcie "+Math.abs(signed)+" mm"+(symmetric.isChecked()?" · symetrycznie":signed<0?" · kierunek odwrócony":"");
                    }
                }catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();}
                ws.invalidate();
            }).show();
        android.widget.ScrollView owner=ws.propertyEditor;
        Runnable preview=()-> {
            if(owner==null||ws.propertyEditor!=owner)return;
            ws.invalidateGeometryPreview();
            try {
                double signed=FeatureDirection.signed(ws.parseCoordinate(input),reverse.isChecked(),symmetric.isChecked());
                double[] candidate=base.clone();candidate[6]=signed;
                candidate[5]=FeatureDirection.baseCode(base[5],symmetric.isChecked());
                final boolean proposedSymmetric=symmetric.isChecked();status.setText("Obliczanie podglądu…");
                ws.submitGeometryPreview(owner,ws.meshPreviewQuery(candidate,ws.kernelFeatures(),initial?0:ws.project.filletRadius,Math.abs(signed)),mesh->{
                    if(mesh.length>0)status.setText("Podgląd · "+Math.abs(signed)+" mm"+(proposedSymmetric?" · symetrycznie":signed<0?" · odwrotnie":"")+". Zastosuj zapisuje operację.");
                    else status.setText(ws.previewFailure("Brak poprawnego wyniku. Zmień głębokość lub kierunek."));
                });
            }catch(IllegalArgumentException error){status.setText(error.getMessage());}
            ws.invalidate();
        };
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){ws.invalidateGeometryPreview();ws.removeCallbacks(preview);ws.postDelayed(preview,350);}
            public void afterTextChanged(android.text.Editable text){}
        });
        reverse.setOnCheckedChangeListener((button,checked)->{ws.invalidateGeometryPreview();ws.removeCallbacks(preview);ws.postDelayed(preview,100);});
        symmetric.setOnCheckedChangeListener((button,checked)->{reverse.setEnabled(!checked);ws.invalidateGeometryPreview();ws.removeCallbacks(preview);ws.postDelayed(preview,100);});
        ws.postDelayed(preview,100);
        double u=base[0]==1?base[1]:(base[1]+base[3])*.5,v=base[0]==1?base[2]:(base[2]+base[4])*.5;
        int plane=FeatureDirection.basePlane(base[5]);
        double[] origin=plane==1?new double[]{u,0,v}:plane==2?new double[]{0,u,v}:new double[]{u,v,0};
        double[] axis=plane==1?new double[]{0,1,0}:plane==2?new double[]{1,0,0}:new double[]{0,0,1};
        ws.bindSourceExtrusion(profileId,1,input,reverse,symmetric,origin,axis,preview);ws.showExtrusionSketch=true;
    }

}
