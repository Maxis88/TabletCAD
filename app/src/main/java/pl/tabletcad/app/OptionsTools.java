package pl.tabletcad.app;

import android.widget.*;

/** Program options dialog (Opcje): display of threads, colours, grid, view cube, units of new projects. */
final class OptionsTools {
    private final CadWorkspaceView ws;
    OptionsTools(CadWorkspaceView view){ws=view;}
    private Spinner choice(LinearLayout form,String label,String[] items,int selected) {
        TextView title=new TextView(ws.getContext());title.setText(label);title.setTextColor(PanelStyle.INK);title.setPadding(0,(int)ws.dp(8),0,0);form.addView(title);
        Spinner spinner=new Spinner(ws.getContext());spinner.setAdapter(new ArrayAdapter<>(ws.getContext(),android.R.layout.simple_spinner_dropdown_item,items));spinner.setSelection(selected);form.addView(spinner);return spinner;
    }
    private CheckBox check(LinearLayout form,String label,boolean value) {
        CheckBox box=new CheckBox(ws.getContext());box.setText(label);box.setChecked(value);form.addView(box);return box;
    }
    void show() {
        ws.closePropertyEditor();ws.dismissToolPopup();
        LinearLayout form=new LinearLayout(ws.getContext());form.setOrientation(LinearLayout.VERTICAL);
        TextView info=new TextView(ws.getContext());info.setText("Ustawienia programu na tym tablecie. Nie zmieniają geometrii zapisanej w projektach.");info.setTextColor(PanelStyle.MUTED);form.addView(info);
        CheckBox threads=check(form,"Pokazuj gwinty modelowane",AppOptions.showThreads);
        TextView threadInfo=new TextView(ws.getContext());threadInfo.setText("Wyłączone: gwinty są pomijane w widoku i pomiarach (otwór gładki, szybsze przeliczanie). Eksport STEP/STL/OBJ/3MF zawsze zawiera gwint.");threadInfo.setTextColor(PanelStyle.MUTED);threadInfo.setTextSize(12);form.addView(threadInfo);
        Spinner background=choice(form,"Tło obszaru roboczego",AppOptions.BACKGROUNDS,AppOptions.background);
        Spinner accent=choice(form,"Kolor akcentu (przyciski, zaznaczenia)",AppOptions.ACCENTS,AppOptions.accent);
        Spinner model=choice(form,"Kolor bryły",AppOptions.MODEL_COLORS,AppOptions.modelColor);
        CheckBox grid=check(form,"Siatka w obszarze roboczym i szkicu",AppOptions.showGrid);
        CheckBox cube=check(form,"Kostka widoku",AppOptions.showViewCube);
        CheckBox dims=check(form,"Wymiary w szkicu widoczne",AppOptions.showSketchDimensions);
        int unitIndex=0;for(int i=0;i<Units.CODES.length;i++)if(Units.CODES[i].equals(AppOptions.defaultUnits))unitIndex=i;
        Spinner units=choice(form,"Jednostki nowych projektów",Units.NAMES,unitIndex);
        ws.new PropertyBuilder().setTitle("Opcje").setView(form).setNegativeButton("Anuluj",null).setNeutralButton("Domyślne",(d,w)->{
            boolean threadsChanged=!AppOptions.showThreads;
            AppOptions.showThreads=true;AppOptions.showGrid=true;AppOptions.showViewCube=true;AppOptions.showSketchDimensions=true;AppOptions.background=0;AppOptions.accent=0;AppOptions.modelColor=0;AppOptions.defaultUnits="mm";
            finish(threadsChanged);
        }).setPositiveButton("Zapisz",(d,w)->{
            boolean threadsChanged=threads.isChecked()!=AppOptions.showThreads;
            AppOptions.showThreads=threads.isChecked();AppOptions.showGrid=grid.isChecked();AppOptions.showViewCube=cube.isChecked();AppOptions.showSketchDimensions=dims.isChecked();
            AppOptions.background=background.getSelectedItemPosition();AppOptions.accent=accent.getSelectedItemPosition();AppOptions.modelColor=model.getSelectedItemPosition();
            AppOptions.defaultUnits=Units.CODES[units.getSelectedItemPosition()];
            finish(threadsChanged);
        }).show();
    }
    private void finish(boolean threadsChanged) {
        AppOptions.save(ws.getContext());ws.applyAppOptions();
        if(threadsChanged)ws.rebuildForOptions(AppOptions.showThreads?"Gwinty widoczne – przeliczanie modelu…":"Gwinty ukryte – przeliczanie modelu…");
        else{ws.operationMessage="Zapisano opcje";ws.invalidate();}
    }
}
