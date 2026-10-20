package pl.tabletcad.app;

import pl.tabletcad.app.model.SketchPrimitive;

/** Exact planar similarities; analytic circles and arcs remain analytic. Identity is preserved. */
public final class SketchTransform {
    private SketchTransform() {}
    public static SketchPrimitive move(SketchPrimitive source,double dx,double dy) {
        return finish(source,SketchPattern.transform(source,source.id,0,0,0,dx,dy),1,0);
    }
    public static SketchPrimitive rotate(SketchPrimitive source,double degrees,double cx,double cy) {
        if(!Double.isFinite(degrees)||Math.abs(degrees)>36000)throw new IllegalArgumentException("Kąt obrotu: ±36000°");
        return finish(source,SketchPattern.transform(source,source.id,cx,cy,Math.toRadians(degrees),0,0),1,degrees);
    }
    public static SketchPrimitive scale(SketchPrimitive source,double factor,double cx,double cy) {
        if(!Double.isFinite(factor)||factor<.001||factor>1000)throw new IllegalArgumentException("Skala: 0,001–1000");
        if(!Double.isFinite(cx)||!Double.isFinite(cy)||Math.abs(cx)>5000||Math.abs(cy)>5000)throw new IllegalArgumentException("Niepoprawny środek skali");
        SketchPrimitive result;
        if(source.isEllipse())result=SketchPrimitive.ellipse(source.id,cx+(source.x1-cx)*factor,cy+(source.y1-cy)*factor,cx+(source.x2-cx)*factor,cy+(source.y2-cy)*factor,source.minor*factor);
        else if(source.curves!=null) {
            double[] data=source.curves.clone();
            for(int i=0;i<data.length;i+=7)for(int j:new int[]{1,3,5}) {
                if(j==3&&data[i]==0)continue;
                data[i+j]=cx+(data[i+j]-cx)*factor;data[i+j+1]=cy+(data[i+j+1]-cy)*factor;
            }
            result=new SketchPrimitive(source.id,data,source.closedGeometry());
        }else if(source.vertices!=null) {
            double[] data=source.vertices.clone();for(int i=0;i<data.length;i+=2){data[i]=cx+(data[i]-cx)*factor;data[i+1]=cy+(data[i+1]-cy)*factor;}
            result=SketchSpline.is(source)?new SketchPrimitive(source.id,data,"Splajn"):new SketchPrimitive(source.id,data);
        }else result=new SketchPrimitive(source.id,source.type,cx+(source.x1-cx)*factor,cy+(source.y1-cy)*factor,cx+(source.x2-cx)*factor,cy+(source.y2-cy)*factor);
        return finish(source,result,factor,0);
    }
    private static SketchPrimitive finish(SketchPrimitive source,SketchPrimitive result,double scale,double degrees) {
        double[] bounds=result.curves!=null?SketchCurves.bounds(result.curves):new double[]{result.x1,result.y1,result.x2,result.y2};
        if(result.isEllipse()){double r=result.major();bounds=new double[]{result.x1-r,result.y1-r,result.x1+r,result.y1+r};}
        if(result.type.equals("Okrąg")){double radius=SketchDimensions.length(result);if(radius<.01)throw new IllegalArgumentException("Promień musi wynosić co najmniej 0,01 mm");bounds=new double[]{result.x1-radius,result.y1-radius,result.x1+radius,result.y1+radius};}
        for(double value:bounds)if(!Double.isFinite(value)||Math.abs(value)>5000)throw new IllegalArgumentException("Geometria poza zakresem ±5000 mm");
        if(result.vertices!=null&&!SketchSpline.is(result))SketchContours.validate(result.vertices);
        if(result.curves==null&&result.vertices==null&&!result.isPoint()&&SketchDimensions.length(result)<.01)throw new IllegalArgumentException("Element jest zbyt mały");
        if(result.type.equals("Prostokąt")&&(Math.abs(result.x2-result.x1)<.01||Math.abs(result.y2-result.y1)<.01))throw new IllegalArgumentException("Boki prostokąta muszą wynosić co najmniej 0,01 mm");
        if(source.driving!=null) {
            if(!source.type.equals(result.type)&&!(source.type.equals("Pomocnicza")&&result.type.equals("Linia")))throw new IllegalArgumentException("Przed obrotem prostokąta do konturu odblokuj jego wymiary");
            double[] driving=source.driving.clone();driving[0]*=scale;
            if(driving.length>1)driving[1]=source.type.equals("Prostokąt")?driving[1]*scale:Math.IEEEremainder(driving[1]+degrees,360);
            // A quarter-turn swaps rectangle width and height.
            if(source.type.equals("Prostokąt")){driving[0]=Math.abs(result.x2-result.x1);driving[1]=Math.abs(result.y2-result.y1);}
            result=new SketchPrimitive(result.id,result.type,result.x1,result.y1,result.x2,result.y2,driving);
        }
        return result.withConstruction(source.construction);
    }
}
