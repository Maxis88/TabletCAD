package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import pl.tabletcad.app.model.*;

/** History operation editor: opens the right dialog for each kind of operation */
final class FeatureEditorTools {
    private final CadWorkspaceView ws;
    FeatureEditorTools(CadWorkspaceView view){ws=view;}
    void showCommittedFeatureEditor(int index) {
        double[] feature = ws.project.committedFeatures.get(index);
        if(ws.project.extrusionExtents.containsKey(index)){ws.showExtrusionExtentEditor(index);return;}
        if(HoleThread.is(feature)){ws.showThreadFeatureEditor(index);return;}
        if(FeaturePattern.is(feature)){ws.featurePatternTools.showEditor(index);return;}
        if(ShellFeature.is(feature)){ws.shellTools.showEditor(index);return;}
        if(BodyCombine.is(feature)){ws.beginBodyCombine((int)feature[1],index);return;}
        if(BodySplit.is(feature)){ws.bodyTools.showSplit(index);return;}
        if(SurfaceSplit.is(feature)||SurfaceSplit.isLoft(feature)){ws.bodyTools.showSplit(index);return;}
        if(SweepLoft.bodyPattern(feature)){ws.showBodyPatternDialog((int)feature[1],index);return;}
        if(SweepLoft.loft(feature)||SweepLoft.sweep(feature)){ws.showLoftSweepEditor(index);return;}
        if(DraftFeature.is(feature)){ws.showDraftEditor(index);return;}
        if(FaceOffset.is(feature)){ws.showFaceOffsetEditor(index);return;}
        if(MateFeature.is(feature)){ws.mateTools.showEditor(index);return;}
        if(BodyInstance.is(feature)){int created=BodyInstance.created(feature);Component c=Component.of(ws.project.components,created);
            if(c!=null&&c.instance())ws.assemblyTools.showInstanceDialog(c);else{ws.operationMessage="Wystąpienie bez komponentu – usuń je z historii i wstaw ponownie";ws.invalidate();}return;}
        if(BodyTransform.is(feature)){ws.bodyTools.showTransform((int)feature[1],index);return;}
        if(HoleFeature.is(feature)){ws.holeEditIndex=index;double[] f=feature;ws.holeReference=new double[]{f[2],f[3],f[4],f[5],f[6],f[7],f[8]};ws.mode="Otwór";ws.holeTools.showPanel();return;}
        if(Revolve.is(feature)){String id=ws.project.committedProfileIds.get(index);ws.revolveTools.show(Revolve.profile(id,feature),feature,index,false);return;}
        if(EdgeFillet.is(feature)||EdgeChamfer.is(feature)){ws.edgeTools.showFillet(index);return;}
        String linkedProfileId=index<ws.project.committedProfileIds.size()?ws.project.committedProfileIds.get(index):null;
        if(feature[0]>=5 || ws.project.isProfileDimensioned(linkedProfileId) || ProfileDependencies.consumers(ws.project,linkedProfileId).size()>1) {
            EditText depth=ws.bound(ws.coordinateInput("Głębokość [mm]",Math.abs(feature[6])),UserParameters.depthKey(index));
            android.widget.CheckBox reverse=ws.extrusionDialogs.directionCheck(feature[6]);
            android.widget.CheckBox symmetric=ws.extrusionDialogs.symmetricCheck(FeatureDirection.isSymmetric(feature[1]),reverse);
            LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);form.addView(depth);form.addView(reverse);
            form.addView(symmetric);
            android.widget.TextView status=new android.widget.TextView(ws.getContext());form.addView(status);
            ws.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle((feature[0]>=5?"Kontur":"Profil źródłowy")+" · głębokość [mm]").setView(form)
                .setNeutralButton("Edytuj profil źródłowy",(d,w)->ws.editSourceProfile(linkedProfileId))
                .setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)-> {
                    try {
                        double value=FeatureDirection.signed(ws.parseCoordinate(depth),reverse.isChecked(),symmetric.isChecked());
                        double[] candidate=feature.clone();candidate[6]=value;
                        candidate[1]=FeatureDirection.withSymmetric(feature[1],symmetric.isChecked());
                        if(ws.commitModelChange(() -> {ws.project.committedFeatures.set(index,candidate);ws.bindField(ws.project,UserParameters.depthKey(index),depth);}))
                            ws.operationMessage="Gotowe: przeliczono operację konturu";
                    }catch(IllegalArgumentException e){ws.operationMessage="Głębokość: 0,1–5000 mm";}
                    ws.invalidate();
                }).show();
            bindCommittedPreview(index,new EditText[]{depth},reverse,symmetric,status,()-> {
                double[] candidate=feature.clone();candidate[6]=FeatureDirection.signed(ws.parseCoordinate(depth),reverse.isChecked(),symmetric.isChecked());
                candidate[1]=FeatureDirection.withSymmetric(feature[1],symmetric.isChecked());return candidate;
            });return;
        }
        LinearLayout fields = new LinearLayout(ws.getContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding((int)ws.dp(16), 0, (int)ws.dp(16), 0);
        String[] labels = {"X1 / środek X [mm]", "Y1 / środek Y [mm]",
            feature[0] == 1 ? "Punkt promienia X [mm]" : "X2 [mm]",
            feature[0] == 1 ? "Punkt promienia Y [mm]" : "Y2 [mm]", "Głębokość [mm]"};
        EditText[] inputs = new EditText[5];
        for (int i = 0; i < inputs.length; i++) {
            android.widget.TextView label = new android.widget.TextView(ws.getContext());
            label.setText(Units.label(labels[i],ws.project.units));
            label.setTextColor(ws.MUTED);
            label.setTextSize(12);
            fields.addView(label);
            inputs[i] = ws.coordinateInput(labels[i], i==4?Math.abs(feature[6]):feature[i + 2]);
            if(i==4)ws.bound(inputs[i],UserParameters.depthKey(index));
            fields.addView(inputs[i]);
        }
        android.widget.CheckBox reverse=ws.extrusionDialogs.directionCheck(feature[6]);fields.addView(reverse);
        android.widget.CheckBox symmetric=ws.extrusionDialogs.symmetricCheck(FeatureDirection.isSymmetric(feature[1]),reverse);fields.addView(symmetric);
        android.widget.TextView status=new android.widget.TextView(ws.getContext());fields.addView(status);
        android.widget.ScrollView scroll = new android.widget.ScrollView(ws.getContext());
        scroll.addView(fields);
        ws.new PropertyBuilder()
            .setTitle((FeatureDirection.isCut(feature[1]) ? "Wycięcie " : "Dodanie ") + (index + 1) + " — profil i głębokość")
            .setView(scroll)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zastosuj", (dialog, which) -> {
                try {
                    double[] candidate = feature.clone();
                    boolean valid = true;
                    for (int i = 0; i < inputs.length; i++) {
                        candidate[i + 2] = ws.parseCoordinate(inputs[i]);
                        valid &= Double.isFinite(candidate[i + 2])
                            && Math.abs(candidate[i + 2]) <= 5000;
                    }
                    valid &= candidate[6] >= 0.1;
                    if(valid)candidate[6]=FeatureDirection.signed(candidate[6],reverse.isChecked(),symmetric.isChecked());
                    candidate[1]=FeatureDirection.withSymmetric(feature[1],symmetric.isChecked());
                    if (!valid) {
                        ws.operationMessage = "Współrzędne: ±5000 mm, głębokość: 0,1–5000 mm";
                    } else {
                        if (ws.commitModelChange(() -> {
                            ws.project.committedFeatures.set(index, candidate);
                            ws.bindField(ws.project,UserParameters.depthKey(index),inputs[4]);
                            String profileId = index < ws.project.committedProfileIds.size()
                                ? ws.project.committedProfileIds.get(index) : "";
                            SketchPrimitive changed=SketchPrimitive.fromFeature(profileId,candidate[0]==1?"Okrąg":"Prostokąt",candidate,2,16);
                            ProfileDependencies.propagate(ws.project,profileId,changed);
                            for (int i = 0; i < ws.project.sketch.size(); i++) {
                                SketchPrimitive p = ws.project.sketch.get(i);
                                if (p.id.equals(profileId)) ws.project.sketch.set(i,changed.withConstruction(p.construction));
                            }
                        })) ws.operationMessage = "Gotowe: przeliczono historię modelu";
                    }
                } catch (NumberFormatException error) {
                    ws.operationMessage = "Niepoprawna głębokość";
                }
                ws.invalidate();
            }).show();
        bindCommittedPreview(index,inputs,reverse,symmetric,status,()-> {
            double[] candidate=feature.clone();
            for(int i=0;i<4;i++) {
                candidate[i+2]=ws.parseCoordinate(inputs[i]);
                if(!Double.isFinite(candidate[i+2])||Math.abs(candidate[i+2])>5000)throw new IllegalArgumentException("Współrzędne: ±5000 mm");
            }
            candidate[6]=FeatureDirection.signed(ws.parseCoordinate(inputs[4]),reverse.isChecked(),symmetric.isChecked());
            candidate[1]=FeatureDirection.withSymmetric(feature[1],symmetric.isChecked());return candidate;
        });
    }

    void bindCommittedPreview(int index,EditText[] inputs,android.widget.CheckBox reverse,android.widget.CheckBox symmetric,
            android.widget.TextView status,java.util.function.Supplier<double[]> proposed) {
        android.widget.ScrollView owner=ws.propertyEditor;
        Runnable preview=()-> {
            if(owner==null||ws.propertyEditor!=owner)return;
            ws.invalidateGeometryPreview();
            try {
                double[] candidate=proposed.get(),active=ws.activeFeatureValues();
                int length=active==null?0:active.length;
                for(int i=0;i<ws.project.committedFeatures.size();i++)length+=(i==index?candidate:ws.project.committedFeatures.get(i)).length;
                double[] packed=new double[length];int offset=0;
                for(int i=0;i<ws.project.committedFeatures.size();i++) {
                    double[] record=i==index?candidate:ws.project.committedFeatures.get(i);
                    System.arraycopy(record,0,packed,offset,record.length);offset+=record.length;
                }
                if(active!=null)System.arraycopy(active,0,packed,offset,active.length);
                status.setText("Obliczanie podglądu…");
                ws.submitGeometryPreview(owner,ws.meshPreviewQuery(ws.project.featureBase,packed,ws.project.filletRadius,ws.modelDepth),mesh->{
                    if(mesh.length>0)status.setText("Podgląd · "+Math.abs(candidate[6])+" mm"+(FeatureDirection.isSymmetric(candidate[1])?" · symetrycznie":candidate[6]<0?" · odwrotnie":"")+". Zastosuj zapisuje zmianę.");
                    else status.setText(ws.previewFailure("Brak poprawnego wyniku. Zmień głębokość lub kierunek."));
                });
            }catch(IllegalArgumentException error){status.setText(error.getMessage());}
            ws.invalidate();
        };
        for(EditText input:inputs)input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){ws.invalidateGeometryPreview();ws.removeCallbacks(preview);ws.postDelayed(preview,350);}
            public void afterTextChanged(android.text.Editable text){}
        });
        reverse.setOnCheckedChangeListener((button,checked)->{ws.invalidateGeometryPreview();ws.removeCallbacks(preview);ws.postDelayed(preview,100);});
        symmetric.setOnCheckedChangeListener((button,checked)->{reverse.setEnabled(!checked);ws.invalidateGeometryPreview();ws.removeCallbacks(preview);ws.postDelayed(preview,100);});
        ws.postDelayed(preview,100);
    }

}
