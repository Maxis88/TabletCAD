import pl.tabletcad.app.ExtrusionSign;

/** The on-model extrusion value keeps its sign relative to the sketch plane (below = minus), also for cuts. */
public final class ExtrusionSignHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    public static void main(String[] args){
        java.util.function.DoubleFunction<String> mm=v->String.format(java.util.Locale.US,"%.2f mm",v);
        check(ExtrusionSign.label(20,1,false,mm).equals("20.00 mm"),"join, arrow up: +20");
        check(ExtrusionSign.label(-20,1,false,mm).equals("−20.00 mm"),"join, arrow down below the plane: −20");
        check(ExtrusionSign.label(20,-1,false,mm).equals("−20.00 mm"),"cut into the material (below the plane): −20, not +20");
        check(ExtrusionSign.label(-20,-1,false,mm).equals("20.00 mm"),"cut reversed (above the plane): +20");
        check(ExtrusionSign.label(30,-1,true,mm).equals("↕ 30.00 mm"),"symmetric: total length, no sign");
        check(ExtrusionSign.relativeToPlane(-5,1,false)==-5&&ExtrusionSign.relativeToPlane(5,-1,false)==-5,"relative depth below the plane is negative");
        System.out.println("PASS ExtrusionSignHostTest: "+checks+" checks");
    }
}
