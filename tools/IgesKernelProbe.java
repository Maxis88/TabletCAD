import java.io.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** 0.99 IGES exchange: STEP → IGES → STEP keeps the exact solid; malformed files are refused. */
public final class IgesKernelProbe {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static void close(String what,double actual,double expected,double tolerance){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(what+": "+actual+" != "+expected);checks++;System.out.println("PASS "+what+" = "+actual);}
    public static void main(String[] args)throws Exception {
        File dir=new File(args[0]).getParentFile();dir.mkdirs();
        check(NativeCadEngine.igesAvailable(),"kernel built with IGES");
        CadProject p=HistoryKernelProbe.plate();
        HistoryKernelProbe.add(p,ExtrusionPlan.feature(HistoryKernelProbe.top(),new SketchPrimitive("h","Okrąg",50,30,55,30),"cut",20,0,false,false),"h",1);
        File step=new File(dir,"model.step"),iges=new File(dir,"model.igs"),back=new File(dir,"back.step");
        double[] f=BodyGraph.pack(p,HistoryKernelProbe.raw(p));
        check(NativeCadEngine.evaluateFeatures(100,60,20,0,p.featureBase,f,step.getAbsolutePath()).length>0&&step.length()>0,"plate with a hole written as STEP");
        String toIges=NativeCadEngine.convertExchange(step.getAbsolutePath(),iges.getAbsolutePath(),1);
        check(toIges==null&&iges.length()>0,"STEP → IGES ("+toIges+", "+iges.length()+" B)");
        String toStep=NativeCadEngine.convertExchange(iges.getAbsolutePath(),back.getAbsolutePath(),0);
        check(toStep==null&&back.length()>0,"IGES → STEP ("+toStep+")");
        double[] props=NativeCadEngine.bodyProperties(1,1,1,0,null,new double[0],back.getAbsolutePath());
        check(props.length==7,"re-imported IGES model evaluates as a solid");
        close("volume kept through IGES (plate minus Ø10 hole)",props[0],120000-Math.PI*25*20,1e-3);
        File junk=new File(dir,"junk.igs");try(FileWriter w=new FileWriter(junk)){w.write("not an IGES file\n");}
        String refused=NativeCadEngine.convertExchange(junk.getAbsolutePath(),back.getAbsolutePath(),0);
        check(refused!=null,"malformed IGES is refused: "+refused);
        System.out.println("PASS IgesKernelProbe: "+checks+" checks");
    }
}
