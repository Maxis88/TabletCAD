import java.util.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

public final class TimelineProbe {
    static void require(boolean v,String label){if(!v)throw new AssertionError(label);System.out.println("PASS "+label);}
    public static void main(String[] args)throws Exception {
        CadProject p=CadProject.blank();require(CadTimeline.entries(p).size()==1,"empty document only shows start");
        p.featureBase=new double[]{0,0,0,40,20,0,-12};p.sketchExtruded=false;
        p.savedSketches.add(new org.json.JSONObject().put("id","archived").put("entities",new org.json.JSONArray()));
        List<CadTimeline.Item> items=CadTimeline.entries(p);
        require(items.size()==3&&items.get(1).kind==CadTimeline.Kind.BASE&&items.get(2).kind==CadTimeline.Kind.SKETCH,"base and archived sketches remain visible without active sketch");
        p.committedFeatures.add(new double[]{0,0,0,0,5,5,3,0,0,0,1,0,0,0,1,0});p.committedProfileIds.add("add");
        p.committedFeatures.add(new double[]{0,1,0,0,2,2,2,0,0,0,1,0,0,0,1,0});p.committedProfileIds.add("cut");p.featureOperation="add";
        items=CadTimeline.entries(p);
        require(items.get(2).kind==CadTimeline.Kind.FEATURE&&items.get(2).featureIndex==0&&items.get(3).featureIndex==1,"displayed feature icons retain exact feature indexes after base entry");
        require(items.get(items.size()-1).kind==CadTimeline.Kind.ACTIVE,"active operation separately addressable");
        p.importedStep=true;items=CadTimeline.entries(p);
        require(items.get(1).kind==CadTimeline.Kind.IMPORT&&items.get(2).featureIndex==0,"import entry does not shift feature edit mapping");
        p.filletRadius=1.5;items=CadTimeline.entries(p);require(items.get(items.size()-1).kind==CadTimeline.Kind.FILLET,"fillet remains separate action");
        CadProject legacy=new CadProject();require(CadTimeline.entries(legacy).get(1).kind==CadTimeline.Kind.BASE,"legacy default body has editable base icon");
        CadProject restored=CadProject.fromJson(p.toJson());require(CadTimeline.entries(restored).size()==items.size(),"timeline mapping rebuilds after project reload");
        System.out.println("ALL TIMELINE TESTS PASSED");
    }
}
