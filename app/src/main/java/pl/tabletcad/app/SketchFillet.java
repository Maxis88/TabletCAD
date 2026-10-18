package pl.tabletcad.app;

import pl.tabletcad.app.model.SketchPrimitive;

/** Tangent circular corner fillets of a closed straight profile. */
public final class SketchFillet {
    private SketchFillet() {}
    public static SketchPrimitive apply(SketchPrimitive source,double radius) {
        if(!Double.isFinite(radius)||radius<.01||radius>5000)throw new IllegalArgumentException("Promień: 0,01–5000 mm");
        if(source.driving!=null)throw new IllegalArgumentException("Odblokuj wymiary figury przed zaokrągleniem narożników");
        if(SketchSpline.is(source)||source.curves!=null||!source.closedProfile()||source.type.equals("Okrąg"))throw new IllegalArgumentException("Wybierz prostokąt lub zamknięty kontur odcinków");
        double[] points=source.vertices!=null?source.vertices:new double[]{source.x1,source.y1,source.x2,source.y1,source.x2,source.y2,source.x1,source.y2};
        int n=points.length/2;double[][] enter=new double[n][2],exit=new double[n][2],mid=new double[n][2];double[] tangent=new double[n],length=new double[n];
        for(int i=0;i<n;i++) {
            int prev=(i+n-1)%n,next=(i+1)%n;double x=points[2*i],y=points[2*i+1];
            double ax=x-points[2*prev],ay=y-points[2*prev+1],bx=points[2*next]-x,by=points[2*next+1]-y;
            double al=Math.hypot(ax,ay),bl=Math.hypot(bx,by);if(al<.01||bl<.01)throw new IllegalArgumentException("Zerowy bok konturu");
            ax/=al;ay/=al;bx/=bl;by/=bl;length[i]=bl;
            double half=Math.acos(Math.max(-1,Math.min(1,-ax*bx-ay*by)))/2;
            if(half<1e-5||half>Math.PI/2-1e-5)throw new IllegalArgumentException("Narożnik musi mieć niezerowy kąt");
            double d=radius/Math.tan(half);tangent[i]=d;
            enter[i]=new double[]{x-ax*d,y-ay*d};exit[i]=new double[]{x+bx*d,y+by*d};
            double ux=bx-ax,uy=by-ay,ul=Math.hypot(ux,uy),distance=radius/Math.sin(half);
            double cx=x+ux/ul*distance,cy=y+uy/ul*distance;
            double mx=enter[i][0]+exit[i][0]-2*cx,my=enter[i][1]+exit[i][1]-2*cy,ml=Math.hypot(mx,my);
            mid[i]=new double[]{cx+mx/ml*radius,cy+my/ml*radius};
        }
        double[] curves=new double[n*14];
        for(int i=0;i<n;i++) {
            int next=(i+1)%n;if(tangent[i]+tangent[next]>=length[i]-.001)throw new IllegalArgumentException("Promień za duży dla długości sąsiednich boków");
            int o=i*14;curves[o]=0;curves[o+1]=exit[i][0];curves[o+2]=exit[i][1];curves[o+5]=enter[next][0];curves[o+6]=enter[next][1];
            curves[o+7]=1;curves[o+8]=enter[next][0];curves[o+9]=enter[next][1];curves[o+10]=mid[next][0];curves[o+11]=mid[next][1];curves[o+12]=exit[next][0];curves[o+13]=exit[next][1];
        }
        return new SketchPrimitive(source.id,curves,true);
    }
}
