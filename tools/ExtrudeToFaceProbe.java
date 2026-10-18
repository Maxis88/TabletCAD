import pl.tabletcad.app.ExtrudeToFace;

/** "Do powierzchni": exact plane offset, parallel check and the direction flag per operation. */
public final class ExtrudeToFaceProbe {
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
    static void rejects(double[] o,double[] u,double[] v,double[] pts,String text){try{ExtrudeToFace.distance(o,u,v,pts);}catch(IllegalArgumentException e){System.out.println("PASS "+text+": "+e.getMessage());return;}throw new AssertionError(text);}
    public static void main(String[] args) {
        double[] o={0,0,20},u={1,0,0},v={0,1,0};
        double[] bottom={0,0,0,100,0,0,100,60,0,0,60,0};
        double d=ExtrudeToFace.distance(o,u,v,bottom);check(d==-20,"top-face sketch to bottom face is -20 along +Z ("+d+")");
        check(!ExtrudeToFace.reverse(d,true),"cut towards the bottom keeps the default direction");
        check(ExtrudeToFace.reverse(d,false),"add towards the bottom needs reverse");
        double[] above={0,0,45.75,10,0,45.75,10,10,45.75};
        d=ExtrudeToFace.distance(o,u,v,above);check(Math.abs(d-25.75)<1e-12&&!ExtrudeToFace.reverse(d,false),"face 25.75 mm above, add goes forward ("+d+")");
        check(ExtrudeToFace.format(d).equals("25.75")&&ExtrudeToFace.format(-125.123456789).equals("125.123457"),"exact decimal text without display rounding to 3 places");
        // Sketch on XZ (U=X, V=Z, normal -Y): face at y=+30 is a negative distance.
        d=ExtrudeToFace.distance(new double[]{0,0,0},new double[]{1,0,0},new double[]{0,0,1},new double[]{0,30,0,5,30,0,5,30,5});
        check(d==-30,"XZ sketch normal is U×V = -Y ("+d+")");
        rejects(o,u,v,new double[]{0,0,0,100,0,0,100,0,20},"side face not parallel to the sketch is rejected");
        rejects(o,u,v,new double[]{0,0,20,1,0,20,1,1,20},"coplanar face is rejected");
        rejects(o,u,v,new double[]{0,0,6000,1,0,6000,1,1,6000},"face beyond 5000 mm rejected");
        System.out.println("ALL EXTRUDE TO FACE TESTS PASSED");
    }
}
