package pl.tabletcad.app;

import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.UUID;
import pl.tabletcad.app.model.CadProject;
import pl.tabletcad.app.model.SketchPrimitive;

/** Sketch text: insert and edit dialog, emboss / engrave / new-body extrusion with the on-model depth arrow. */
final class TextTools {
    private final CadWorkspaceView v;
    private AndroidTextFont font;
    TextTools(CadWorkspaceView view){v=view;}
    SketchText.Font font(){if(font==null)font=new AndroidTextFont();return font;}
    /** New text at (x, y) or edit of an existing one: content, height, flat or along a circle, font. */
    private Object owner;private EditText placeX,placeY;
    /** The text panel is open: a tap on the sketch moves the anchor instead of closing the panel. */
    boolean placing(){return owner!=null&&owner==v.propertyEditor;}
    void place(double x,double y){if(!placing())return;placeX.setText(Units.input(x,v.project.units));placeY.setText(Units.input(y,v.project.units));v.operationMessage="Przesunięto punkt zaczepienia napisu – dotknij innego miejsca albo Wstaw/Zastosuj";}
    void showEditor(SketchPrimitive existing,double x,double y) {
        double[] p=existing!=null?existing.textParams():SketchText.params(x,y,5,SketchText.FLAT,0,20,0,0);
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        EditText content=new EditText(v.getContext());content.setSingleLine(true);content.setHint("Treść napisu");content.setText(existing!=null?existing.text():"");form.addView(content);
        android.widget.Spinner layout=new android.widget.Spinner(v.getContext());layout.setAdapter(new android.widget.ArrayAdapter<>(v.getContext(),android.R.layout.simple_spinner_dropdown_item,SketchText.LAYOUTS));layout.setSelection((int)p[3]);form.addView(layout);
        EditText height=v.labeledCoordinate(form,"Wysokość liter [mm]",p[2]);v.slider(form,height,1,Math.max(50,v.modelExtent()/2),.5);
        EditText radius=v.labeledCoordinate(form,"Promień okręgu [mm]",p[5]);
        EditText angle=v.labeledCoordinate(form,"Kąt [°] (płasko: obrót; okrąg: środek napisu)",p[4]);v.slider(form,angle,-180,180,1);
        android.widget.Spinner font=new android.widget.Spinner(v.getContext());font.setAdapter(new android.widget.ArrayAdapter<>(v.getContext(),android.R.layout.simple_spinner_dropdown_item,SketchText.FONTS));font.setSelection((int)p[6]);form.addView(font);
        EditText spacing=v.labeledCoordinate(form,"Dodatkowy odstęp liter [mm]",p[7]);
        EditText ax=v.labeledCoordinate(form,"Punkt zaczepienia X [mm] (okrąg: środek)",p[0]),ay=v.labeledCoordinate(form,"Punkt zaczepienia Y [mm]",p[1]);
        Runnable visibility=()->v.fieldVisible(radius,layout.getSelectedItemPosition()!=SketchText.FLAT);visibility.run();
        layout.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> a,View v,int i,long l){visibility.run();}public void onNothingSelected(android.widget.AdapterView<?> a){}});
        CadWorkspaceView.PropertyBuilder builder=v.new PropertyBuilder().setTitle(existing==null?"Napis":"Napis · edycja").setView(form).setNegativeButton("Anuluj",null)
            .setPositiveButton(existing==null?"Wstaw":"Zastosuj",(d,w)->{
                try {
                    double[] params=SketchText.params(v.parseCoordinate(ax),v.parseCoordinate(ay),v.parseCoordinate(height),layout.getSelectedItemPosition(),v.parseCoordinate(angle),v.parseCoordinate(radius),font.getSelectedItemPosition(),v.parseCoordinate(spacing));
                    String value=content.getText().toString();
                    if(existing==null) {
                        SketchPrimitive t=SketchText.create(UUID.randomUUID().toString(),value,params,font());
                        v.checkpoint();v.preserveExtrudedBase();v.commitActiveFeature();v.project.sketch.add(t);v.selectedSketchId=t.id;v.projectRepository.save(v.project);
                        v.operationMessage="Napis „"+value+"” · "+SketchText.loopCount(t.textLoops())+" konturów. Wyciągnij: dodaj na ścianie albo wytnij (grawer). Aby przesunąć lub zmienić: narzędzie Napis → dotknij napisu.";
                    } else {
                        SketchPrimitive t=SketchText.with(existing,value,params,font());v.replaceSketchEntity(existing,t,true);v.selectedSketchId=t.id;
                        v.operationMessage="Zmieniono napis „"+value+"”";
                    }
                    v.invalidate();
                }catch(IllegalArgumentException error){v.operationMessage=error.getMessage();v.invalidate();}
            });
        if(existing!=null)builder.setNeutralButton("Usuń element",(d,w)->v.deleteSketchEntity(existing.id));
        android.widget.TextView status=new android.widget.TextView(v.getContext());form.addView(status,0);
        builder.show();
        // Live preview on the sketch (dashed): the text stays visible while the lower settings are
        // changed, and a reason is shown instead of silently not inserting.
        final android.widget.Button confirm=v.panelConfirm;
        Runnable preview=()->{
            v.sketchGhost.clear();
            try {
                double[] params=SketchText.params(v.parseCoordinate(ax),v.parseCoordinate(ay),v.parseCoordinate(height),layout.getSelectedItemPosition(),v.parseCoordinate(angle),v.parseCoordinate(radius),font.getSelectedItemPosition(),v.parseCoordinate(spacing));
                String value=content.getText().toString();
                SketchPrimitive t=existing==null?SketchText.create("preview",value,params,font()):SketchText.with(existing,value,params,font());
                v.sketchGhost.add(t);status.setText("Podgląd: „"+value+"” · "+SketchText.loopCount(t.textLoops())+" konturów");if(confirm!=null)confirm.setEnabled(true);
            }catch(RuntimeException error){status.setText(error.getMessage()==null?"Niepoprawne parametry napisu":error.getMessage());if(confirm!=null)confirm.setEnabled(false);}
            v.invalidate();
        };
        v.watchFields(form,preview);preview.run();
        owner=v.propertyEditor;placeX=ax;placeY=ay;
    }
    /** Embossing or engraving of a sketch text (or letters as a new body): depth, operation, direction. */
    void showExtrusion(SketchPrimitive text) {
        if(!v.project.hasSolid()){v.operationMessage="Napis wyciąga się na bryle (wypukły lub grawerowany) albo jako nową bryłę obok niej – najpierw utwórz bryłę, np. płytkę pod napis";v.invalidate();return;}
        if(v.inSketch()&&SketchTransactions.active(v.project)){v.finishSketchWork(()->showExtrusion(text));return;}
        v.mode="Wybierz";
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        String[] labels={"Połącz (wypukły)","Wytnij (grawer)","Nowa bryła","Część wspólna"},ops={"join","cut","new","common"};
        android.widget.Spinner operation=new android.widget.Spinner(v.getContext());operation.setAdapter(new android.widget.ArrayAdapter<>(v.getContext(),android.R.layout.simple_spinner_dropdown_item,labels));form.addView(operation);
        EditText depth=v.labeledCoordinate(form,"Głębokość [mm]",1);v.slider(form,depth,.1,Math.max(30,v.modelExtent()),.1);
        android.widget.CheckBox reverse=new android.widget.CheckBox(v.getContext());reverse.setText("Odwróć kierunek");form.addView(reverse);
        android.widget.CheckBox symmetric=new android.widget.CheckBox(v.getContext());symmetric.setText("Symetrycznie");form.addView(symmetric);
        // A cut goes into the face the text was drawn on, an addition out of it ("Odwróć" swaps either).
        final int target=v.project.selectedBody>0?v.project.selectedBody:1;
        android.content.DialogInterface.OnClickListener apply=(d,w)->{
            try {
                String baseline=v.project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));BodyGraph.begin(next);
                SketchPrimitive t=null;for(SketchPrimitive e:next.sketch)if(e.id.equals(text.id))t=e;if(t==null)throw new IllegalArgumentException("Napis nie jest dostępny");
                String op=ops[operation.getSelectedItemPosition()];
                double[] feature=SketchText.feature(next,t,op,v.parseCoordinate(depth),reverse.isChecked(),symmetric.isChecked());
                next.committedFeatures.add(feature);next.committedProfileIds.add(SketchText.REF+next.sketchId+"/"+t.id);
                next.featureBodyTargets.add(op.equals("new")?0:target);next.featureBodyOutputs.add(op.equals("new")?BodyGraph.allocate(next):target);
                v.commitProjectAsync(next,baseline,SketchText.describe(feature));
            }catch(org.json.JSONException|IllegalArgumentException error){v.operationMessage=error.getMessage();v.invalidate();}
        };
        operation.setSelection(1);
        v.new PropertyBuilder().setTitle("Wyciągnij napis „"+text.text()+"”").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",apply).show();
        v.capturedPreview(form,apply,"Podgląd napisu.");
        // Orange arrow on the model at the text anchor: dragging it sets the depth (direction follows "Odwróć").
        double[] tp=text.textParams();double[] anchor=v.sketchToWorld(tp[0],tp[1]);
        double[] normal={v.project.sketchUy*v.project.sketchVz-v.project.sketchUz*v.project.sketchVy,v.project.sketchUz*v.project.sketchVx-v.project.sketchUx*v.project.sketchVz,v.project.sketchUx*v.project.sketchVy-v.project.sketchUy*v.project.sketchVx};
        v.attachFieldHandle(anchor,normal,depth,.1,5000,"Głębokość");
        if(v.fieldHandle!=null)v.fieldHandle.axisSource=()->(reverse.isChecked()^ops[operation.getSelectedItemPosition()].equals("cut"))?new double[]{-normal[0],-normal[1],-normal[2]}:normal;
    }
}
