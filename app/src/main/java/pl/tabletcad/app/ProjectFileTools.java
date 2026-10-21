package pl.tabletcad.app;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import pl.tabletcad.app.model.*;

/** Project files and settings: rename, project browser, copies, trash, parameters, units, drawing metadata */
final class ProjectFileTools {
    private final CadWorkspaceView ws;
    ProjectFileTools(CadWorkspaceView view){ws=view;}
    String currentSketchName() {return ws.project.sketchName;}

    void showRenameSketch() {
        EditText name=new EditText(ws.getContext());name.setSingleLine(true);name.setText(currentSketchName());
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(48)});
        new android.app.AlertDialog.Builder(ws.getContext()).setTitle("Nazwa szkicu").setView(name)
            .setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(d,w)->{
                String value=name.getText().toString().trim();if(value.isEmpty())return;
                try {ws.checkpoint();ws.project.sketchName=value;ws.project.archiveCurrentSketch();
                    for(org.json.JSONObject snapshot:ws.project.savedSketches)if(ws.project.sketchId.equals(snapshot.optString("id")))snapshot.put("name",value);
                    ws.projectRepository.save(ws.project);ws.invalidate();
                }catch(org.json.JSONException|RuntimeException error){ws.operationMessage="Nie można zapisać nazwy szkicu";}
            }).show();
    }


    public void showStartupProjects() {
        if(ws.projectChooser!=null&&ws.projectChooser.isShowing())return;
        openProjectBrowser(true,null);
    }

    void openProjectBrowser(boolean startup,CadProject snapshot) {
        ws.closePropertyEditor();
        if(ws.projectChooser!=null){ws.projectChooser.dismiss();ws.projectChooser=null;}
        ProjectLibraryBrowser browser=new ProjectLibraryBrowser(ws.getContext(),ws.projectRepository,ws.project.id,new ProjectLibraryBrowser.Actions(){
            public void open(CadProject next){ws.warmThen(next,"Otwieranie projektu „"+next.name+"”…",()->{if(!activateProject(next))ws.post(ProjectFileTools.this::showStartupProjects);});}
            public void create(){showNewProject();}
            public void trash(){showProjectTrash();}
            public void delete(CadProject target){confirmDeleteProject(target);}
        });
        ws.projectChooser=browser.show(startup,snapshot);
    }

    void confirmDeleteProject(CadProject target) {
        if(target.id.equals(ws.project.id)){android.widget.Toast.makeText(ws.getContext(),"Nie można usunąć otwartego projektu — najpierw otwórz inny",android.widget.Toast.LENGTH_LONG).show();return;}
        new android.app.AlertDialog.Builder(ws.getContext()).setTitle("Usunąć projekt „"+target.name+"”?")
            .setMessage("Projekt zniknie z listy. Pliki zostaną przeniesione do kosza aplikacji, skąd można je przywrócić.")
            .setNegativeButton("Anuluj",null).setPositiveButton("Usuń",(d,w)->{
                if(ws.projectChooser!=null){ws.projectChooser.dismiss();ws.projectChooser=null;}
                final String activeId=ws.project.id;
                runProjectLibraryTask("Przenoszenie projektu do kosza",()->{ws.projectRepository.deleteProject(target.id,activeId);return target;},"Przeniesiono do kosza: ");
            }).show();
    }

    /** File menu (top bar): open/import, export, projects, copies and the trash. */
    void showFileMenu() {
        ws.closePropertyEditor();ws.dismissToolPopup();
        ws.new PropertyBuilder().setTitle("Plik")
            .setItems(new String[]{"Importuj plik (STEP, IGES, STL, OBJ, 3MF, DXF)…","Eksportuj…","Nowy pusty projekt","Moje projekty","Utwórz kopię projektu","Kosz — przywróć projekt","Opcje…"},(d,which)-> {
                if(which==0){if(ws.fileRequestListener!=null)ws.fileRequestListener.requestOpenStep();}
                else if(which==1)ws.showExportPicker();else if(which==2)showNewProject();else if(which==3)showProjectLibrary();else if(which==4)showProjectCopy();else if(which==5)showProjectTrash();else ws.showOptions();
            }).setNegativeButton("Zamknij",null).show();
    }
    void showProjectMenu() {
        ws.closePropertyEditor();
        ws.new PropertyBuilder().setTitle("Projekt · "+ws.project.name+" · TabletCAD v"+ws.appVersion())
            .setItems(new String[]{"Nowy pusty projekt","Moje projekty","Utwórz kopię projektu","Kosz — przywróć projekt","Dane i nazwa dokumentu","Jednostki i dokładność","Parametry","Opcje programu…"},(d,which)-> {
                if(which==0)showNewProject();else if(which==1)showProjectLibrary();else if(which==2)showProjectCopy();else if(which==3)showProjectTrash();else if(which==4)showDrawingMetadataEditor();else if(which==5)showUnitSettings();else if(which==6)showParameters();else ws.showOptions();
            }).setNegativeButton("Zamknij",null).show();
    }

    /** User parameters: list with current values, add/edit/delete; ws.bound values are recomputed on change. */
    void showParameters() {
        if(ws.projectRepository.readOnly()){ws.operationMessage=ws.projectRepository.loadNotice();ws.invalidate();return;}
        if(SketchTransactions.active(ws.project)){ws.operationMessage="Najpierw zakończ lub anuluj aktywną edycję szkicu";ws.invalidate();return;}
        java.util.Map<String,double[]> values;
        try{values=UserParameters.evaluate(ws.project);}catch(IllegalArgumentException error){values=new java.util.HashMap<>();ws.operationMessage=error.getMessage();}
        List<String> rows=new ArrayList<>();
        for(UserParameters.Parameter x:ws.project.parameters){double[] v=values.get(x.name);rows.add(x.name+" = "+x.expression+(v==null?"":"  →  "+(x.length?ws.formatMm(v[0]):String.format(java.util.Locale.US,"%.6g",v[0]))));}
        rows.add("+ Dodaj parametr");
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(ws.getContext());
        info.setText("Wpisz nazwę parametru w polu głębokości lub wymiaru (np. grubosc/2), aby nim sterować. Powiązań: "+ws.project.parameterBindings.size()+".");form.addView(info);
        ws.new PropertyBuilder().setTitle("Parametry").setView(form).setItems(rows.toArray(new String[0]),(d,which)->showParameterEditor(which<ws.project.parameters.size()?which:-1)).setNegativeButton("Zamknij",null).show();
    }
    void showParameterEditor(int index) {
        UserParameters.Parameter original=index>=0?ws.project.parameters.get(index):null;
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView l1=new android.widget.TextView(ws.getContext());l1.setText("Nazwa");form.addView(l1);
        EditText name=new EditText(ws.getContext());name.setSingleLine(true);name.setText(original!=null?original.name:"p"+(ws.project.parameters.size()+1));form.addView(name);
        android.widget.TextView l2=new android.widget.TextView(ws.getContext());l2.setText("Wyrażenie (liczby, jednostki, + − * / ( ), inne parametry)");form.addView(l2);
        EditText expression=new EditText(ws.getContext());expression.setSingleLine(true);expression.setText(original!=null?original.expression:"10");form.addView(expression);
        android.widget.CheckBox length=new android.widget.CheckBox(ws.getContext());length.setText("Długość (w jednostce projektu: "+ws.project.units+")");length.setChecked(original==null||original.length);form.addView(length);
        CadWorkspaceView.PropertyBuilder builder=ws.new PropertyBuilder().setTitle(original!=null?"Parametr "+original.name:"Nowy parametr").setView(form).setNegativeButton("Anuluj",null);
        if(original!=null)builder.setNeutralButton("Usuń",(d,w)->changeParameters(next->{
            for(String expr:next.parameterBindings.values())if(UserParameters.names(expr).contains(original.name))throw new IllegalArgumentException("Parametr jest używany w powiązaniach – najpierw je zmień");
            next.parameters.remove(index);
        },"Usunięto parametr "+original.name));
        builder.setPositiveButton("Zapisz",(d,w)->changeParameters(next->{
            String n=name.getText().toString().trim();UserParameters.validateName(n);
            UserParameters.Parameter value=new UserParameters.Parameter(n,expression.getText().toString().trim(),length.isChecked());
            if(index>=0){
                String old=next.parameters.get(index).name;next.parameters.set(index,value);
                if(!old.equals(n))for(java.util.Map.Entry<String,String> e:next.parameterBindings.entrySet())if(UserParameters.names(e.getValue()).contains(old))throw new IllegalArgumentException("Zmiana nazwy używanego parametru – najpierw zmień powiązania");
            }else next.parameters.add(value);
        },"Zapisano parametr")).show();
    }
    void changeParameters(CadWorkspaceView.DocumentChange change,String success) {
        try {
            String baseline=ws.project.toJson().toString();CadProject next=CadProject.fromJson(new org.json.JSONObject(baseline));
            change.apply(next);UserParameters.evaluate(next);UserParameters.apply(next);
            ws.commitProjectAsync(next,baseline,success);
        }catch(org.json.JSONException|IllegalArgumentException error){ws.operationMessage="Parametry: "+error.getMessage();ws.invalidate();}
    }

    /** Project units and display precision; the B-Rep and all stored values stay in millimetres. */
    void showUnitSettings() {
        if(ws.projectRepository.readOnly()){ws.operationMessage=ws.projectRepository.loadNotice();ws.invalidate();return;}
        if(SketchTransactions.active(ws.project)){ws.operationMessage="Najpierw zakończ lub anuluj aktywną edycję szkicu";ws.invalidate();return;}
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView info=new android.widget.TextView(ws.getContext());
        info.setText("Geometria jest zapisywana dokładnie w milimetrach. Jednostka zmienia wyświetlanie i interpretację wpisywanych liczb bez jednostki. W polach można wpisywać wyrażenia, np. 100/3, 2*(10+2,5), 1in+2mm.");form.addView(info);
        android.widget.Spinner unit=new android.widget.Spinner(ws.getContext());unit.setAdapter(new android.widget.ArrayAdapter<>(ws.getContext(),android.R.layout.simple_spinner_dropdown_item,Units.NAMES));
        for(int i=0;i<Units.CODES.length;i++)if(Units.CODES[i].equals(ws.project.units))unit.setSelection(i);form.addView(unit);
        android.widget.TextView decimalsLabel=new android.widget.TextView(ws.getContext());decimalsLabel.setText("Miejsca po przecinku na rysunku (0–6)");form.addView(decimalsLabel);
        android.widget.Spinner decimals=new android.widget.Spinner(ws.getContext());decimals.setAdapter(new android.widget.ArrayAdapter<>(ws.getContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"0","1","2","3","4","5","6"}));
        decimals.setSelection(ws.project.displayDecimals);form.addView(decimals);
        ws.new PropertyBuilder().setTitle("Jednostki i dokładność").setView(form).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",(d,w)->{
            try {
                String code=Units.CODES[unit.getSelectedItemPosition()];int places=decimals.getSelectedItemPosition();
                if(code.equals(ws.project.units)&&places==ws.project.displayDecimals)return;
                String baseline=ws.project.toJson().toString();CadProject candidate=CadProject.fromJson(new org.json.JSONObject(baseline));
                candidate.units=code;candidate.displayDecimals=places;ws.projectRepository.save(candidate);
                ws.project=candidate;ws.undoStack.push(baseline);while(ws.undoStack.size()>50)ws.undoStack.removeLast();ws.redoStack.clear();
                ws.operationMessage="Jednostki projektu: "+code+", dokładność "+places+" miejsc. Geometria bez zmian.";
            }catch(Exception error){ws.operationMessage="Nie zapisano ustawień jednostek: "+error.getMessage();}
            ws.invalidate();
        }).show();
    }

    void showProjectCopy() {
        if(ws.projectRepository.readOnly()){ws.operationMessage=ws.projectRepository.loadNotice();ws.invalidate();return;}
        if(SketchTransactions.active(ws.project)){ws.operationMessage="Najpierw zakończ lub anuluj aktywną edycję szkicu";ws.invalidate();return;}
        EditText name=new EditText(ws.getContext());name.setSingleLine(true);name.setText(ws.project.name+" — kopia");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        new android.app.AlertDialog.Builder(ws.getContext()).setTitle("Kopia projektu").setView(name).setNegativeButton("Anuluj",null).setPositiveButton("Utwórz kopię",(d,w)->{
            try{CadProject snapshot=CadProject.fromJson(ws.project.toJson());String title=name.getText().toString();runProjectLibraryTask("Tworzenie kopii projektu",()->{ws.projectRepository.save(snapshot);return ws.projectRepository.duplicateProject(snapshot.id,title);},"Utworzono kopię: ");}
            catch(org.json.JSONException|RuntimeException error){ws.operationMessage="Nie utworzono kopii: "+error.getMessage();ws.invalidate();}
        }).show();
    }

    void showProjectTrash() {
        ws.closePropertyEditor();if(ws.projectChooser!=null){ws.projectChooser.dismiss();ws.projectChooser=null;}
        LinearLayout body=new LinearLayout(ws.getContext());body.setOrientation(LinearLayout.VERTICAL);body.setPadding((int)ws.dp(16),0,(int)ws.dp(16),0);
        android.widget.TextView status=new android.widget.TextView(ws.getContext());body.addView(status);
        android.widget.ProgressBar progress=new android.widget.ProgressBar(ws.getContext());body.addView(progress);
        android.widget.Button retry=new android.widget.Button(ws.getContext());retry.setText("Odśwież kosz");body.addView(retry);
        android.widget.ListView list=new android.widget.ListView(ws.getContext());body.addView(list,new LinearLayout.LayoutParams(-1,(int)ws.dp(240)));
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(ws.getContext()).setTitle("Kosz")
            .setView(body).setNegativeButton("Wróć do projektów",(d,w)->{d.dismiss();showStartupProjects();})
            .setOnCancelListener(d->ws.post(this::showStartupProjects)).create();
        android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
        LatestTaskRunner reader=new LatestTaskRunner(command->ui.post(command));
        dialog.setOnDismissListener(d->{reader.close();if(ws.projectChooser==dialog)ws.projectChooser=null;});
        Runnable refresh=()->{
            retry.setEnabled(false);list.setEnabled(false);progress.setVisibility(View.VISIBLE);status.setText("Odczytywanie kosza…");
            reader.submit(()->{
                List<pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry> entries=ws.projectRepository.listDeletedProjects();
                java.text.DateFormat date=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT);
                String[] names=new String[entries.size()];
                for(int i=0;i<names.length;i++){pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry e=entries.get(i);names[i]=e.name+"\n"+date.format(new java.util.Date(e.deletedAt))+(e.backup?" · kopia odzyskiwania":"")+(ws.projectRepository.hasProjectFiles(e.id)?" · już w bibliotece":"");}
                return new TrashListing(entries,names);
            },result->{
                if(!dialog.isShowing())return;
                progress.setVisibility(View.GONE);retry.setEnabled(true);list.setEnabled(true);
                status.setText(result.entries.isEmpty()?"Brak projektów możliwych do przywrócenia. Nieczytelne zapisy pozostają zachowane w koszu.":"Projektów do przywrócenia: "+result.entries.size());
                list.setAdapter(new android.widget.ArrayAdapter<>(ws.getContext(),android.R.layout.simple_list_item_1,result.labels));
                list.setOnItemClickListener((parent,view,index,id)->{dialog.dismiss();confirmRestoreProject(result.entries.get(index));});
            },error->{if(!dialog.isShowing())return;progress.setVisibility(View.GONE);retry.setEnabled(true);status.setText("Nie można odczytać kosza: "+error.getMessage()+". Użyj Odśwież kosz, aby ponowić.");});
        };
        retry.setOnClickListener(v->refresh.run());ws.projectChooser=dialog;dialog.show();refresh.run();
    }
    static final class TrashListing {
        final List<pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry> entries;final String[] labels;
        TrashListing(List<pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry> entries,String[] labels){this.entries=entries;this.labels=labels;}
    }
    void confirmRestoreProject(pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry entry) {
        boolean conflict=ws.projectRepository.hasProjectFiles(entry.id);
        android.app.AlertDialog.Builder dialog=new android.app.AlertDialog.Builder(ws.getContext()).setTitle(entry.name)
            .setMessage(conflict?"Ten projekt jest już w bibliotece. Możesz odzyskać zapis z kosza jako osobną kopię.":"Przywrócić projekt do biblioteki?")
            .setNegativeButton("Wróć",(d,w)->showProjectTrash()).setOnCancelListener(d->showProjectTrash());
        if(!conflict)dialog.setPositiveButton("Przywróć",(d,w)->restoreProjectEntry(entry,false,null));
        dialog.setNeutralButton("Przywróć jako kopię",(d,w)->{
            EditText name=new EditText(ws.getContext());name.setSingleLine(true);name.setText(entry.name+" — odzyskany");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
            new android.app.AlertDialog.Builder(ws.getContext()).setTitle("Nazwa odzyskanego projektu").setView(name).setNegativeButton("Wróć",(a,b)->showProjectTrash()).setOnCancelListener(a->showProjectTrash())
                .setPositiveButton("Przywróć kopię",(a,b)->restoreProjectEntry(entry,true,name.getText().toString())).show();
        }).show();
    }
    void restoreProjectEntry(pl.tabletcad.app.storage.ProjectLibraryFiles.TrashEntry entry,boolean asCopy,String name) {
        runProjectLibraryTask("Przywracanie projektu",()->ws.projectRepository.restoreProject(entry.token,asCopy,name),"Przywrócono projekt: ");
    }
    void runProjectLibraryTask(String title,java.util.concurrent.Callable<CadProject> action,String success) {
        if(ws.libraryFileOperationPending)return;ws.libraryFileOperationPending=true;
        android.app.AlertDialog progress=new android.app.AlertDialog.Builder(ws.getContext()).setTitle(title).setMessage("Proszę czekać…").setView(new android.widget.ProgressBar(ws.getContext())).setCancelable(false).show();ws.libraryProgress=progress;
        android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(()->{
            String result;try{result=success+action.call().name;}catch(Exception error){result="Operacja nie powiodła się: "+error.getMessage();}final String message=result;
            ui.post(()->{ws.libraryFileOperationPending=false;if(ws.libraryProgress==progress){progress.dismiss();ws.libraryProgress=null;}if(!ws.isAttachedToWindow())return;
                ws.operationMessage=message;android.widget.Toast.makeText(ws.getContext(),message,android.widget.Toast.LENGTH_LONG).show();showStartupProjects();ws.invalidate();});
        },"TabletCAD-library-files").start();
    }

    void showNewProject() {
        createNewProject("Nowy projekt");
    }

    void createNewProject(String title) {
        try {
            ws.projectRepository.save(ws.project);
            CadProject next=CadProject.blank();next.name=title;next.units=AppOptions.defaultUnits;if(!activateProject(next))ws.post(this::showStartupProjects);
        }catch(RuntimeException error){ws.operationMessage="Nie można utworzyć projektu; poprzedni dokument zachowano";ws.post(this::showStartupProjects);ws.invalidate();}
    }

    void showProjectLibrary() {
        try {openProjectBrowser(false,CadProject.fromJson(ws.project.toJson()));}
        catch(org.json.JSONException|RuntimeException error){ws.operationMessage="Nie można przygotować biblioteki; bieżący dokument zachowano";ws.invalidate();}
    }

    boolean activateProject(CadProject next) {
        CadProject previous=ws.project;String previousMode=ws.mode;
        double[] previousMesh=ws.nativeMesh,previousPrism=ws.nativePrism;
        java.util.Map<Integer,Integer> previousFaceBodies=ws.faceBodies,previousFaceFeatures=ws.faceFeatures;
        List<HoleThread.Cylinder> previousHoles=ws.holeCylinders;
        String previousInfo=ws.modelInfo;TechnicalSheet previousSheet=ws.technicalSheet;
        double w=ws.modelWidth,h=ws.modelHeight,d=ws.modelDepth;
        double[] previousCamera={ws.meshCenterX,ws.meshCenterY,ws.meshCenterZ,ws.cameraDistance};boolean previousAnchor=ws.cameraAnchorSet;
        int previousSketchBody=ws.editingSketchBody;double[] previousPlacement=ws.editingSketchPlacement;
        try {
            ws.cameraAnchorSet=false;ws.editingSketchBody=0;ws.editingSketchPlacement=SketchPlacement.identity();
            ws.project=next;ws.mode="Wybierz";
            ws.modelWidth=next.width;ws.modelHeight=next.height;ws.modelDepth=next.depth;
            if(!ws.updateNativeModel())throw new IllegalStateException("Nie można odtworzyć geometrii lub źródła projektu");
            ws.projectRepository.save(next);
        }catch(RuntimeException error) {
            ws.meshCenterX=previousCamera[0];ws.meshCenterY=previousCamera[1];ws.meshCenterZ=previousCamera[2];ws.cameraDistance=previousCamera[3];ws.cameraAnchorSet=previousAnchor;
            ws.editingSketchBody=previousSketchBody;ws.editingSketchPlacement=previousPlacement;
            ws.project=previous;ws.mode=previousMode;ws.modelWidth=w;ws.modelHeight=h;ws.modelDepth=d;
            ws.nativeMesh=previousMesh;ws.nativePrism=previousPrism;ws.faceBodies=previousFaceBodies;ws.faceFeatures=previousFaceFeatures;ws.holeCylinders=previousHoles;ws.modelInfo=previousInfo;ws.technicalSheet=previousSheet;ws.updateMeshCenter();
            ws.operationMessage="Nie można otworzyć projektu. Bieżący dokument pozostaje aktywny.";ws.invalidate();return false;
        }
        ws.closePropertyEditor();ws.undoStack.clear();ws.redoStack.clear();ws.highlightSketch=false;ws.selectedTimelineIndex=-1;ws.sketchVisible=true;
        ws.sketchPlane=next.sketchPlane;ws.sketchPlaneChosen=false;
        ws.sketching=false;ws.sketchRect=null;ws.draggedSketch=ws.dragPreview=null;ws.dragSolvedPreview=null;
        ws.selectedFace=-1;ws.selectedSketchId=null;ws.measureA=ws.measureB=null;ws.measuredEdgeText="";
        ws.coincidenceMode=ws.trimMode=ws.dimensionMode=false;ws.coincidenceFirst=null;ws.snapMarker=null;
        ws.polylineTouch=false;ws.polylinePreview=null;
        ws.cameraYaw=-28;ws.cameraPitch=22;ws.zoom=1;ws.panX=ws.panY=0;ws.showSolid=true;ws.treeSelection="Model";
        ws.operationMessage="Otwarto projekt: "+next.name;
        ws.arcDraft.clear();ws.threePointPreview=null;ws.resumeSketchWork();
        ws.invalidate();return true;
    }

    void showDrawingMetadataEditor() {
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding((int)ws.dp(16),0,(int)ws.dp(16),0);
        String[] labels={"Nazwa dokumentu", "Numer rysunku", "Autor", "Materiał", "Rewizja"};
        String[] values={ws.project.name,ws.project.drawingNumber,ws.project.drawingAuthor,ws.project.drawingMaterial,ws.project.drawingRevision};
        int[] limits={48,32,32,32,8};EditText[] inputs=new EditText[5];
        for(int i=0;i<inputs.length;i++) {
            android.widget.TextView label=new android.widget.TextView(ws.getContext());
            label.setText(labels[i]);form.addView(label);
            EditText input=new EditText(ws.getContext());input.setSingleLine(true);
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limits[i])});
            input.setText(values[i]);input.setSelectAllOnFocus(true);form.addView(input);inputs[i]=input;
        }
        android.widget.ScrollView scroll=new android.widget.ScrollView(ws.getContext());scroll.addView(form);
        ws.new PropertyBuilder().setTitle("Dane tabliczki rysunkowej")
            .setView(scroll).setNegativeButton("Anuluj",null)
            .setPositiveButton("Zapisz",(dialog,which)-> {
                String name=inputs[0].getText().toString().trim();
                if(name.isEmpty()) {ws.operationMessage="Nazwa dokumentu nie może być pusta";ws.invalidate();return;}
                ws.checkpoint();ws.project.name=name;
                ws.project.drawingNumber=inputs[1].getText().toString().trim();
                ws.project.drawingAuthor=inputs[2].getText().toString().trim();
                ws.project.drawingMaterial=inputs[3].getText().toString().trim();
                ws.project.drawingRevision=inputs[4].getText().toString().trim();
                ws.projectRepository.save(ws.project);ws.operationMessage="Gotowe: zapisano dane tabliczki";ws.invalidate();
            }).show();
    }

}
