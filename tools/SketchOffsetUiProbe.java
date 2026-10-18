import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;
import pl.tabletcad.app.storage.*;

/** UI thread, isolated files: Geometria → Odsunięcie creates an exact, undoable offset copy. */
public final class SketchOffsetUiProbe {
    public static Context applicationContext;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    static Object field(Object o,String name)throws Exception {return InteractionFixProbe.field(o,name);}
    static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception {return InteractionFixProbe.call(o,name,types,args);}
    static void run(String[] args)throws Exception {
        File root=new File(args[0],"offset-"+UUID.randomUUID());check(root.mkdirs(),"isolated offset fixture");
        Context context=new ContextWrapper(applicationContext){@Override public File getFilesDir(){return root;}};
        CadProject fixture=CadProject.blank();fixture.sketch.add(new SketchPrimitive("plate","Prostokąt",0,0,100,60));new ProjectRepository(root).save(fixture);
        CadWorkspaceView view=new CadWorkspaceView(context,null);FrameLayout host=new FrameLayout(context);host.addView(view,new FrameLayout.LayoutParams(-1,-1));
        host.measure(View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1500,View.MeasureSpec.EXACTLY));host.layout(0,0,2400,1500);
        call(view,"enterSketchEditor",new Class<?>[0]);view.draw(new Canvas(Bitmap.createBitmap(2400,1500,Bitmap.Config.ARGB_8888)));
        check(ToolbarCatalog.allows(true,"Odsunięcie")&&!ToolbarCatalog.allows(false,"Odsunięcie"),"offset is a sketch-only command");
        call(view,"activateTopTool",new Class<?>[]{String.class},"Odsunięcie");
        check(String.valueOf(field(view,"operationMessage")).contains("Zaznacz"),"offset without selection asks for a selected element");
        InteractionFixProbe.set(view,"selectedSketchId","plate");
        call(view,"activateTopTool",new Class<?>[]{String.class},"Odsunięcie");
        View panel=(View)field(view,"propertyEditor");check(panel!=null,"offset opens contextual value panel");
        EditText input=InteractionFixProbe.input(panel);input.setText("2,5");
        Button apply=WorkspaceUiProbe.button(panel,"Zastosuj");check(apply!=null,"offset has apply action");apply.performClick();
        CadProject p=(CadProject)field(view,"project");check(p.sketch.size()==2,"offset adds an independent sketch element: "+field(view,"operationMessage"));
        SketchPrimitive copy=p.sketch.get(1);
        check(copy.type.equals("Prostokąt")&&copy.x1==-2.5&&copy.y1==-2.5&&copy.x2==102.5&&copy.y2==62.5,"decimal comma 2,5 mm offset is exact");
        check(field(view,"selectedSketchId").equals(copy.id),"new offset becomes the selection");
        call(view,"undo",new Class<?>[0]);check(((CadProject)field(view,"project")).sketch.size()==1,"undo removes the offset copy");
        // Geometria/Przytnij → Przedłuż: the selected line grows to the offset rectangle edge.
        ((CadProject)field(view,"project")).sketch.add(new SketchPrimitive("probe-line","Linia",10,30,20,30));
        InteractionFixProbe.set(view,"selectedSketchId","probe-line");
        call(view,"activateTopTool",new Class<?>[]{String.class},"Przedłuż");
        SketchPrimitive grown=null;for(SketchPrimitive e:((CadProject)field(view,"project")).sketch)if(e.id.equals("probe-line"))grown=e;
        check(grown!=null&&Math.abs(grown.x1-0)<1e-9&&Math.abs(grown.x2-20)<1e-9,"extend moves the nearer end to the rectangle edge: "+(grown==null?"none":grown.x1+","+grown.x2)+" "+field(view,"operationMessage"));
        // Geometria → Szyk kołowy: 6 holes on a pitch circle from the panel values.
        ((CadProject)field(view,"project")).sketch.add(new SketchPrimitive("probe-hole","Okrąg",70,30,73,30));
        InteractionFixProbe.set(view,"selectedSketchId","probe-hole");
        call(view,"activateTopTool",new Class<?>[]{String.class},"Szyk kołowy");
        View patternPanel=(View)field(view,"propertyEditor");List<EditText> fields=new ArrayList<>();WorkspaceUiProbe.editors(patternPanel,fields);
        check(fields.size()==4,"circular pattern panel has count, angle and centre");
        fields.get(0).setText("6");fields.get(1).setText("360");fields.get(2).setText("50");fields.get(3).setText("30");
        int before=((CadProject)field(view,"project")).sketch.size();WorkspaceUiProbe.button(patternPanel,"Zastosuj").performClick();
        List<SketchPrimitive> after=((CadProject)field(view,"project")).sketch;check(after.size()==before+5,"circular pattern adds 5 copies: "+field(view,"operationMessage"));
        for(int i=before;i<after.size();i++)check(Math.abs(Math.hypot(after.get(i).x1-50,after.get(i).y1-30)-20)<1e-9,"pattern copy "+(i-before+1)+" on Ø40 pitch circle");
        call(view,"undo",new Class<?>[0]);check(((CadProject)field(view,"project")).sketch.size()==before,"one undo removes the whole pattern");
        System.out.println("ALL SKETCH OFFSET UI TESTS PASSED");
    }
}
