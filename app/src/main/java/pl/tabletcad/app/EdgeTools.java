package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Fillet and chamfer of model edges: edge picking, radius / size with live preview, editing. */
final class EdgeTools {
    private final CadWorkspaceView v;
    EdgeTools(CadWorkspaceView view){v=view;}
    void showFillet(int index) {
        show(index,index>=0&&EdgeChamfer.is(v.project.committedFeatures.get(index)));
    }

    void show(int index,boolean chamfer) {
        if(!v.project.hasSolid()||v.project.importedMeshSource!=null) {
            v.operationMessage="Obróbka krawędzi wymaga bryły B-Rep, nie siatki STL";v.invalidate();return;
        }
        v.mode="Wybierz";
        final double[][] candidate={index>=0?v.project.committedFeatures.get(index).clone():null};
        double[] root=v.evaluationBase();
        final double[] rootReference=root==null?new double[]{0,0,0,v.modelWidth,v.modelHeight,0,v.modelDepth}:root.clone();
        final double[] rootEdges=(index<0||candidate[0].length==16)&&!v.project.importedStep&&RootEdgeReference.supports(rootReference)
            ?NativeCadEngine.modelEdges(v.modelWidth,v.modelHeight,v.modelDepth,0,rootReference,new double[0],null):new double[0];
        if(index>=0&&candidate[0].length==16)candidate[0]=RootEdgeReference.anchor(candidate[0],rootReference,rootEdges);
        EditText radius=v.coordinateInput(chamfer?"Wielkość fazy [mm]":"Promień [mm]",index>=0?candidate[0][11]:3);
        android.widget.TextView status=new android.widget.TextView(v.getContext());status.setTextSize(13);
        android.widget.TextView selectionCount=new android.widget.TextView(v.getContext());
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.addView(radius);form.addView(selectionCount);form.addView(status);
        v.slider(form,radius,.1,Math.max(10,v.modelExtent()/2),.1);
        v.new PropertyBuilder().setCompact(Float.NaN,Float.NaN).setTitle(chamfer?(index<0?"Fazuj · wskaż krawędź":"Fazowanie · równe odległości [mm]"):(index<0?"Zaokrąglij · wskaż krawędź":"Zaokrąglenie · promień [mm]")).setView(form)
            .setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",(d,w)-> {
                try {
                    if(candidate[0]==null)throw new IllegalArgumentException("Najpierw dotknij widocznej krawędzi bryły");
                    double[] value=EdgeGroup.withRadius(candidate[0],v.parseCoordinate(radius));
                    if(chamfer)EdgeChamfer.validate(value);else EdgeFillet.validate(value);
                    if(v.evaluateFillet(value,index).length==0)throw new IllegalArgumentException(v.previewFailure("Nie można wykonać operacji na tej krawędzi z podaną wartością lub przebudować kolejnych cech"));
                    if (v.commitModelChange(() -> {
                        if(index<0) {
                            v.preserveExtrudedBase();v.commitActiveFeature();
                            v.project.committedFeatures.add(value);v.project.committedProfileIds.add("");
                        } else v.project.committedFeatures.set(index,value);
                    })) v.operationMessage=chamfer?"Gotowe: fazowanie wskazanej krawędzi "+v.formatMm(value[11]):"Gotowe: zaokrąglenie wskazanej krawędzi R"+v.formatMm(value[11]);
                }catch(IllegalArgumentException e){v.operationMessage=e.getMessage();}
                v.invalidate();
            }).show();
        android.widget.ScrollView owner=v.propertyEditor;
        Runnable preview=()-> {
            if(v.propertyEditor!=owner)return;
            v.invalidateGeometryPreview();
            try {
                double dimension=v.parseCoordinate(radius);
                if(index<0) {
                    candidate[0]=null;
                    selectionCount.setText("Wybrane krawędzie: "+v.filletSelections.size());
                    java.util.List<double[]> selected=new java.util.ArrayList<>();
                    for(int segment:v.filletSelections.values())selected.add(RootEdgeReference.anchor(
                        chamfer?EdgeChamfer.create(v.filletEdges,segment,dimension):EdgeFillet.create(v.filletEdges,segment,dimension),rootReference,rootEdges));
                    candidate[0]=selected.isEmpty()?null:selected.size()==1?selected.get(0):EdgeGroup.create(selected,dimension,chamfer);
                }
                selectionCount.setText("Wybrane krawędzie: "+(candidate[0]==null?0:EdgeGroup.is(candidate[0])?(int)candidate[0][1]:1));
                if(candidate[0]==null){status.setText("Dotykaj kolejnych krawędzi. Ponowne dotknięcie odznacza; przeciągnięcie obraca model.");v.invalidate();return;}
                candidate[0]=EdgeGroup.withRadius(candidate[0],dimension);
                if(chamfer)EdgeChamfer.validate(candidate[0]);else EdgeFillet.validate(candidate[0]);
                double proposedRadius=candidate[0][11];status.setText("Obliczanie podglądu…");
                v.submitGeometryPreview(owner,v.filletPreviewQuery(candidate[0],index),mesh->{
                    if(mesh.length==0)status.setText(v.previewFailure("Brak poprawnego wyniku. Zmniejsz wartość lub wskaż inną krawędź. Model nie został zmieniony."));
                    else status.setText("Podgląd "+(chamfer?"fazy ":"R")+v.formatMm(proposedRadius)+". "+(chamfer?"Równe odsunięcia na obu ścianach. ":"")+"Zastosuj zapisuje operację w historii.");
                });
            }catch(IllegalArgumentException e){status.setText(chamfer?"Wielkość fazy: 0,1–1000 mm":"Promień: 0,1–1000 mm");}
            v.invalidate();
        };
        if(index<0) {
            v.filletEdges=NativeCadEngine.modelEdges(v.modelWidth,v.modelHeight,v.modelDepth,v.project.importedStep?0:v.project.filletRadius,
                v.evaluationBase(),v.kernelFeatures(),v.project.importedStep?v.importedSourceFile().getAbsolutePath():null);
            v.filletSelectionChanged=preview;
            if(v.filletEdges.length==0)status.setText("Nie można odczytać krawędzi bryły");
        }
        radius.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){v.invalidateGeometryPreview();v.removeCallbacks(preview);v.postDelayed(preview,180);}
            public void afterTextChanged(android.text.Editable s){}
        });
        preview.run();
    }

}
