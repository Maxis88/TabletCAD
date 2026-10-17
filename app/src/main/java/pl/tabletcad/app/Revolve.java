package pl.tabletcad.app;

import java.util.Arrays;
import pl.tabletcad.app.model.*;

/** Parametric revolution: 22-value header, followed by the exact source profile. */
public final class Revolve {
    public static final int PREFIX=22;
    private Revolve() {}
    public static boolean is(double[] f){return f!=null&&f.length>=PREFIX&&f[0]==-3;}
    public static double[] create(SketchPrimitive shape,CadProject p,double[] axis,double angle,boolean cut) {
        if(shape==null||!shape.closedProfile())throw new IllegalArgumentException("Obrót wymaga zamkniętego profilu");
        if(axis==null||axis.length!=4)throw new IllegalArgumentException("Niepoprawna oś obrotu");
        double[] f={-3,cut?1:0,shape.x1,shape.y1,shape.x2,shape.y2,angle,
            p.sketchOriginX,p.sketchOriginY,p.sketchOriginZ,p.sketchUx,p.sketchUy,p.sketchUz,
            p.sketchVx,p.sketchVy,p.sketchVz,axis[0],axis[1],axis[2],axis[3],shape.featureType(),0};
        f=shape.appendVertices(f);validate(f);return f;
    }
    public static SketchPrimitive profile(String id,double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawny zapis obrotu");
        double type=f[20];
        if(type>=1002&&type<=1128&&type==Math.floor(type)&&f.length==PREFIX+((int)type-1000)*7)
            return new SketchPrimitive(id,Arrays.copyOfRange(f,PREFIX,f.length),true);
        if(type>=5&&type<=258&&type==Math.floor(type)&&f.length==PREFIX+((int)type-2)*2) {
            double[] points=Arrays.copyOfRange(f,PREFIX,f.length);SketchContours.validate(points);return new SketchPrimitive(id,points);
        }
        if((type!=0&&type!=1)||f.length!=PREFIX)throw new IllegalArgumentException("Niepoprawny profil obrotu");
        return new SketchPrimitive(id,type==1?"Okrąg":"Prostokąt",f[2],f[3],f[4],f[5]);
    }
    public static double[] withProfile(double[] f,SketchPrimitive shape) {
        if(shape==null||!shape.closedProfile())throw new IllegalArgumentException("Obrót wymaga zamkniętego profilu");
        double[] result=Arrays.copyOf(f,PREFIX);result[2]=shape.x1;result[3]=shape.y1;result[4]=shape.x2;result[5]=shape.y2;result[20]=shape.featureType();
        result=shape.appendVertices(result);validate(result);return result;
    }
    private static double side(double x,double y,double[] f) {
        double length=Math.hypot(f[18]-f[16],f[19]-f[17]);
        return ((x-f[16])*(f[19]-f[17])-(y-f[17])*(f[18]-f[16]))/length;
    }
    private static void add(double[] range,double value){range[0]=Math.min(range[0],value);range[1]=Math.max(range[1],value);}
    public static void validate(double[] f) {
        if(!is(f))throw new IllegalArgumentException("Niepoprawna operacja obrotu");
        for(double v:f)if(!Double.isFinite(v))throw new IllegalArgumentException("Parametry obrotu muszą być skończone");
        for(int i=2;i<=5;i++)if(Math.abs(f[i])>5000)throw new IllegalArgumentException("Profil poza zakresem szkicu");
        if((f[1]!=0&&f[1]!=1)||f[21]!=0||Math.abs(f[6])<.1||Math.abs(f[6])>360)
            throw new IllegalArgumentException("Kąt obrotu: od 0,1 do 360 stopni; kierunek może być odwrócony");
        double uu=0,vv=0,uv=0;for(int i=0;i<3;i++){uu+=f[10+i]*f[10+i];vv+=f[13+i]*f[13+i];uv+=f[10+i]*f[13+i];}
        if(Math.abs(uu-1)>1e-6||Math.abs(vv-1)>1e-6||Math.abs(uv)>1e-6)throw new IllegalArgumentException("Niepoprawna płaszczyzna obrotu");
        for(int i=16;i<=19;i++)if(Math.abs(f[i])>5000)throw new IllegalArgumentException("Oś poza zakresem szkicu");
        if(Math.hypot(f[18]-f[16],f[19]-f[17])<.01)throw new IllegalArgumentException("Oś obrotu musi być niezerową linią");
        SketchPrimitive p=profile("profile",f);double[] range={Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};
        if(p.curves!=null) {
            double dx=f[19]-f[17],dy=-(f[18]-f[16]),length=Math.hypot(dx,dy);dx/=length;dy/=length;
            for(int i=0;i<p.curves.length;i+=7) {
                add(range,side(p.curves[i+1],p.curves[i+2],f));add(range,side(p.curves[i+5],p.curves[i+6],f));
                if(p.curves[i]==1) {
                    SketchCurves.Arc a=SketchCurves.arc(p.curves,i);
                    for(int sign:new int[]{-1,1}){double x=a.x+sign*a.r*dx,y=a.y+sign*a.r*dy;if(a.contains(x,y))add(range,side(x,y,f));}
                }
            }
        }else if(p.vertices!=null)for(int i=0;i<p.vertices.length;i+=2)add(range,side(p.vertices[i],p.vertices[i+1],f));
        else if(p.type.equals("Okrąg")) {
            double r=Math.hypot(p.x2-p.x1,p.y2-p.y1),d=side(p.x1,p.y1,f);
            if(r<.01)throw new IllegalArgumentException("Promień profilu musi być dodatni");add(range,d-r);add(range,d+r);
        }else {
            if(Math.abs(p.x2-p.x1)<.01||Math.abs(p.y2-p.y1)<.01)throw new IllegalArgumentException("Profil musi mieć dodatnie pole");
            for(double x:new double[]{p.x1,p.x2})for(double y:new double[]{p.y1,p.y2})add(range,side(x,y,f));
        }
        if(range[0]<-1e-7&&range[1]>1e-7)throw new IllegalArgumentException("Oś przecina wnętrze profilu. Umieść profil po jednej stronie osi.");
    }
}
