import java.util.*;
import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class EdgeGroupProbe {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    static double volume(double[] base,double[] features,String source) {
        double[] p=NativeCadEngine.bodyProperties(40,30,20,0,base,features,source);check(p.length==7,"valid grouped B-Rep");return p[0];
    }
    static void near(double value,double expected,String label){check(Math.abs(value-expected)<1e-5,label+" "+value);}
    static void invalid(double[] group,String label)throws Exception {
        boolean rejected=false;try{EdgeGroup.validate(group);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,label+" rejected by document validator");
        check(NativeCadEngine.evaluateFeatures(40,30,20,0,null,group,null).length==0,label+" rejected natively");
    }
    public static void main(String[] args)throws Exception {
        try{run(args);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    static void run(String[] args)throws Exception {
        double[] base={0,0,0,40,30,0,20},edges=NativeCadEngine.modelEdges(40,30,20,0,base,new double[0],null);
        List<double[]> raw=new ArrayList<>(),anchored=new ArrayList<>();
        for(double[] xy:new double[][]{{0,0},{40,0},{40,30},{0,30}}) {
            double[] f=EdgeFilletProbe.edge(edges,xy[0],xy[1]);raw.add(f);anchored.add(RootEdgeReference.anchor(f,base,edges));
        }
        double[] group=EdgeGroup.create(anchored,3,false);EdgeFillet.validate(group);
        check(EdgeFillet.is(group)&&group[1]==4&&EdgeGroup.members(group).size()==4,"four root references stored in one fillet feature");
        double expected=24000-4*(1-Math.PI/4)*9*20;
        near(volume(base,group,null),expected,"four simultaneous vertical fillets have analytic volume");
        List<double[]> all=new ArrayList<>();for(int i=0;i<edges.length;i+=16)all.add(EdgeFillet.create(edges,i,3));
        double[] rounded=NativeCadEngine.bodyProperties(40,30,20,3,base,new double[0],null);
        check(rounded.length==7,"global rounded box reference available");
        near(volume(base,EdgeGroup.create(all,3,false),null),rounded[0],"twelve adjacent edges share one simultaneous fillet including corners");
        double[] enlarged={0,0,0,60,45,0,35};
        near(volume(enlarged,group,null),60*45*35-4*(1-Math.PI/4)*9*35,"all group references follow resized root");
        double[] resized=EdgeGroup.withRadius(group,2);near(volume(base,resized,null),24000-4*(1-Math.PI/4)*4*20,"editing shared radius updates all members");
        List<double[]> chamfers=new ArrayList<>();for(double[] f:anchored){double[] c=f.clone();c[0]=-5;chamfers.add(c);}
        double[] chamfer=EdgeGroup.create(chamfers,3,true);EdgeChamfer.validate(chamfer);
        near(volume(base,chamfer,null),24000-4*.5*9*20,"four simultaneous chamfers have analytic volume");
        near(volume(enlarged,chamfer,null),60*45*35-4*.5*9*35,"all chamfer references follow resized root");
        double[] cut={0,1,10,10,20,20,5,0,0,20,1,0,0,0,1,0};
        near(volume(base,EdgeFilletProbe.join(group,cut),null),expected-500,"group is one feature before following Boolean cut");
        check(NativeCadEngine.evaluateFeatures(40,30,20,0,base,group,args[0]).length>0,"group exports real STEP");
        near(volume(null,new double[0],args[0]),expected,"STEP round trip preserves grouped result");
        String plain=args[0]+".base.step";check(NativeCadEngine.evaluateFeatures(40,30,20,0,base,new double[0],plain).length>0,"plain STEP fixture written");
        near(volume(null,EdgeGroup.create(raw,3,false),plain),expected,"group on imported STEP resolves exact members");
        CadProject p=CadProject.blank();p.featureBase=base;p.committedFeatures.add(group);p.committedProfileIds.add("");
        CadProject reopened=CadProject.fromJson(p.toJson());check(reopened.committedFeatures.size()==1&&Arrays.equals(group,reopened.committedFeatures.get(0)),"group survives document round trip as one history feature");
        check(CadTimeline.entries(reopened).get(2).title.equals("Zaokrąglenie"),"group has correct history kind");
        double[] broken=group.clone();broken[1]=4.5;invalid(broken,"fractional member count");
        broken=group.clone();broken[11]=1001;invalid(broken,"invalid radius");
        broken=Arrays.copyOf(group,group.length-1);invalid(broken,"truncated group");
        broken=group.clone();broken[16]=23;invalid(broken,"invalid member length");
        broken=group.clone();broken[18]+=2;invalid(broken,"invalid member flags");
        boolean duplicate=false;try{EdgeGroup.create(Arrays.asList(raw.get(0),raw.get(0)),3,false);}catch(IllegalArgumentException expectedError){duplicate=true;}
        check(duplicate,"duplicate selection rejected before native operation");
        double[] missing=raw.get(0).clone();missing[2]+=2;double[] unresolved=EdgeGroup.create(Arrays.asList(missing,raw.get(1)),3,false);
        check(NativeCadEngine.evaluateFeatures(40,30,20,0,base,unresolved,null).length==0,"missing member rejects entire group instead of partial treatment");
        double[] impossible=EdgeGroup.withRadius(group,1000);
        check(NativeCadEngine.evaluateFeatures(40,30,20,0,base,impossible,null).length==0,"impossible shared radius rejected atomically");
        System.out.println("ALL EDGE GROUP TESTS PASSED");
    }
}
