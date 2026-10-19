import java.util.*;
import pl.tabletcad.app.ViewCube;

/** 0.84 navigation cube: faces follow the camera and a tap selects the matching standard view. */
public final class ViewCubeHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    static Set<String> names(List<ViewCube.Projected> faces){Set<String> s=new HashSet<>();for(ViewCube.Projected f:faces)s.add(f.face.view);return s;}
    public static void main(String[] args) {
        // Standard views of the workspace: each shows its own face to the viewer.
        Object[][] views={{"Przód",0.0,0.0},{"Tył",180.0,0.0},{"Góra",0.0,90.0},{"Dół",0.0,-90.0},{"Prawy bok",-90.0,0.0},{"Lewy bok",90.0,0.0}};
        for(Object[] v:views) {
            List<ViewCube.Projected> faces=ViewCube.visible((double)v[1],(double)v[2],100,100,20);
            check(faces.size()==1&&faces.get(0).face.view.equals(v[0]),v[0]+" view shows only its face");
            check(v[0].equals(ViewCube.hit((double)v[1],(double)v[2],100,100,20,100,100)),"tap in the middle selects "+v[0]);
        }
        double iso=Math.toDegrees(Math.atan(1/Math.sqrt(2)));
        List<ViewCube.Projected> faces=ViewCube.visible(-45,iso,100,100,20);
        check(names(faces).equals(new HashSet<>(Arrays.asList("Przód","Góra","Prawy bok"))),"isometric view shows front, top and right faces");
        for(ViewCube.Projected f:faces)check(f.face.view.equals(ViewCube.hit(-45,iso,100,100,20,f.cx,f.cy)),"tap on the "+f.face.view+" face selects it");
        ViewCube.Projected top=null;for(ViewCube.Projected f:faces)if(f.face.view.equals("Góra"))top=f;
        check(top.cy<100,"top face is drawn above the centre (screen Y down)");
        double[] x=ViewCube.project(-45,iso,new double[]{1,0,0});check(x[0]>0,"+X points to the right in the default view");
        check(ViewCube.hit(-45,iso,100,100,20,10,10)==null,"tap outside the cube is not consumed");
        // Face-on view: the outer band of the face leads to the neighbouring faces.
        check("Prawy bok".equals(ViewCube.hit(0,0,100,100,20,117,100)),"front view: right edge band turns to the right side");
        check("Góra".equals(ViewCube.hit(0,0,100,100,20,100,83)),"front view: top edge band turns to the top");
        check("Lewy bok".equals(ViewCube.hit(0,0,100,100,20,83,100)),"front view: left edge band turns to the left side");
        check("Przód".equals(ViewCube.hit(0,90,100,100,20,100,117)),"top view: bottom edge band turns to the front");
        System.out.println("PASS ViewCubeHostTest: "+checks+" checks");
    }
}
