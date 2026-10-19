package pl.tabletcad.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;
import pl.tabletcad.app.model.CadProject;
import pl.tabletcad.app.storage.*;

/** Native library browser. Document reads and bounded thumbnail decoding never run in getView. */
public final class ProjectLibraryBrowser {
    public interface Actions {
        void open(CadProject project);
        void create();
        void trash();
        void delete(CadProject project);
    }
    private final Context context;
    private final ProjectRepository repository;
    private final String activeId;
    private final Actions actions;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final LatestTaskRunner reader=new LatestTaskRunner(command->ui.post(command));
    private final ThreadPoolExecutor thumbnails=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(24),r->new Thread(r,"TabletCAD-library-images"));
    private final android.util.LruCache<String,Bitmap> images=new android.util.LruCache<>(24);
    private final Set<String> requested=new HashSet<>(),missing=new HashSet<>();
    private final List<ProjectLibraryItem> all=new ArrayList<>(),visible=new ArrayList<>();
    private final DateFormat date=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT);
    private final EditText search;
    private final Spinner sorting,kind;
    private final TextView status;
    private final ProgressBar progress;
    private final ListView list;
    private final Button refresh;
    private final BaseAdapter adapter;
    private AlertDialog dialog;
    private CadProject pendingSave;
    private boolean closed,loading,startupChooser;
    private String readFailure;

    public ProjectLibraryBrowser(Context context,ProjectRepository repository,String activeId,Actions actions) {
        this.context=context;this.repository=repository;this.activeId=activeId;this.actions=actions;
        search=new EditText(context);search.setSingleLine(true);search.setHint("Szukaj: nazwa, numer, autor, materiał");
        search.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)});
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        sorting=spinner(new String[]{"Ostatnio zmienione","Najstarsze zmiany","Nazwa A–Z","Nazwa Z–A"});
        kind=spinner(new String[]{"Wszystkie projekty","Bryły natywne","Import STEP","Siatki STL","Szkice bez bryły","Puste projekty"});
        status=new TextView(context);status.setPadding(dp(4),dp(6),dp(4),dp(6));
        progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);
        list=new ListView(context);list.setDividerHeight(dp(1));
        refresh=new Button(context);refresh.setText("Odśwież");refresh.setOnClickListener(v->reload());PanelStyle.secondary(refresh);
        adapter=new BaseAdapter(){
            public int getCount(){return visible.size();}
            public Object getItem(int position){return visible.get(position);}
            public long getItemId(int position){return position;}
            public View getView(int position,View reuse,ViewGroup parent){return card(visible.get(position));}
        };
        list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filter();}
            public void afterTextChanged(Editable text){}
        });
        AdapterView.OnItemSelectedListener selection=new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){filter();}
            public void onNothingSelected(AdapterView<?> parent){}
        };
        sorting.setOnItemSelectedListener(selection);kind.setOnItemSelectedListener(selection);
        list.setOnItemClickListener((parent,view,position,id)->{
            if(loading||closed)return;
            String selected=visible.get(position).project.id;busy("Odczytywanie projektu…");
            reader.submit(()->repository.loadProject(selected),loaded->{if(!active())return;dialog.dismiss();actions.open(loaded);},this::failed);
        });
    }
    public AlertDialog show(boolean startup,CadProject saveBeforeListing) {
        startupChooser=startup;
        pendingSave=saveBeforeListing;
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),0,dp(12),0);
        body.addView(search);
        LinearLayout options=new LinearLayout(context);options.addView(sorting,new LinearLayout.LayoutParams(0,-2,1));options.addView(kind,new LinearLayout.LayoutParams(0,-2,1));options.addView(refresh);
        PanelStyle.form(search);PanelStyle.form(status);options.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(options);body.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));body.addView(status);
        int height=Math.max(dp(120),Math.min(dp(360),(int)(context.getResources().getDisplayMetrics().heightPixels*.4)));
        body.addView(list,new LinearLayout.LayoutParams(-1,height));
        AlertDialog.Builder builder=new AlertDialog.Builder(context).setTitle("Projekty · TabletCAD")
            .setView(body).setPositiveButton("Nowy projekt",(d,w)->actions.create()).setNeutralButton("Kosz",(d,w)->actions.trash()).setCancelable(!startup);
        if(!startup)builder.setNegativeButton("Zamknij",null);
        dialog=builder.create();dialog.setOnDismissListener(d->{closed=true;reader.close();thumbnails.shutdownNow();images.evictAll();});
        dialog.show();
        Button create=dialog.getButton(AlertDialog.BUTTON_POSITIVE);if(create!=null){create.setTextColor(PanelStyle.ACCENT);create.setTypeface(Typeface.DEFAULT_BOLD);create.setAllCaps(false);}
        for(int id:new int[]{AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE}){Button b=dialog.getButton(id);if(b!=null){b.setAllCaps(false);b.setTextColor(PanelStyle.MUTED);}}
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(Math.min(dp(820),context.getResources().getDisplayMetrics().widthPixels-dp(32)),ViewGroup.LayoutParams.WRAP_CONTENT);
        reload();return dialog;
    }
    private int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private boolean active(){return !closed&&dialog!=null&&dialog.isShowing();}
    private Spinner spinner(String[] labels){Spinner spinner=new Spinner(context);ArrayAdapter<String> items=new ArrayAdapter<>(context,android.R.layout.simple_spinner_item,labels);items.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(items);return spinner;}
    private void busy(String message){loading=true;readFailure=null;list.setEnabled(false);refresh.setEnabled(false);progress.setVisibility(View.VISIBLE);status.setText(message);navigation(false);}
    private void idle(){loading=false;list.setEnabled(true);refresh.setEnabled(true);progress.setVisibility(View.GONE);navigation(true);}
    private void navigation(boolean enabled) {
        if(dialog==null)return;
        // An initial autosave must finish before a new document can be created or opened.
        dialog.setCancelable(enabled&&!startupChooser);
        for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}) {
            Button button=dialog.getButton(id);if(button!=null)button.setEnabled(enabled);
        }
    }
    private void reload() {
        if(closed||loading)return;busy("Odczytywanie biblioteki…");
        final CadProject snapshot=pendingSave;
        reader.submit(()->{if(snapshot!=null&&!repository.readOnly())repository.save(snapshot);return repository.listProjectItems();},items->{
            if(!active())return;
            pendingSave=null;all.clear();all.addAll(items);missing.clear();images.evictAll();idle();filter();
        },this::failed);
    }
    private void failed(Exception error){if(!active())return;idle();readFailure="Nie można odczytać projektów: "+error.getMessage()+". Użyj Odśwież, aby ponowić.";status.setText(readFailure);}
    private void filter() {
        if(closed)return;
        visible.clear();visible.addAll(ProjectLibraryItem.select(all,search.getText().toString(),
            ProjectLibraryItem.Kind.values()[Math.max(0,kind.getSelectedItemPosition())],
            ProjectLibraryItem.Sort.values()[Math.max(0,sorting.getSelectedItemPosition())]));
        adapter.notifyDataSetChanged();
        if(!loading)status.setText(readFailure!=null?readFailure:visible.size()+" z "+all.size()+" projektów"+(visible.isEmpty()?(all.isEmpty()?" · biblioteka jest pusta":" · brak wyników; zmień wyszukiwanie lub filtr"):""));
    }
    private View card(ProjectLibraryItem item) {
        CadProject p=item.project;
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(8),dp(8),dp(8));
        ImageView image=new ImageView(context);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageDrawable(null);image.setBackground(PanelStyle.shape(context,Color.rgb(236,241,244),0,8));image.setClipToOutline(true);
        row.addView(image,new LinearLayout.LayoutParams(dp(100),dp(68)));thumbnail(p.id,image);
        LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(12),0,dp(8),0);
        TextView name=new TextView(context);name.setText(p.name);name.setTextSize(16);name.setTypeface(Typeface.DEFAULT_BOLD);name.setTextColor(PanelStyle.INK);labels.addView(name);
        TextView details=new TextView(context);details.setTextSize(12.5f);details.setTextColor(PanelStyle.MUTED);
        String[] kinds={"", "bryła B-Rep", "import STEP", "siatka STL", "szkice bez bryły", "pusty projekt"};
        details.setText(kinds[item.kind().ordinal()]+(item.modifiedAt>0?" · "+date.format(new Date(item.modifiedAt)):"")+
            (item.recovered?"\nKopia odzyskiwania .bak":"")+(p.id.equals(activeId)?" · otwarty":""));labels.addView(details);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        Button delete=new Button(context);delete.setText("Usuń");PanelStyle.danger(delete);delete.setEnabled(!p.id.equals(activeId)&&!repository.readOnly());delete.setAlpha(delete.isEnabled()?1f:.35f);delete.setFocusable(false);
        delete.setContentDescription("Usuń projekt "+p.name);delete.setOnClickListener(v->{if(!loading&&!closed)actions.delete(p);});row.addView(delete);
        row.setContentDescription("Otwórz projekt "+p.name);return row;
    }
    private void thumbnail(String id,ImageView target) {
        Bitmap cached=images.get(id);if(cached!=null){target.setImageBitmap(cached);return;}
        if(missing.contains(id)||requested.contains(id)||closed)return;
        requested.add(id);
        try {thumbnails.execute(()->{
            Bitmap decoded=null;
            try {
                File file=repository.thumbnailFile(id);
                if(file.isFile()&&file.length()<=4*1024*1024) {
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),options);
                    if(options.outWidth>0&&options.outHeight>0&&options.outWidth<=8192&&options.outHeight<=8192) {
                        options.inSampleSize=1;while(options.outWidth/options.inSampleSize>320||options.outHeight/options.inSampleSize>240)options.inSampleSize*=2;
                        options.inJustDecodeBounds=false;decoded=BitmapFactory.decodeFile(file.getPath(),options);
                    }
                }
            }catch(RuntimeException ignored){}
            Bitmap result=decoded;
            ui.post(()->{requested.remove(id);if(!active()){if(result!=null)result.recycle();return;}if(result==null)missing.add(id);else images.put(id,result);adapter.notifyDataSetChanged();});
        });}catch(RejectedExecutionException error){requested.remove(id);}
    }
}
