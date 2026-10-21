import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** M12 coarse and fine threads in a 20 mm plate: the groove must remove (almost) the whole tool. */
public final class ThreadPitchProbe {
    static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);System.out.println("PASS "+m);}
    public static void main(String[] args)throws Exception {
        // Hole close to the side wall (the user's case): wall between the bore and the side face.
        for(double cx:new double[]{8,7,6.6,6.2}){
            double r=5.1;double[] hole={1,1,cx,20,cx+r,20,20,0,0,20,1,0,0,0,1,0};
            CadModelResult drilled=new CadModelResult(NativeCadEngine.evaluateModel(40,40,20,0,null,hole,null));HoleThread.Cylinder h=drilled.holes.get(0);
            double[] t=HoleThread.create(h,new HoleThread.Preset(12,1.75),20,true,false);
            CadProject p=new CadProject();p.width=40;p.height=40;p.depth=20;p.committedFeatures.add(hole);p.committedProfileIds.add("");BodyGraph.begin(p);p.committedFeatures.add(t);p.committedProfileIds.add("");BodyGraph.reconcile(p);
            double[] raw=new double[32];System.arraycopy(hole,0,raw,0,16);System.arraycopy(t,0,raw,16,16);
            double before=NativeCadEngine.bodyProperties(40,40,20,0,null,hole,null)[0];double[] after=NativeCadEngine.bodyProperties(40,40,20,0,null,BodyGraph.pack(p,raw,true),null);
            System.out.println("INFO edge centre "+cx+" (wall to thread "+(cx-6)+"): removed "+(after.length==7?before-after[0]:-1)+(after.length==7?"":" "+NativeCadEngine.lastFailure().getMessage()));
        }
        for(double[] c:new double[][]{{10.5,1.5},{10.2,1.75},{8.5,1.25},{6.8,1},{5,1}}) {
            double r=c[0]/2,major=Math.round(c[0]+c[1])/2.0;if(c[0]==8.5)major=5;if(c[0]==6.8)major=4;if(c[0]==5)major=3;
            for(double depth:new double[]{20,12})for(boolean max:new boolean[]{true,false}){
            double[] hole={1,1,20,20,20+r,20,20,0,0,20,1,0,0,0,1,0};
            CadModelResult drilled=new CadModelResult(NativeCadEngine.evaluateModel(40,40,20,0,null,hole,null));HoleThread.Cylinder h=drilled.holes.get(0);
            double[] t=HoleThread.create(h,new HoleThread.Preset(major*2,c[1]),depth,max,false);
            CadProject p=new CadProject();p.width=40;p.height=40;p.depth=20;p.committedFeatures.add(hole);p.committedProfileIds.add("");BodyGraph.begin(p);p.committedFeatures.add(t);p.committedProfileIds.add("");BodyGraph.reconcile(p);
            double[] raw=new double[32];System.arraycopy(hole,0,raw,0,16);System.arraycopy(t,0,raw,16,16);
            double before=NativeCadEngine.bodyProperties(40,40,20,0,null,hole,null)[0];double[] after=NativeCadEngine.bodyProperties(40,40,20,0,null,BodyGraph.pack(p,raw,true),null);
            double height=major-r,expected=height*height/Math.sqrt(3)*2*Math.PI*(r+height/2)*depth/c[1];
            double removed=after.length==7?before-after[0]:-1;
            System.out.println("INFO M"+major*2+"x"+c[1]+" depth "+depth+" max "+max+": removed "+removed+" expected≈"+expected);
            check(removed>.8*expected&&removed<1.2*expected,"M"+major*2+"x"+c[1]+" depth "+depth+" from "+(max?"max":"min")+" removes the full groove ("+removed+" / ≈"+expected+")");}
        }
    }
}
