package pl.tabletcad.app;

/** Editing a plane must not leave the profile behind opaque model surfaces. */
public final class SketchReadability {
    private SketchReadability() {}
    public static boolean bodyInFront(double[] mesh,double ox,double oy,double oz,double yaw,double pitch) {
        if(mesh.length%13!=0)throw new IllegalArgumentException("Invalid model mesh");
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch);
        double nx=-Math.sin(y)*Math.cos(p),ny=Math.sin(p),nz=Math.cos(y)*Math.cos(p);
        for(int i=0;i<mesh.length;i+=13)for(int j=0;j<9;j+=3)
            if((mesh[i+j]-ox)*nx+(mesh[i+j+1]-oy)*ny+(mesh[i+j+2]-oz)*nz>1e-4)return true;
        return false;
    }
}
