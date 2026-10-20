import pl.tabletcad.app.FacePick;

/** 1.0.0-rc4: the touched point on a face, and occlusion of sketches behind the solid. */
public final class FacePickHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    public static void main(String[] args){
        // Triangle (0,0,0)-(100,0,0)-(0,100,0) seen orthographically 1:1 (q equal).
        float[] sx={0,100,0},sy={0,0,100};double[] q={1e-3,1e-3,1e-3};double[] wx={0,100,0},wy={0,0,100},wz={0,0,0};
        double[] h=FacePick.hit(sx,sy,q,wx,wy,wz,20,30);
        check(Math.abs(h[0]-20)<1e-9&&Math.abs(h[1]-30)<1e-9,"touched point, not the triangle centre (33,33)");
        // Perspective: far vertex has smaller q; the screen midpoint maps nearer to the near vertex in world space.
        double[] qp={1,1,.5};float[] px={0,100,0},py={0,0,100};double[] w2={0,0,0},w2y={0,0,100},w2z={0,0,0};
        double[] hp=FacePick.hit(px,py,qp,w2,w2y,w2z,0,50);
        check(Math.abs(hp[1]-100.0/3)<1e-9,"perspective-correct interpolation (y = 33.3, not 50)");
        check(FacePick.behind(1/120.0,1/100.0,.05),"a sketch 20 mm behind the face is hidden");
        check(!FacePick.behind(1/100.02,1/100.0,.05),"a sketch on the face itself stays pickable");
        double[] out=FacePick.hit(sx,sy,q,wx,wy,wz,-5,-5);check(out[0]>=0&&out[1]>=0,"points just outside are clamped onto the face");
        System.out.println("PASS FacePickHostTest: "+checks+" checks");
    }
}
