package pl.tabletcad.app;

import java.util.*;
import pl.tabletcad.app.model.*;

/** Extents are measured from the source plane; the packed B-Rep tool stores its actual start. */
public final class ExtrusionPlan {
    private ExtrusionPlan() {}
    public static boolean placedBase(double[] f){return f!=null&&f.length>=23&&f[0]==-10;}
    public static double[] feature(CadProject p,SketchPrimitive profile,String operation,double first,double second,boolean reverse,boolean symmetric) {
        double signed=FeatureDirection.signed(first,reverse,symmetric);
        if(!Double.isFinite(second)||second<0||second>5000||first+second>5000||symmetric&&second!=0)throw new IllegalArgumentException("Niepoprawny zakres wyciągania");
        double[] f=profile.appendVertices(new double[]{profile.featureType(),FeatureDirection.featureCode(operation,symmetric),profile.x1,profile.y1,profile.x2,profile.y2,Math.copySign(first+second,signed),p.sketchOriginX,p.sketchOriginY,p.sketchOriginZ,p.sketchUx,p.sketchUy,p.sketchUz,p.sketchVx,p.sketchVy,p.sketchVz});
        double sign=(operation.equals("cut")?-1:1)*Math.signum(signed);
        double[] normal={p.sketchUy*p.sketchVz-p.sketchUz*p.sketchVy,p.sketchUz*p.sketchVx-p.sketchUx*p.sketchVz,p.sketchUx*p.sketchVy-p.sketchUy*p.sketchVx};
        for(int i=0;i<3;i++)f[7+i]-=normal[i]*sign*second;return f;
    }
    public static double[] base(CadProject p,SketchPrimitive profile,double first,double second,boolean reverse,boolean symmetric) {
        int plane=p.sketchPlane.equals("XZ")?1:p.sketchPlane.equals("YZ")?2:0;
        if(second==0&&!p.sketchPlane.startsWith("Ściana"))return profile.appendVertices(new double[]{profile.featureType(),profile.x1,profile.y1,profile.x2,profile.y2,FeatureDirection.baseCode(plane,symmetric),FeatureDirection.signed(first,reverse,symmetric)});
        double[] feature=feature(p,profile,"new",first,second,plane==1?!reverse:reverse,symmetric),result=new double[7+feature.length];
        result[0]=-10;result[1]=FeatureDirection.signed(first,reverse,symmetric);result[2]=second;result[6]=Math.copySign(first+second,result[1]);System.arraycopy(feature,0,result,7,feature.length);validateBase(result);return result;
    }
    public static void validateBase(double[] f) {
        if(!placedBase(f)||!Double.isFinite(f[1])||Math.abs(f[1])<.1||f[2]<0||f[2]>5000||Math.abs(Math.abs(f[6])-Math.abs(f[1])-f[2])>1e-6)throw new IllegalArgumentException("Niepoprawne dwukierunkowe wyciągnięcie bazowe");
        for(double value:f)if(!Double.isFinite(value))throw new IllegalArgumentException("Niepoprawne parametry wyciągania");
        if(!FeatureDirection.operation(f[8]).equals("new"))throw new IllegalArgumentException("Baza musi tworzyć nową bryłę");
        double[] inner=Arrays.copyOfRange(f,7,f.length);if(SketchRegion.is(inner))SketchRegion.validate(inner);
        SketchPrimitive.fromFeature("validate","Prostokąt",inner,2,16);
    }
    public static SketchPrimitive baseProfile(String id,double[] f){return SketchPrimitive.fromFeature(id,f[7]==1?"Okrąg":"Prostokąt",Arrays.copyOfRange(f,7,f.length),2,16);}
    public static double[] withProfile(double[] base,SketchPrimitive shape) {
        double[] header=Arrays.copyOf(base,23);header[7]=shape.featureType();header[9]=shape.x1;header[10]=shape.y1;header[11]=shape.x2;header[12]=shape.y2;
        double[] result=shape.appendVertices(header);validateBase(result);return result;
    }
    public static double[] resizeFeature(double[] feature,double oldSecond,double first,double second,boolean reverse,boolean symmetric) {
        double[] f=feature.clone();double oldSign=(FeatureDirection.isCut(f[1])?-1:1)*Math.signum(f[6]);
        double[] normal={f[11]*f[15]-f[12]*f[14],f[12]*f[13]-f[10]*f[15],f[10]*f[14]-f[11]*f[13]};
        double signed=FeatureDirection.signed(first,reverse,symmetric),sign=(FeatureDirection.isCut(f[1])?-1:1)*Math.signum(signed);
        if(!Double.isFinite(second)||second<0||second>5000||symmetric&&second!=0)throw new IllegalArgumentException("Niepoprawny drugi kierunek");
        for(int i=0;i<3;i++)f[7+i]+=normal[i]*(oldSign*oldSecond-sign*second);
        f[6]=Math.copySign(first+second,signed);f[1]=FeatureDirection.withSymmetric(f[1],symmetric);return f;
    }
}
