package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Body properties, materials and densities. */
final class MaterialTools {
    private final CadWorkspaceView ws;
    MaterialTools(CadWorkspaceView view){ws=view;}
    void showBodyProperties() {
        if(!ws.project.hasSolid()||ws.project.importedMeshSource!=null){ws.operationMessage="Dokładne właściwości wymagają bryły B-Rep, nie siatki STL";ws.invalidate();return;}
        double[] properties=NativeCadEngine.bodyProperties(ws.modelWidth,ws.modelHeight,ws.modelDepth,ws.project.importedStep?0:ws.project.filletRadius,
            ws.evaluationBase(),ws.kernelFeatures(),ws.project.importedStep?ws.importedSourceFile().getAbsolutePath():null);
        if(properties.length!=7){ws.operationMessage="Nie można obliczyć właściwości aktualnej bryły";ws.invalidate();return;}
        android.widget.TextView values=new android.widget.TextView(ws.getContext());values.setTextSize(16);
        String mass="Masa: przypisz gęstość materiału";
        if(ws.project.materialDensityKgM3>0) {
            try { mass=String.format(java.util.Locale.US,"Masa: %.9g kg\nGęstość: %.9g kg/m³",
                MaterialDensity.massKg(properties[0],ws.project.materialDensityKgM3),ws.project.materialDensityKgM3); }
            catch(IllegalArgumentException error){mass="Masa: "+error.getMessage();}
        }
        String u=ws.project.units;int digits=Math.max(ws.project.displayDecimals,u.equals("mm")?6:8);
        values.setText(mass+"\n\n"+String.format(java.util.Locale.US,
            "Objętość: %s\n\nPole powierzchni: %s\n\nŚrodek objętości [%s]\nX: %s\nY: %s\nZ: %s\n\nBryły: %.0f\nŚciany: %.0f\n\nWynik dla całego modelu B-Rep OCCT. Masa zakłada jednakową gęstość wszystkich brył. Środek objętości odpowiada wtedy środkowi masy. Wartości z chwili otwarcia panelu.",
            Units.volume(properties[0],u,digits),Units.area(properties[1],u,digits),u,Units.number(properties[2],u,digits),Units.number(properties[3],u,digits),Units.number(properties[4],u,digits),properties[5],properties[6]));
        // Per-body mass properties (inertia about each centroid, principal moments) with per-body densities.
        StringBuilder perBody=new StringBuilder();
        try {
            List<BodyAnalysis.Body> bodies=BodyAnalysis.read(ws.project,NativeCadEngine.bodyInertia(ws.modelWidth,ws.modelHeight,ws.modelDepth,ws.project.importedStep?0:ws.project.filletRadius,
                ws.evaluationBase(),ws.kernelFeatures(),ws.project.importedStep?ws.importedSourceFile().getAbsolutePath():null));
            for(BodyAnalysis.Body b:bodies) {
                perBody.append("\n\n— ").append(BodyGraph.name(ws.project,b.id)).append(" —\nObjętość: ").append(Units.volume(b.volume,u,digits));
                perBody.append(String.format(java.util.Locale.US,"\nŚrodek [%s]: %s; %s; %s",u,Units.number(b.centroid[0],u,digits),Units.number(b.centroid[1],u,digits),Units.number(b.centroid[2],u,digits)));
                if(b.hasMass()) {
                    perBody.append(String.format(java.util.Locale.US,"\nGęstość: %.6g kg/m³ · masa: %.9g kg",b.density,b.mass()));
                    perBody.append(String.format(java.util.Locale.US,"\nMomenty bezwładności względem środka masy [kg·mm²]:\nIxx %.6g · Iyy %.6g · Izz %.6g\nIxy %.6g · Ixz %.6g · Iyz %.6g\nGłówne: %.6g · %.6g · %.6g",
                        b.kgmm2(b.tensor[0]),b.kgmm2(b.tensor[1]),b.kgmm2(b.tensor[2]),b.kgmm2(b.tensor[3]),b.kgmm2(b.tensor[4]),b.kgmm2(b.tensor[5]),b.kgmm2(b.principal[0]),b.kgmm2(b.principal[1]),b.kgmm2(b.principal[2])));
                } else perBody.append(String.format(java.util.Locale.US,"\nMomenty geometryczne (gęstość 1) [mm⁵]: Ixx %.6g · Iyy %.6g · Izz %.6g\nPrzypisz gęstość, aby otrzymać masę i momenty w kg·mm².",b.tensor[0],b.tensor[1],b.tensor[2]));
            }
            if(bodies.size()>1){double[] c=BodyAnalysis.centre(bodies);perBody.append(String.format(java.util.Locale.US,"\n\nŚrodek %s całego modelu: %s; %s; %s",c[3]==1?"masy":"objętości",Units.number(c[0],u,digits),Units.number(c[1],u,digits),Units.number(c[2],u,digits)));}
        }catch(IllegalArgumentException error){perBody.append("\n\nAnaliza brył: ").append(error.getMessage());}
        values.setText(values.getText()+perBody.toString());
        android.widget.ScrollView scroll=new android.widget.ScrollView(ws.getContext());scroll.addView(values);
        ws.new PropertyBuilder().setTitle("Właściwości bryły · OCCT").setView(scroll)
            .setNeutralButton("Materiał…",(d,w)->showMaterialChoice()).setNegativeButton("Zamknij",null).show();
    }

    /** Document or per-body material from the catalog, or a custom density. */
    void showMaterialChoice() {
        List<Integer> ids=new ArrayList<>(BodyGraph.bodies(ws.project));List<String> targets=new ArrayList<>();targets.add("Cały dokument (domyślna)");
        for(int id:ids)targets.add(BodyGraph.name(ws.project,id)+(ws.project.bodyDensities.containsKey(id)?" · "+java.math.BigDecimal.valueOf(ws.project.bodyDensities.get(id)).stripTrailingZeros().toPlainString()+" kg/m³":""));
        ws.new PropertyBuilder().setTitle("Materiał · zakres").setItems(targets.toArray(new String[0]),(d,w)-> {
            if(w==0&&ids.size()<=1){showMaterialCatalog(-1);return;}
            showMaterialCatalog(w==0?-1:ids.get(w-1));
        }).setNegativeButton("Anuluj",null).show();
    }
    void showMaterialCatalog(int body) {
        String[] items=new String[MaterialDensity.NAMES.length+(body>0?2:1)];for(int i=0;i<MaterialDensity.NAMES.length;i++)items[i]=MaterialDensity.label(i);
        items[MaterialDensity.NAMES.length]="Własna gęstość…";if(body>0)items[items.length-1]="Jak dokument (usuń przypisanie bryły)";
        ws.new PropertyBuilder().setTitle(body>0?"Materiał · "+BodyGraph.name(ws.project,body):"Materiał dokumentu").setItems(items,(d,w)-> {
            if(w<MaterialDensity.NAMES.length)setDensity(body,MaterialDensity.VALUES[w]);
            else if(w==MaterialDensity.NAMES.length){if(body>0)showBodyDensityEditor(body);else ws.assemblyTools.showMaterialDensityEditor();}
            else setDensity(body,0);
        }).setNegativeButton("Anuluj",null).show();
    }
    void showBodyDensityEditor(int body) {
        EditText density=new EditText(ws.getContext());density.setSingleLine(true);density.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        density.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));density.setHint("Gęstość [kg/m³]");
        Double current=ws.project.bodyDensities.get(body);if(current!=null)density.setText(java.math.BigDecimal.valueOf(current).stripTrailingZeros().toPlainString());
        ws.new PropertyBuilder().setTitle("Gęstość · "+BodyGraph.name(ws.project,body)).setView(density).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(d,w)->{
            try{setDensity(body,MaterialDensity.parse(density.getText().toString()));}catch(IllegalArgumentException error){ws.operationMessage=error.getMessage();ws.invalidate();}
        }).show();
    }
    /** body ≤ 0: document density; value 0 removes the assignment. */
    void setDensity(int body,double value) {
        if(ws.projectRepository.readOnly()){ws.operationMessage=ws.projectRepository.loadNotice();ws.invalidate();return;}
        try {
            String baseline=ws.project.toJson().toString();CadProject candidate=CadProject.fromJson(new org.json.JSONObject(baseline));
            if(body>0){if(value>0)candidate.bodyDensities.put(body,value);else candidate.bodyDensities.remove(body);}else candidate.materialDensityKgM3=value;
            candidate.toJson();ws.projectRepository.save(candidate);
            ws.project=candidate;ws.undoStack.push(baseline);while(ws.undoStack.size()>50)ws.undoStack.removeLast();ws.redoStack.clear();
            ws.operationMessage="Zapisano materiał";showBodyProperties();
        }catch(Exception error){ws.operationMessage="Nie zapisano gęstości: "+error.getMessage();}
        ws.invalidate();
    }

}
