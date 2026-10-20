package pl.tabletcad.app;

/** Face colour of the shaded model from its camera-space normal (pure logic, testable). */
public final class ShadeModel {
    private ShadeModel() {}
    static final double[] KEY=normalize(-0.45,0.6,0.66),FILL=normalize(0.6,-0.2,0.77);
    static double[] normalize(double x,double y,double z){double l=Math.sqrt(x*x+y*y+z*z);return new double[]{x/l,y/l,z/l};}
    /** Brightness 0…1: ambient + key light + weak fill (two-sided, faces seen from behind use the flipped normal). */
    public static double intensity(double nx,double ny,double nz) {
        if(nz<0){nx=-nx;ny=-ny;nz=-nz;}
        double key=Math.max(0,nx*KEY[0]+ny*KEY[1]+nz*KEY[2]),fill=Math.max(0,nx*FILL[0]+ny*FILL[1]+nz*FILL[2]);
        return Math.min(1,0.22+0.62*key+0.22*fill);
    }
    /** ARGB colour: from a dark slate teal in shadow to an almost white teal in full light. */
    public static int color(double nx,double ny,double nz) {
        double t=intensity(nx,ny,nz);
        int r=(int)Math.round(70+(232-70)*t),g=(int)Math.round(98+(245-98)*t),b=(int)Math.round(104+(243-104)*t);
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }
}
