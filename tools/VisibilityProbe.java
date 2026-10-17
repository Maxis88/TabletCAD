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
    }
}
