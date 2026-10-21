package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Assembly mates: face-to-face, flush, coaxial and angle mates and their editor. */
final class MateTools {
    private final CadWorkspaceView v;
    MateTools(CadWorkspaceView view){v=view;}
    /** Gap and kind of a persistent mate from the history. */
    void showEditor(int index) {
        double[] original=v.project.committedFeatures.get(index);
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(v.getContext());info.setText("Dosunięcie jest przeliczane przy każdej zmianie modelu: bryła podąża za ścianą, do której ją dosunięto.");form.addView(info);
        final boolean coaxial=original[1]==MateFeature.AXIS,angled=original[1]==MateFeature.ANGLE;
        EditText gap=v.labeledCoordinate(form,angled?"Kąt [°]":"Odstęp [mm]",original[3]);if(angled)v.slider(form,gap,0,180,.5);else v.slider(form,gap,-v.modelExtent()/2,v.modelExtent()/2,.5);
        android.widget.CheckBox flush=new android.widget.CheckBox(v.getContext());flush.setText("Wyrównaj (ściany w jednej płaszczyźnie)");flush.setChecked(original[1]==MateFeature.FLUSH);form.addView(flush);
        if(angled){flush.setEnabled(false);flush.setVisibility(View.GONE);info.setText("Kąt między ścianami jest przeliczany przy każdej zmianie modelu.");}
        if(coaxial){gap.setEnabled(false);flush.setEnabled(false);info.setText("Współosiowość jest przeliczana przy każdej zmianie modelu. Położenie wzdłuż osi ustawisz dosunięciem płaskich ścian.");}
        android.content.DialogInterface.OnClickListener apply=(d,w)->{
            try{double[] updated=coaxial?original.clone():angled?MateFeature.withGap(original,MateFeature.ANGLE,Double.parseDouble(gap.getText().toString().replace(',','.'))):MateFeature.withGap(original,flush.isChecked()?MateFeature.FLUSH:MateFeature.CONTACT,v.parseCoordinate(gap));String baseline=v.project.toJson().toString();CadProject next=v.copyProject(v.project);next.committedFeatures.set(index,updated);v.commitProjectAsync(next,baseline,"Zaktualizowano: "+MateFeature.describe(updated,BodyGraph.name(next,MateFeature.other(updated))));}
            catch(org.json.JSONException|IllegalArgumentException error){v.operationMessage=error.getMessage();v.invalidate();}
        };
        v.new PropertyBuilder().setTitle("Edytuj dosunięcie").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zastosuj",apply).show();
        v.capturedPreview(form,apply,"Podgląd położenia bryły.");
    }
    void begin() {
        if(!v.project.hasSolid()||BodyGraph.bodies(v.project).size()<2){v.operationMessage="Dosuń ściany łączy dwie bryły – potrzebne są co najmniej dwie bryły (np. wyciągnięcie jako „Nowa bryła” albo wystąpienie)";v.invalidate();return;}
        v.closePropertyEditor();v.mode="Dosuń";v.mateComponent=null;v.mateBodies.clear();v.mateMovingAxis=null;v.mateMovingPoint=null;
        v.operationMessage="Dotknij płaskiej lub walcowej ściany bryły, która ma się przesunąć";v.invalidate();
    }
    /** Axis {point, direction} of a cylindrical/conical face. */
    double[] faceAxis(int face){int old=v.selectedFace;v.selectedFace=face;try{return v.selectedSurfaceAxis();}finally{v.selectedFace=old;}}
    void tap(int face) {
        if(face<0){v.operationMessage="Dotknij ściany bryły";v.invalidate();return;}
        boolean planar=v.isFacePlanar(face);double[] axis=planar?null:faceAxis(face);
        if(!planar&&axis==null){v.operationMessage="Dotknij płaskiej ściany albo walcowej ściany (otwór, wałek)";v.invalidate();return;}
        double l=Math.sqrt(v.selectedNormalX*v.selectedNormalX+v.selectedNormalY*v.selectedNormalY+v.selectedNormalZ*v.selectedNormalZ);
        double[] point={v.selectedPointX,v.selectedPointY,v.selectedPointZ},normal={v.selectedNormalX/l,v.selectedNormalY/l,v.selectedNormalZ/l};int body=v.faceBodies.getOrDefault(face,1);
        if(v.mateComponent==null&&v.mateBodies.isEmpty()) {
            Component c=Component.of(v.project.components,body);
            if(c!=null&&c.instance())v.mateComponent=c.id;else if(c!=null)v.mateBodies.addAll(c.bodies);else v.mateBodies.add(body);
            v.mateMovingAxis=axis;v.mateMovingPoint=point;v.mateMovingNormal=normal;v.mateMovingCreator=v.faceFeatures.get(face);v.selectedFace=face;
            v.operationMessage=(axis!=null?"Teraz dotknij walcowej ściany, z którą oś ma być współosiowa":"Teraz dotknij ściany, do której ta ściana ma przylegać")+" (inna bryła)";v.invalidate();return;
        }
        java.util.Set<Integer> moving=new java.util.LinkedHashSet<>(v.mateBodies);if(v.mateComponent!=null){Component c=Assembly.find(v.project,v.mateComponent);if(c!=null)moving.addAll(c.bodies);}
        if(moving.contains(body)){v.operationMessage="Wybierz ścianę innej bryły";v.invalidate();return;}
        if((v.mateMovingAxis!=null)!=(axis!=null)){v.operationMessage=v.mateMovingAxis!=null?"Pierwsza ściana była walcowa – dotknij walcowej ściany drugiej bryły":"Pierwsza ściana była płaska – dotknij płaskiej ściany drugiej bryły";v.invalidate();return;}
        final double[] tp=point,tn=normal,ta=axis,mp=v.mateMovingPoint,mn=v.mateMovingNormal,ma=v.mateMovingAxis;final String component=v.mateComponent;final java.util.Set<Integer> bodies=new java.util.LinkedHashSet<>(v.mateBodies);
        final Integer movingCreator=v.mateMovingCreator,targetCreator=v.faceFeatures.get(face);final int targetBody=body;
        // A plane mate of one ordinary body is stored as a persistent mate (re-solved when either body changes).
        // Single-body instances are mated persistently too: their faces are created by the instance record.
        Component instanceComponent=component==null?null:Assembly.find(v.project,component);
        final Integer instanceBody=instanceComponent!=null&&instanceComponent.bodies.size()==1?instanceComponent.bodies.iterator().next():null;
        final boolean persistent=(component==null&&bodies.size()==1||instanceBody!=null)&&movingCreator!=null&&targetCreator!=null&&v.project.bodyGraphStart>=0;
        v.mode="Wybierz";v.selectedFace=face;
        LinearLayout form=new LinearLayout(v.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(v.getContext());form.addView(info);
        EditText gap=null;android.widget.CheckBox flush=new android.widget.CheckBox(v.getContext());
        if(ta==null){info.setText("Ściany zostaną ustawione naprzeciw siebie (styk) albo w jednej płaszczyźnie (wyrównanie).");gap=v.labeledCoordinate(form,"Odstęp [mm]",0);v.slider(form,gap,-v.modelExtent()/2,v.modelExtent()/2,.5);flush.setText("Wyrównaj (ściany w jednej płaszczyźnie, ten sam kierunek)");form.addView(flush);}
        else info.setText("Osie walcowych ścian zostaną wyrównane (współosiowo). Położenie wzdłuż osi ustawisz kolejnym dosunięciem płaskich ścian.");
        final EditText gapField=gap;
        final android.widget.CheckBox angleMode=new android.widget.CheckBox(v.getContext());final EditText angleField;
        if(ta==null){angleMode.setText("Kąt między ścianami (zamiast styku)");form.addView(angleMode);angleField=v.labeledCoordinate(form,"Kąt [°]",90);angleField.setTag(null);}else angleField=null;
        android.content.DialogInterface.OnClickListener apply=(d,w)->{
            try {
                if(angleField!=null&&angleMode.isChecked()) {
                    if(!persistent||component!=null&&instanceBody==null)throw new IllegalArgumentException("Kąt między ścianami wymaga zwykłej bryły lub wystąpienia jednej bryły");
                    double angle=Double.parseDouble(angleField.getText().toString().replace(',','.'));
                    String baseline=v.project.toJson().toString();CadProject next=v.copyProject(v.project);
                    int movingBody=component!=null?instanceBody:bodies.iterator().next();
                    double[] mate=MateFeature.create(MateFeature.ANGLE,targetBody,angle,mp,mn,movingCreator,tp,tn,targetCreator);
                    next.committedFeatures.add(mate);next.committedProfileIds.add("");next.featureBodyTargets.add(movingBody);next.featureBodyOutputs.add(movingBody);
                    BodyGraph.validate(next);v.commitProjectAsync(next,baseline,"Ustawiono kąt "+v.formatAngle(angle)+" między ścianami");return;
                }
                double[][] delta=ta==null?Assembly.planeDelta(mp,mn,tp,tn,v.parseCoordinate(gapField),flush.isChecked()):Assembly.axisDelta(new double[]{ma[0],ma[1],ma[2]},new double[]{ma[3],ma[4],ma[5]},new double[]{ta[0],ta[1],ta[2]},new double[]{ta[3],ta[4],ta[5]});
                String baseline=v.project.toJson().toString();CadProject next=v.copyProject(v.project);String name;
                if(component!=null&&persistent) {
                    Component c=Assembly.find(next,component);name=c.name;
                    double[] mate=ta!=null?MateFeature.create(MateFeature.AXIS,targetBody,0,new double[]{ma[0],ma[1],ma[2]},new double[]{ma[3],ma[4],ma[5]},movingCreator,new double[]{ta[0],ta[1],ta[2]},new double[]{ta[3],ta[4],ta[5]},targetCreator)
                        :MateFeature.create(flush.isChecked()?MateFeature.FLUSH:MateFeature.CONTACT,targetBody,v.parseCoordinate(gapField),mp,mn,movingCreator,tp,tn,targetCreator);
                    next.committedFeatures.add(mate);next.committedProfileIds.add("");next.featureBodyTargets.add(instanceBody);next.featureBodyOutputs.add(instanceBody);
                } else if(component!=null) {
                    Component c=Assembly.find(next,component);name=c.name;
                    for(int index:Assembly.instanceRecords(next,c))next.committedFeatures.set(index,Assembly.composeDelta(delta,next.committedFeatures.get(index)));
                } else {
                    if(persistent) {
                        int movingBody=bodies.iterator().next();
                        double[] mate=ta!=null?MateFeature.create(MateFeature.AXIS,targetBody,0,new double[]{ma[0],ma[1],ma[2]},new double[]{ma[3],ma[4],ma[5]},movingCreator,new double[]{ta[0],ta[1],ta[2]},new double[]{ta[3],ta[4],ta[5]},targetCreator)
                            :MateFeature.create(flush.isChecked()?MateFeature.FLUSH:MateFeature.CONTACT,targetBody,v.parseCoordinate(gapField),mp,mn,movingCreator,tp,tn,targetCreator);
                        next.committedFeatures.add(mate);next.committedProfileIds.add("");next.featureBodyTargets.add(movingBody);next.featureBodyOutputs.add(movingBody);
                    } else {
                    List<double[]> records=Assembly.transformRecords(delta);if(records.isEmpty())throw new IllegalArgumentException("Bryła już jest w tym położeniu");
                    for(int b:bodies)for(double[] r:records){next.committedFeatures.add(r);next.committedProfileIds.add("");next.featureBodyTargets.add(b);next.featureBodyOutputs.add(b);}
                    }
                    name=bodies.size()==1?BodyGraph.name(next,bodies.iterator().next()):bodies.size()+" brył";
                }
                if(v.previewSink==null){v.mateComponent=null;v.mateBodies.clear();v.mateMovingAxis=null;}
                v.commitProjectAsync(next,baseline,ta!=null?"Ustawiono współosiowo: "+name:"Dosunięto „"+name+"” do wskazanej ściany"+(flush.isChecked()?" (wyrównanie)":"")+(gapField!=null&&v.parseCoordinate(gapField)!=0?" z odstępem "+v.formatMm(v.parseCoordinate(gapField)):""));
            }catch(org.json.JSONException|IllegalArgumentException error){v.operationMessage=error.getMessage();v.invalidate();}
        };
        v.new PropertyBuilder().setTitle(ta!=null?"Dosuń · współosiowo":"Dosuń ściany").setView(form).setNegativeButton("Anuluj",(d,w)->{v.mateComponent=null;v.mateBodies.clear();v.mateMovingAxis=null;}).setPositiveButton("Dosuń",apply).show();
        v.capturedPreview(form,apply,"Podgląd położenia bryły.");
    }
}
