package pl.tabletcad.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Navigation cube: the six principal views as faces of a small cube that turns with the camera.
 * Uses the same rotation as the model view (yaw about Y, then pitch about X, screen Y down).
 * Pure geometry, so it can be tested without Android.
 */
public final class ViewCube {
    private ViewCube() {}
    /** Face name (standard view), outward normal and two in-face axes. */
    public static final class Face {
        public final String view,label;public final double[] normal,u,v;
        Face(String view,String label,double[] n,double[] u,double[] v){this.view=view;this.label=label;normal=n;this.u=u;this.v=v;}
    }
    public static final Face[] FACES={
        new Face("Przód","PRZÓD",new double[]{0,0,1},new double[]{1,0,0},new double[]{0,1,0}),
        new Face("Tył","TYŁ",new double[]{0,0,-1},new double[]{-1,0,0},new double[]{0,1,0}),
        new Face("Góra","GÓRA",new double[]{0,1,0},new double[]{1,0,0},new double[]{0,0,-1}),
        new Face("Dół","DÓŁ",new double[]{0,-1,0},new double[]{1,0,0},new double[]{0,0,1}),
        new Face("Prawy bok","PRAWY",new double[]{1,0,0},new double[]{0,0,-1},new double[]{0,1,0}),
        new Face("Lewy bok","LEWY",new double[]{-1,0,0},new double[]{0,0,1},new double[]{0,1,0})};

    /** {screen dx, screen dy (down), depth toward the viewer} of a unit-scale vector. */
    public static double[] project(double yawDeg,double pitchDeg,double[] p) {
        double y=Math.toRadians(yawDeg),t=Math.toRadians(pitchDeg),cy=Math.cos(y),sy=Math.sin(y),cp=Math.cos(t),sp=Math.sin(t);
        double rx=p[0]*cy+p[2]*sy,rz=-p[0]*sy+p[2]*cy,ry=p[1]*cp-rz*sp,depth=p[1]*sp+rz*cp;
        return new double[]{rx,-ry,depth};
    }
    /** Projected face: screen polygon (4 points), centre and how much it faces the viewer. */
    public static final class Projected {
        public final Face face;public final float[] polygon;public final float cx,cy;public final double facing;
        Projected(Face f,float[] poly,float cx,float cy,double facing){face=f;polygon=poly;this.cx=cx;this.cy=cy;this.facing=facing;}
    }
    /** Visible faces, farthest first (drawing order); half = half the cube edge in pixels. */
    public static List<Projected> visible(double yaw,double pitch,float centerX,float centerY,float half) {
        List<Projected> out=new ArrayList<>();
        for(Face f:FACES) {
            double facing=project(yaw,pitch,f.normal)[2];if(facing<=1e-3)continue;
            float[] poly=new float[8];double[][] signs={{-1,-1},{1,-1},{1,1},{-1,1}};
            for(int i=0;i<4;i++){double[] p={f.normal[0]+f.u[0]*signs[i][0]+f.v[0]*signs[i][1],f.normal[1]+f.u[1]*signs[i][0]+f.v[1]*signs[i][1],f.normal[2]+f.u[2]*signs[i][0]+f.v[2]*signs[i][1]};
                double[] s=project(yaw,pitch,p);poly[2*i]=(float)(centerX+s[0]*half);poly[2*i+1]=(float)(centerY+s[1]*half);}
            double[] c=project(yaw,pitch,f.normal);out.add(new Projected(f,poly,(float)(centerX+c[0]*half),(float)(centerY+c[1]*half),facing));
        }
        out.sort((a,b)->Double.compare(a.facing,b.facing));return out;
    }
    /** Standard view under the touch point (front-most face), or null. */
    public static String hit(double yaw,double pitch,float centerX,float centerY,float half,float x,float y) {
        List<Projected> faces=visible(yaw,pitch,centerX,centerY,half);
        for(int i=faces.size()-1;i>=0;i--)if(contains(faces.get(i).polygon,x,y)) {
            Projected p=faces.get(i);
            // Looking straight at a face, its outer band turns to the neighbouring face in that direction.
            if(p.facing>.95) {
                double[] c=project(yaw,pitch,p.face.normal),cu=project(yaw,pitch,add(p.face.normal,p.face.u)),cv=project(yaw,pitch,add(p.face.normal,p.face.v));
                double ux=(cu[0]-c[0])*half,uy=(cu[1]-c[1])*half,vx=(cv[0]-c[0])*half,vy=(cv[1]-c[1])*half,dx=x-p.cx,dy=y-p.cy,det=ux*vy-uy*vx;
                if(Math.abs(det)>1e-9) {
                    double su=(dx*vy-dy*vx)/det,sv=(ux*dy-uy*dx)/det;
                    if(Math.max(Math.abs(su),Math.abs(sv))>.55) {
                        double[] n=Math.abs(su)>=Math.abs(sv)?scale(p.face.u,Math.signum(su)):scale(p.face.v,Math.signum(sv));
                        for(Face f:FACES)if(f.normal[0]==n[0]&&f.normal[1]==n[1]&&f.normal[2]==n[2])return f.view;
                    }
                }
            }
            return p.face.view;
        }
        return null;
    }
    static double[] add(double[] a,double[] b){return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    static double[] scale(double[] a,double k){return new double[]{a[0]*k+0.0,a[1]*k+0.0,a[2]*k+0.0};}
    static boolean contains(float[] p,float x,float y) {
        boolean inside=false;for(int i=0,j=3;i<4;j=i++){float xi=p[2*i],yi=p[2*i+1],xj=p[2*j],yj=p[2*j+1];if((yi>y)!=(yj>y)&&x<(xj-xi)*(y-yi)/(yj-yi)+xi)inside=!inside;}return inside;
    }
}
