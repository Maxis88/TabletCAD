import pl.tabletcad.app.*;

public final class BodyPropertiesProbe {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    static void near(double a,double b,String label){check(Math.abs(a-b)<1e-5,label);}
    static double[] properties(double[] features,String source){double[] p=NativeCadEngine.bodyProperties(40,30,20,0,null,features,source);check(p.length==7,"exact body properties available");return p;}
    public static void main(String[] args){try{run(args);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
    static void run(String[] args) {
        double[] p=properties(new double[0],null);near(p[0],24000,"box volume");near(p[1],5200,"box area");
        near(p[2],20,"box centroid X");near(p[3],15,"box centroid Y");near(p[4],10,"box centroid Z");
        check(p[5]==1&&p[6]==6,"box topology counts");
        double[] hole={1,1,20,15,25,15,20,0,0,20,1,0,0,0,1,0};
        p=properties(hole,null);near(p[0],24000-Math.PI*25*20,"through-hole exact volume");
        near(p[1],5200-2*Math.PI*25+2*Math.PI*5*20,"through-hole exact analytic surface area");
        near(p[2],20,"through-hole centroid X");near(p[3],15,"through-hole centroid Y");near(p[4],10,"through-hole centroid Z");
        double[] edges=NativeCadEngine.modelEdges(40,30,20,0,null,new double[0],null);
        double[] chamfer=EdgeFilletProbe.edge(edges,0,0);chamfer[0]=-2;
        p=properties(chamfer,null);near(p[0],23910,"chamfer exact volume");
        near(p[2],(24000*20-90)/23910.0,"chamfer shifted centroid X");
        near(p[3],(24000*15-90)/23910.0,"chamfer shifted centroid Y");near(p[4],10,"chamfer centroid Z");
        check(NativeCadEngine.evaluateFeatures(40,30,20,0,null,hole,args[0]).length>0,"holed body exported to STEP");
        double[] imported=properties(new double[0],args[0]),before=properties(hole,null);
        for(int i=0;i<7;i++)near(imported[i],before[i],"STEP property preserved "+i);
        double[] bad=hole.clone();bad[6]=Double.NaN;
        check(NativeCadEngine.bodyProperties(40,30,20,0,null,bad,null).length==0,"invalid shape cannot return plausible properties");
        System.out.println("ALL BODY PROPERTIES TESTS PASSED");
    }
}
