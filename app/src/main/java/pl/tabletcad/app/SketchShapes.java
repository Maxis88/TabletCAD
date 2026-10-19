package pl.tabletcad.app;

import pl.tabletcad.app.model.SketchPrimitive;

/** Exact sketch constructions. Curves remain analytic through modeling and exchange. */
public final class SketchShapes {
    private SketchShapes() {}
    public static boolean threePointTool(String kind){return "Łuk".equals(kind)||"Okrąg 3 pkt".equals(kind)||"Szczelina".equals(kind)||"Elipsa".equals(kind);}
    public static boolean drawingTool(String kind){return "Kwadrat".equals(kind)||"Splajn".equals(kind)||threePointTool(kind)||"Linia".equals(kind)||"Polilinia".equals(kind)||"Pomocnicza".equals(kind)||"Okrąg".equals(kind)||"Prostokąt".equals(kind)||"Prostokąt od środka".equals(kind)||"Wielokąt".equals(kind);}
    private static void point(double x,double y) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>5000||Math.abs(y)>5000)
            throw new IllegalArgumentException("Szkic poza zakresem ±5000 mm");
    }
    private static void circle(double x,double y,double radius) {
        if(!Double.isFinite(radius)||radius<.01||radius>5000)throw new IllegalArgumentException("Promień: 0,01–5000 mm");
        point(x-radius,y-radius);point(x+radius,y+radius);
    }
    public static SketchPrimitive drag(String id,String tool,double ax,double ay,double bx,double by,int sides) {
        point(ax,ay);point(bx,by);
        if(Math.hypot(bx-ax,by-ay)<.01)throw new IllegalArgumentException("Rozsuń punkty szkicu");
        if(tool.equals("Kwadrat")) {
            double side=Math.max(Math.abs(bx-ax),Math.abs(by-ay));bx=ax+Math.copySign(side,bx-ax);by=ay+Math.copySign(side,by-ay);
            point(bx,by);return new SketchPrimitive(id,"Prostokąt",ax,ay,bx,by,new double[]{side,side});
        }
        if(tool.equals("Prostokąt od środka")) {
            double x=2*ax-bx,y=2*ay-by;point(x,y);
            if(Math.abs(bx-ax)<.005||Math.abs(by-ay)<.005)throw new IllegalArgumentException("Prostokąt musi mieć dodatnią szerokość i wysokość");
            return new SketchPrimitive(id,"Prostokąt",x,y,bx,by);
        }
        if(tool.equals("Wielokąt")) {
            if(sides<3||sides>128)throw new IllegalArgumentException("Liczba boków: 3–128");
            double radius=Math.hypot(bx-ax,by-ay),angle=Math.atan2(by-ay,bx-ax),step=2*Math.PI/sides;
            double[] vertices=new double[sides*2];
            for(int i=0;i<sides;i++){vertices[2*i]=ax+radius*Math.cos(angle+i*step);vertices[2*i+1]=ay+radius*Math.sin(angle+i*step);point(vertices[2*i],vertices[2*i+1]);}
            SketchContours.validate(vertices);return new SketchPrimitive(id,vertices);
        }
        if(tool.equals("Okrąg"))circle(ax,ay,Math.hypot(bx-ax,by-ay));
        else if(tool.equals("Prostokąt")){if(Math.abs(bx-ax)<.01||Math.abs(by-ay)<.01)throw new IllegalArgumentException("Prostokąt musi mieć dodatnią szerokość i wysokość");}
        else if(!tool.equals("Linia")&&!tool.equals("Pomocnicza"))throw new IllegalArgumentException("Nieobsługiwane narzędzie rysowania");
        return new SketchPrimitive(id,tool,ax,ay,bx,by);
    }
    public static SketchPrimitive threePoint(String id,String tool,double[] a,double[] b,double[] c) {
        for(double[] p:new double[][]{a,b,c}){if(p==null||p.length!=2)throw new IllegalArgumentException("Punkt wymaga dwóch współrzędnych");point(p[0],p[1]);}
        if(tool.equals("Łuk"))return new SketchPrimitive(id,SketchCurves.arc(a[0],a[1],c[0],c[1],b[0],b[1]),false);
        if(tool.equals("Okrąg 3 pkt")) {
            SketchCurves.Arc arc=SketchCurves.arc(SketchCurves.arc(a[0],a[1],c[0],c[1],b[0],b[1]),0);
            circle(arc.x,arc.y,arc.r);return new SketchPrimitive(id,"Okrąg",arc.x,arc.y,a[0],a[1]);
        }
        if(tool.equals("Elipsa")) {
            // Centre, end of the semi-major axis, and a point whose distance from the major axis is the semi-minor axis.
            double major=Math.hypot(b[0]-a[0],b[1]-a[1]);if(major<.01)throw new IllegalArgumentException("Półoś wielka musi wynosić co najmniej 0,01 mm");
            double minor=Math.abs((c[0]-a[0])*(-(b[1]-a[1])/major)+(c[1]-a[1])*((b[0]-a[0])/major));
            if(minor<.01)throw new IllegalArgumentException("Wskaż półoś małą poza osią wielką");
            if(minor>major)throw new IllegalArgumentException("Półoś mała jest dłuższa od wielkiej – zacznij od dłuższej osi");
            return SketchPrimitive.ellipse(id,a[0],a[1],b[0],b[1],minor);
        }
        if(!tool.equals("Szczelina"))throw new IllegalArgumentException("Nieobsługiwana konstrukcja trzypunktowa");
        double length=Math.hypot(b[0]-a[0],b[1]-a[1]);if(length<.01)throw new IllegalArgumentException("Środki końców szczeliny muszą być różne");
        double ux=(b[0]-a[0])/length,uy=(b[1]-a[1])/length,nx=-uy,ny=ux;
        double radius=Math.abs((c[0]-a[0])*nx+(c[1]-a[1])*ny);
        if(radius<.01)throw new IllegalArgumentException("Wskaż szerokość szczeliny poza jej osią");
        double ax=a[0]+nx*radius,ay=a[1]+ny*radius,bx=b[0]+nx*radius,by=b[1]+ny*radius;
        double cx=b[0]-nx*radius,cy=b[1]-ny*radius,dx=a[0]-nx*radius,dy=a[1]-ny*radius;
        double[] curves={0,ax,ay,0,0,bx,by,
            1,bx,by,b[0]+ux*radius,b[1]+uy*radius,cx,cy,
            0,cx,cy,0,0,dx,dy,
            1,dx,dy,a[0]-ux*radius,a[1]-uy*radius,ax,ay};
        return new SketchPrimitive(id,curves,true);
    }
    public static String instruction(String tool,int count) {
        if(tool.equals("Szczelina"))return count==0?"Szczelina: dotknij środka pierwszego końca":count==1?"Szczelina: dotknij środka drugiego końca":"Szczelina: wskaż szerokość, dotykając poza linią łączącą środki";
        if(tool.equals("Elipsa"))return count==0?"Elipsa: dotknij środka":count==1?"Elipsa: dotknij końca półosi wielkiej":"Elipsa: wskaż półoś małą (odległość od osi wielkiej)";
        if(tool.equals("Okrąg 3 pkt"))return "Okrąg: dotknij "+(count==0?"pierwszego":count==1?"drugiego":"trzeciego")+" punktu obwodu";
        return count==0?"Łuk: dotknij początku":count==1?"Łuk: dotknij końca":"Łuk: wskaż punkt pośredni po właściwej stronie cięciwy";
    }
}
