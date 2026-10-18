import java.util.*;
import pl.tabletcad.app.HiddenLineVisibility;

public final class VisibilityProbe {
    static HiddenLineVisibility.Triangle triangle(double q) {
        return new HiddenLineVisibility.Triangle(new double[]{0,10,0},new double[]{0,0,10},new double[]{q,q,q});
    }
    static void require(boolean good,String label) {if(!good)throw new AssertionError(label);System.out.println("PASS "+label);}
    static boolean close(double a,double b){return Math.abs(a-b)<1e-6;}
    public static void main(String[] args) {
        List<HiddenLineVisibility.Triangle> front=Arrays.asList(triangle(.004));
        require(HiddenLineVisibility.segments(1,1,.002,3,1,.002,front).isEmpty(),"fully hidden edge");
        List<double[]> partial=HiddenLineVisibility.segments(-5,2,.002,15,2,.002,front);
        require(partial.size()==2&&close(partial.get(0)[1],.25)&&close(partial.get(1)[0],.65),"partial occlusion clips edge");
        require(HiddenLineVisibility.segments(1,1,.006,3,1,.006,front).size()==1,"front edge stays visible");
        require(HiddenLineVisibility.segments(1,1,.004,3,1,.004,front).size()==1,"coplanar edge stays visible");
        List<double[]> slope=HiddenLineVisibility.segments(1,1,.002,3,1,.006,front);
        require(slope.size()==1&&Math.abs(slope.get(0)[0]-.5)<.0001,"perspective depth crossing");
        require(!HiddenLineVisibility.visible(1,1,.002,front)&&HiddenLineVisibility.visible(1,1,.006,front),"picking respects occlusion");
        require(HiddenLineVisibility.visible(20,20,.001,front),"outside surface not occluded");
        List<HiddenLineVisibility.Triangle> face=Arrays.asList(triangle(.004),
            new HiddenLineVisibility.Triangle(new double[]{10,10,0},new double[]{0,10,10},new double[]{.004,.004,.004}));
        require(HiddenLineVisibility.segments(1,5,.002,9,5,.002,face).isEmpty(),"rear sketch hidden across triangulation seam");
        require(HiddenLineVisibility.segments(1,5,.004,9,5,.004,face).size()==1,"sketch on front face remains visible");
        List<double[]> across=HiddenLineVisibility.segments(-5,5,.002,15,5,.002,face);
        require(across.size()==2&&close(across.get(0)[1],.25)&&close(across.get(1)[0],.75),"only sketch portions outside solid remain visible");
        System.out.println("ALL VISIBILITY TESTS PASSED");
        double[] a={-5,5,.002},b={15,5,.002};
        require(Double.isInfinite(HiddenLineVisibility.distanceToVisibleSegment(5,5,new double[]{1,5,.002},new double[]{9,5,.002},face)),"fully hidden sketch not selectable");
        require(close(HiddenLineVisibility.distanceToVisibleSegment(5,5,a,b,face),5),"hidden middle does not capture touch");
        require(close(HiddenLineVisibility.distanceToVisibleSegment(-3,6,a,b,face),1),"visible sketch fragment selectable");
        require(close(HiddenLineVisibility.distanceToVisibleSegment(5,5,a,b,Collections.emptyList()),0),"hide solid restores complete sketch selection");
        require(close(HiddenLineVisibility.distanceToVisibleSegment(5,5,new double[]{1,5,.004},new double[]{9,5,.004},face),0),"coplanar front sketch selectable");
        Random random=new Random(9031);List<HiddenLineVisibility.Triangle> naive=new ArrayList<>();HiddenLineVisibility.Index index=new HiddenLineVisibility.Index();
        for(int cycle=0;cycle<3;cycle++) {
            naive.clear();index.clear();
            for(int i=0;i<350;i++){double x=random.nextDouble()*1000-500,y=random.nextDouble()*1000-500;
                HiddenLineVisibility.Triangle t=new HiddenLineVisibility.Triangle(new double[]{x,x+random.nextDouble()*150,x-20},new double[]{y,y-10,y+random.nextDouble()*150},new double[]{.002,.003,.004});naive.add(t);index.add(t);}
            for(int i=0;i<500;i++) {
                double ax=random.nextDouble()*1200-600,ay=random.nextDouble()*1200-600,bx=ax+random.nextDouble()*300-150,by=ay+random.nextDouble()*300-150;
                List<double[]> expected=HiddenLineVisibility.segments(ax,ay,.001,bx,by,.005,naive),actual=HiddenLineVisibility.segments(ax,ay,.001,bx,by,.005,index);
                if(expected.size()!=actual.size())throw new AssertionError("indexed clipping interval count");
                for(int k=0;k<expected.size();k++)if(!close(expected.get(k)[0],actual.get(k)[0])||!close(expected.get(k)[1],actual.get(k)[1]))throw new AssertionError("indexed clipping changes exact visibility");
                if(HiddenLineVisibility.visible(ax,ay,.003,naive)!=HiddenLineVisibility.visible(ax,ay,.003,index))throw new AssertionError("indexed point visibility differs");
            }
        }
        index.clear();require(HiddenLineVisibility.visible(0,0,.001,index),"cleared index has no stale occluders");
        require(true,"1500 indexed segment and point queries match exhaustive clipping after rebuilds");
    }
}
