import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Outline (offset) of ellipses and splines: constant distance from the source, direction, collapse rejected. */
public final class SketchOffsetCurvesHostTest {
    static int checks;
    static void check(boolean ok,String t){if(!ok)throw new AssertionError(t);checks++;System.out.println("PASS "+t);}
    /** Distance range from points of curve b to polyline a. */
    static double[] distances(double[] a,double[] b){double lo=1e18,hi=0;for(int i=0;i<b.length;i+=2){double best=1e18;for(int j=0;j+3<a.length;j+=2){double ax=a[j],ay=a[j+1],dx=a[j+2]-ax,dy=a[j+3]-ay,l=dx*dx+dy*dy,t=l<1e-18?0:Math.max(0,Math.min(1,((b[i]-ax)*dx+(b[i+1]-ay)*dy)/l));best=Math.min(best,Math.hypot(b[i]-ax-t*dx,b[i+1]-ay-t*dy));}lo=Math.min(lo,best);hi=Math.max(hi,best);}return new double[]{lo,hi};}
    static double[] ellipse(SketchPrimitive e,int n){double a=e.major(),b=e.minor,ux=(e.x2-e.x1)/a,uy=(e.y2-e.y1)/a;double[] r=new double[2*n+2];for(int i=0;i<=n;i++){double t=2*Math.PI*i/n,x=a*Math.cos(t),y=b*Math.sin(t);r[2*i]=e.x1+x*ux-y*uy;r[2*i+1]=e.y1+x*uy+y*ux;}return r;}
    public static void main(String[] args) {
        SketchPrimitive e=SketchPrimitive.ellipse("e",10,5,40,5,15);
        SketchPrimitive out=SketchOffset.offset(e,"o",2);check(SketchSpline.is(out)&&SketchSpline.closed(out.vertices),"ellipse outline is a closed spline");
        double[] d=distances(ellipse(e,4000),SketchSpline.sample(out.vertices,32));check(Math.abs(d[0]-2)<.01&&Math.abs(d[1]-2)<.01,"ellipse grown by 2 mm everywhere ("+d[0]+".."+d[1]+")");
        SketchPrimitive in=SketchOffset.offset(e,"i",-3);d=distances(ellipse(e,4000),SketchSpline.sample(in.vertices,32));check(Math.abs(d[0]-3)<.01&&Math.abs(d[1]-3)<.01,"ellipse shrunk by 3 mm ("+d[0]+".."+d[1]+")");
        check(Math.abs(SketchContours.area(SketchSpline.sample(in.vertices,8)))<Math.PI*30*15,"shrunk outline lies inside");
        boolean collapse=false;try{SketchOffset.offset(e,"c",-8);}catch(IllegalArgumentException x){collapse=true;}check(collapse,"inward offset beyond the curvature radius b²/a = 7.5 mm rejected");
        // Closed spline (both windings) grows outward.
        double[] loop={0,0,40,0,50,30,20,45,-5,25,0,0};
        for(boolean reversed:new boolean[]{false,true}){double[] pts=loop.clone();if(reversed)for(int i=0;i<pts.length/2;i++){pts[2*i]=loop[loop.length-2-2*i];pts[2*i+1]=loop[loop.length-1-2*i];}
            SketchPrimitive s=new SketchPrimitive("s",pts,"Splajn"),g=SketchOffset.offset(s,"g",1.5);
            double[] src=SketchSpline.sample(pts,64);d=distances(src,SketchSpline.sample(g.vertices,8));
            check(Math.abs(d[0]-1.5)<.02&&Math.abs(d[1]-1.5)<.02,"closed spline outline at 1.5 mm ("+d[0]+".."+d[1]+(reversed?", reversed":"")+")");
            check(Math.abs(SketchContours.area(SketchSpline.sample(g.vertices,8)))>Math.abs(SketchContours.area(src)),"positive distance grows the closed spline"+(reversed?" (reversed)":""));}
        // Open spline: to the left of its direction.
        SketchPrimitive open=new SketchPrimitive("o",new double[]{0,0,20,10,40,0,60,10},"Splajn");SketchPrimitive left=SketchOffset.offset(open,"l",2);
        d=distances(SketchSpline.sample(open.vertices,64),SketchSpline.sample(left.vertices,8));check(Math.abs(d[0]-2)<.02&&Math.abs(d[1]-2)<.02,"open spline offset 2 mm");
        check(left.vertices[1]>0,"open spline offset goes to the left");
        // A closed chain of separate lines is outlined as one contour.
        java.util.List<SketchPrimitive> lines=java.util.Arrays.asList(new SketchPrimitive("a","Linia",0,0,40,0),new SketchPrimitive("b","Linia",40,0,40,20),new SketchPrimitive("c","Linia",40,20,0,20),new SketchPrimitive("d","Linia",0,20,0,0));
        SketchContours.Result chain=SketchContours.join(lines,"b");check(chain.consumed.size()==4,"chain of 4 lines found from one of them");
        SketchPrimitive grown=SketchOffset.offset(chain.profile,"g",3);check(Math.abs(Math.abs(SketchContours.area(grown.vertices))-46*26)<1e-6,"chain outline grows 40×20 to 46×26");
        SketchPrimitive shrunk=SketchOffset.offset(chain.profile,"s",-3);check(Math.abs(Math.abs(SketchContours.area(shrunk.vertices))-34*14)<1e-6,"chain outline shrinks to 34×14");
        System.out.println("PASS SketchOffsetCurvesHostTest: "+checks+" checks");
    }
}
