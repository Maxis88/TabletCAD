package pl.tabletcad.app;

import pl.tabletcad.app.model.SketchPrimitive;

/** Exact reflection in sketch coordinates; the copy is independently editable. */
public final class SketchMirror {
    private SketchMirror() {}
    private static double[] point(double x,double y,double ax,double ay,double ux,double uy) {
        double dx=x-ax,dy=y-ay,t=dx*ux+dy*uy;
        double px=ax+2*t*ux-dx,py=ay+2*t*uy-dy;
        if(!Double.isFinite(px)||!Double.isFinite(py)||Math.abs(px)>5000||Math.abs(py)>5000)
            throw new IllegalArgumentException("Odbicie wykracza poza zakres szkicu ±5000 mm");
        return new double[]{px,py};
    }
    public static SketchPrimitive copy(SketchPrimitive source,String id,double ax,double ay,double bx,double by) {
        if(source.isText())throw new IllegalArgumentException("Napisu nie odbija się lustrzanie (litery byłyby odwrócone) – wstaw drugi napis albo obróć go");
        return copyGeometry(source,id,ax,ay,bx,by).withConstruction(source.construction);
    }
    private static SketchPrimitive copyGeometry(SketchPrimitive source,String id,double ax,double ay,double bx,double by) {
        if(source==null||id==null||id.isEmpty()||id.equals(source.id))throw new IllegalArgumentException("Kopia wymaga nowej tożsamości");
        double length=Math.hypot(bx-ax,by-ay);
        if(!Double.isFinite(ax)||!Double.isFinite(ay)||!Double.isFinite(bx)||!Double.isFinite(by)||!Double.isFinite(length)||length<.01)
            throw new IllegalArgumentException("Oś lustra musi być niezerową linią");
        double ux=(bx-ax)/length,uy=(by-ay)/length;
        if(source.isEllipse()){double[] c=point(source.x1,source.y1,ax,ay,ux,uy),e=point(source.x2,source.y2,ax,ay,ux,uy);return SketchPrimitive.ellipse(id,c[0],c[1],e[0],e[1],source.minor);}
        if(source.isPoint()){double[] c=point(source.x1,source.y1,ax,ay,ux,uy);return SketchPrimitive.point(id,c[0],c[1]);}
        if(source.curves!=null) {
            double[] curves=source.curves.clone();
            for(int i=0;i<curves.length;i+=7)for(int j:new int[]{1,3,5}) {
                if(j==3&&curves[i]==0)continue;
                double[] p=point(curves[i+j],curves[i+j+1],ax,ay,ux,uy);curves[i+j]=p[0];curves[i+j+1]=p[1];
            }
            return new SketchPrimitive(id,curves,source.closedGeometry());
        }
        double[] vertices=source.vertices;
        if(source.type.equals("Prostokąt"))vertices=new double[]{source.x1,source.y1,source.x2,source.y1,source.x2,source.y2,source.x1,source.y2};
        if(vertices!=null) {
            double[] result=vertices.clone();
            for(int i=0;i<result.length;i+=2){double[] p=point(result[i],result[i+1],ax,ay,ux,uy);result[i]=p[0];result[i+1]=p[1];}
            if(source.type.equals("Prostokąt")&&(Math.abs(result[0]-result[2])<1e-8||Math.abs(result[1]-result[3])<1e-8))
                return new SketchPrimitive(id,"Prostokąt",result[0],result[1],result[4],result[5]);
            if(SketchSpline.is(source))return new SketchPrimitive(id,result,"Splajn");
            SketchContours.validate(result);return new SketchPrimitive(id,result);
        }
        if(!source.type.equals("Linia")&&!source.type.equals("Pomocnicza")&&!source.type.equals("Okrąg"))
            throw new IllegalArgumentException("Nieobsługiwany typ geometrii lustra");
        double[] a=point(source.x1,source.y1,ax,ay,ux,uy),b=point(source.x2,source.y2,ax,ay,ux,uy);
        return new SketchPrimitive(id,source.type,a[0],a[1],b[0],b[1]);
    }
}
