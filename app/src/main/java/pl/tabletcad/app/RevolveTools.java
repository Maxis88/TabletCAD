package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Revolve dialog: axis, angle, join / cut / new body, live preview. */
final class RevolveTools {
    private final CadWorkspaceView v;
    RevolveTools(CadWorkspaceView view){v=view;}
    void show(SketchPrimitive profile,double[] original,int index,boolean root) {
        v.mode="Wybierz";
        if(original==null){v.cameraYaw=-28;v.cameraPitch=22;}
        List<double[]> axes=new ArrayList<>();List<String> labels=new ArrayList<>();
        if(original!=null){axes.add(java.util.Arrays.copyOfRange(original,16,20));labels.add("Zapisana oś operacji");}
        axes.add(new double[]{0,0,1,0});labels.add("Oś X szkicu");axes.add(new double[]{0,0,0,1});labels.add("Oś Y szkicu");
        boolean sameFrame=true;
        if(original!=null){double[] frame={v.project.sketchOriginX,v.project.sketchOriginY,v.project.sketchOriginZ,v.project.sketchUx,v.project.sketchUy,v.project.sketchUz,v.project.sketchVx,v.project.sketchVy,v.project.sketchVz};
            for(int i=0;i<9;i++)if(Math.abs(frame[i]-original[7+i])>1e-8)sameFrame=false;}
        int guides=0;
        if(sameFrame)for(SketchPrimitive p:v.project.sketch)if(p.type.equals("Pomocnicza")) {
            axes.add(new double[]{p.x1,p.y1,p.x2,p.y2});labels.add("Linia pomocnicza "+(++guides));
        }
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView axisLabel=new android.widget.TextView(v.getContext());axisLabel.setText("Oś w płaszczyźnie profilu");form.addView(axisLabel);
        android.widget.Spinner axis=new android.widget.Spinner(v.getContext());
        android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(v.getContext(),android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);axis.setAdapter(adapter);axis.setContentDescription("Oś obrotu profilu");form.addView(axis);
        android.widget.TextView angleLabel=new android.widget.TextView(v.getContext());angleLabel.setText("Kąt obrotu [°] · 0,1–360");form.addView(angleLabel);
        EditText angle=v.coordinateInput("Kąt obrotu [°]",original==null?360:Math.abs(original[6]));form.addView(angle);
        android.widget.CheckBox reverse=v.extrusionDialogs.directionCheck(original==null?360:original[6]);form.addView(reverse);
        android.widget.CheckBox cut=new android.widget.CheckBox(v.getContext());cut.setText("Wytnij materiał obrotem");cut.setChecked(original!=null&&original[1]==1);
        if(!root)form.addView(cut);
        android.widget.CheckBox newBody=new android.widget.CheckBox(v.getContext());newBody.setText("Nowa bryła (osobno od istniejących)");
        if(!root&&original==null)form.addView(newBody);
        newBody.setOnCheckedChangeListener((b,c)->{if(c)cut.setChecked(false);cut.setEnabled(!c);});
        android.widget.TextView status=new android.widget.TextView(v.getContext());form.addView(status);
        java.util.function.Supplier<double[]> candidate=()-> {
            double value=v.parseCoordinate(angle);if(!Double.isFinite(value)||value<.1||value>360)throw new IllegalArgumentException("Kąt: 0,1–360 stopni");
            value=reverse.isChecked()?-value:value;double[] selected=axes.get(axis.getSelectedItemPosition());
            if(original==null)return Revolve.create(profile,v.project,selected,value,!root&&cut.isChecked());
            double[] f=original.clone();f[6]=value;f[1]=!root&&cut.isChecked()?1:0;System.arraycopy(selected,0,f,16,4);Revolve.validate(f);return f;
        };
        v.new PropertyBuilder().setTitle(root?"Obrót profilu · bryła bazowa":"Obrót profilu · dodanie / wycięcie").setView(form)
            .setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)-> {
                try {
                    double[] f=candidate.get();
                    if(newBody.isChecked()){String baseline;try{baseline=v.project.toJson().toString();}catch(org.json.JSONException e){throw new IllegalArgumentException(e.getMessage());}v.commitProjectAsync(v.revolvedBodyProject(f,profile.id),baseline,"Obrót profilu → nowa bryła");v.selectedSketchId=null;v.invalidate();return;}
                    if(v.evaluateRevolve(f,index,root).length==0)throw new IllegalArgumentException(v.previewFailure("Nie można zbudować obrotu lub odtworzyć dalszych operacji"));
                    if (v.commitModelChange(() -> {
                        if(root){v.project.featureBase=f;v.project.featureBaseProfileId=profile.id;}
                        else if(index<0){v.preserveExtrudedBase();v.commitActiveFeature();v.project.committedFeatures.add(f);v.project.committedProfileIds.add(profile.id);}
                        else v.project.committedFeatures.set(index,f);
                    })) {
                        v.selectedSketchId=null;
                        v.operationMessage="Zapisano obrót profilu "+String.format(java.util.Locale.US,"%.3f°",f[6])+" w historii modelu";
                    }
                }catch(IllegalArgumentException error){v.operationMessage=error.getMessage();}
                v.invalidate();
            }).show();
        android.widget.ScrollView owner=v.propertyEditor;
        Runnable preview=()-> {
            if(v.propertyEditor!=owner)return;v.invalidateGeometryPreview();
            try {
                status.setText("Obliczanie podglądu…");
                v.submitGeometryPreview(owner,newBody.isChecked()?v.newBodyRevolveQuery(candidate.get(),profile.id):v.revolvePreviewQuery(candidate.get(),index,root),mesh->{
                    if(mesh.length==0)status.setText(v.previewFailure("Brak poprawnej bryły. Sprawdź oś, profil i przecięcie z modelem. Dokument pozostaje bez zmian."));
                    else status.setText("Podgląd obrotu. Zastosuj zapisuje edytowalną operację w historii.");
                });
            }catch(IllegalArgumentException error){status.setText(error.getMessage());}v.invalidate();
        };
        angle.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int n){}
            public void onTextChanged(CharSequence s,int a,int b,int c){v.invalidateGeometryPreview();v.removeCallbacks(preview);v.postDelayed(preview,180);}public void afterTextChanged(android.text.Editable s){} });
        reverse.setOnCheckedChangeListener((button,checked)->preview.run());cut.setOnCheckedChangeListener((button,checked)->preview.run());
        newBody.setOnCheckedChangeListener((button,checked)->{if(checked)cut.setChecked(false);cut.setEnabled(!checked);preview.run();});
        axis.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){preview.run();}
            public void onNothingSelected(android.widget.AdapterView<?> parent){} });
        preview.run();
    }

}
