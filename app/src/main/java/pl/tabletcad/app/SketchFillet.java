package pl.tabletcad.app;

import pl.tabletcad.app.model.SketchPrimitive;

/** Tangent circular corner fillets of a closed straight profile. */
public final class SketchFillet {
    private SketchFillet() {}
    public static java.util.List<SketchPrimitive> pair(SketchPrimitive a,SketchPrimitive b,double radius) {
        if(!(a.type.equals("Linia")||a.type.equals("Pomocnicza"))||!(b.type.equals("Linia")||b.type.equals("Pomocnicza"))||a.id.equals(b.id)||a.driving!=null||b.driving!=null)throw new IllegalArgumentException("Wybierz dwie różne linie bez zablokowanych wymiarów");
        if(a.construction!=b.construction)throw new IllegalArgumentException("Wybierz dwie linie tego samego rodzaju geometrii");
        if(!Double.isFinite(radius)||radius<.01||radius>5000)throw new IllegalArgumentException("Promień: 0,01–5000 mm");
        double[][] ap={{a.x1,a.y1},{a.x2,a.y2}},bp={{b.x1,b.y1},{b.x2,b.y2}};int ai=-1,bi=-1;
        for(int i=0;i<2;i++)for(int j=0;j<2;j++)if(Math.hypot(ap[i][0]-bp[j][0],ap[i][1]-bp[j][1])<1e-6){ai=i;bi=j;}
        if(ai<0)throw new IllegalArgumentException("Linie muszą spotykać się końcami w jednym narożniku");
        double x=ap[ai][0],y=ap[ai][1],ax=ap[1-ai][0]-x,ay=ap[1-ai][1]-y,bx=bp[1-bi][0]-x,by=bp[1-bi][1]-y;
        double al=Math.hypot(ax,ay),bl=Math.hypot(bx,by);ax/=al;ay/=al;bx/=bl;by/=bl;
        double half=Math.acos(Math.max(-1,Math.min(1,ax*bx+ay*by)))/2;
        if(half<1e-5||half>Math.PI/2-1e-5)throw new IllegalArgumentException("Linie nie tworzą ostrego narożnika");
        double d=radius/Math.tan(half);if(d>=Math.min(al,bl)-.001)throw new IllegalArgumentException("Promień za duży dla wybranych linii");
        double ex=x+ax*d,ey=y+ay*d,fx=x+bx*d,fy=y+by*d,ux=ax+bx,uy=ay+by,ul=Math.hypot(ux,uy),distance=radius/Math.sin(half);
        double cx=x+ux/ul*distance,cy=y+uy/ul*distance,mx=ex+fx-2*cx,my=ey+fy-2*cy,ml=Math.hypot(mx,my);
        ap[ai]=new double[]{ex,ey};bp[bi]=new double[]{fx,fy};
        SketchPrimitive aa=new SketchPrimitive(a.id,"Linia",ap[0][0],ap[0][1],ap[1][0],ap[1][1]).withConstruction(a.construction),bb=new SketchPrimitive(b.id,"Linia",bp[0][0],bp[0][1],bp[1][0],bp[1][1]).withConstruction(b.construction);
        SketchPrimitive arc=new SketchPrimitive(java.util.UUID.randomUUID().toString(),SketchCurves.arc(ex,ey,cx+mx/ml*radius,cy+my/ml*radius,fx,fy),false).withConstruction(a.construction);
        return java.util.Arrays.asList(aa,bb,arc);
    }
    public static SketchPrimitive apply(SketchPrimitive source,double radius) {
        if(!Double.isFinite(radius)||radius<.01||radius>5000)throw new IllegalArgumentException("Promień: 0,01–5000 mm");
        if(source.driving!=null)throw new IllegalArgumentException("Odblokuj wymiary figury przed zaokrągleniem narożników");
        if(SketchSpline.is(source)||source.curves!=null||!source.closedGeometry()||source.type.equals("Okrąg"))throw new IllegalArgumentException("Wybierz prostokąt lub zamknięty kontur odcinków");
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
        return new SketchPrimitive(source.id,curves,true).withConstruction(source.construction);
    }
}
