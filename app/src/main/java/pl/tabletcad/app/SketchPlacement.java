package pl.tabletcad.app;

import pl.tabletcad.app.model.CadProject;

/** Display placement of a source profile. Source geometry stays in its historical frame. */
public final class SketchPlacement {
    private SketchPlacement() {}
    public static double[] identity(){return new double[]{1,0,0,0,0,1,0,0,0,0,1,0};}
    public static double[] point(double[] m,double x,double y,double z){return new double[]{m[0]*x+m[1]*y+m[2]*z+m[3],m[4]*x+m[5]*y+m[6]*z+m[7],m[8]*x+m[9]*y+m[10]*z+m[11]};}
    private static double[] apply(double[] f,double x,double y,double z) {
        if(f[1]==BodyTransform.MOVE)return new double[]{x+f[2],y+f[3],z+f[4]};
        if(f[1]==BodyTransform.SCALE)return new double[]{f[2]+(x-f[2])*f[8],f[3]+(y-f[3])*f[8],f[4]+(z-f[4])*f[8]};
        double[] instance=BodyInstance.create(2,new double[3],new double[]{f[5],f[6],f[7]},f[8],new double[]{f[2],f[3],f[4]});
        return BodyInstance.apply(instance,x,y,z);
    }
    /** Only transforms after the profile's consuming feature affect its displayed placement. */
    public static double[] of(CadProject model,String profile,int body) {
        int born=Integer.MAX_VALUE;
        if(body==1&&profile!=null&&profile.equals(model.featureBaseProfileId))born=-1;
        for(int i=0;i<model.committedProfileIds.size();i++)if(profile!=null&&profile.equals(model.committedProfileIds.get(i))) {
            int output=i<model.featureBodyOutputs.size()?model.featureBodyOutputs.get(i):1;
            if(output==body)born=Math.min(born,i);
        }
        double[] m=identity();if(born==Integer.MAX_VALUE||body<=0)return m;
        for(int i=born+1;i<model.committedFeatures.size();i++) {
            double[] f=model.committedFeatures.get(i);
            if(!BodyTransform.is(f)||FeatureHistory.inactive(model,i))continue;
            int target=i<model.featureBodyTargets.size()?model.featureBodyTargets.get(i):-1;
            if(target>=0&&target!=body)continue;
            double[] o=point(m,0,0,0),x=point(m,1,0,0),y=point(m,0,1,0),z=point(m,0,0,1);
            o=apply(f,o[0],o[1],o[2]);x=apply(f,x[0],x[1],x[2]);y=apply(f,y[0],y[1],y[2]);z=apply(f,z[0],z[1],z[2]);
            m=new double[]{x[0]-o[0],y[0]-o[0],z[0]-o[0],o[0],x[1]-o[1],y[1]-o[1],z[1]-o[1],o[1],x[2]-o[2],y[2]-o[2],z[2]-o[2],o[2]};
        }
        return m;
    }
    public static void frame(CadProject display,double[] m) {
        double[] o=point(m,display.sketchOriginX,display.sketchOriginY,display.sketchOriginZ);
        double[] u=point(m,display.sketchOriginX+display.sketchUx,display.sketchOriginY+display.sketchUy,display.sketchOriginZ+display.sketchUz);
        double[] v=point(m,display.sketchOriginX+display.sketchVx,display.sketchOriginY+display.sketchVy,display.sketchOriginZ+display.sketchVz);
        display.sketchOriginX=o[0];display.sketchOriginY=o[1];display.sketchOriginZ=o[2];
        display.sketchUx=u[0]-o[0];display.sketchUy=u[1]-o[1];display.sketchUz=u[2]-o[2];
        display.sketchVx=v[0]-o[0];display.sketchVy=v[1]-o[1];display.sketchVz=v[2]-o[2];
    }
}
