import pl.tabletcad.app.*;

/** Sliders are not capped at fixed values: lengths widen to the typed or dragged value, angles stay fixed. */
public final class SliderRangeHostTest {
    static int checks;
    static void check(boolean ok,String t){if(!ok)throw new AssertionError(t);checks++;System.out.println("PASS "+t);}
    public static void main(String[] a) {
        SliderRange r=new SliderRange(.1,10,SliderRange.angleLike(.1,10),5000);
        check(!r.fixed,"fillet radius range is not fixed");
        check(r.include(25)&&r.max>=25,"typed R25 widens the range ("+r.max+")");
        double before=r.max;check(r.released(r.max)&&r.max==Math.min(5000,before*2),"thumb released at the end doubles the range");
        check(!r.include(3),"value inside the range changes nothing");
        SliderRange d=new SliderRange(-100,100,false,5000);check(d.include(-350)&&d.min<=-350,"negative side widens too");
        for(int i=0;i<40;i++)d.released(d.max);check(d.max==5000,"lengths stop at ±5000 mm");
        SliderRange ang=new SliderRange(-180,180,SliderRange.angleLike(-180,180),5000);check(ang.fixed&&!ang.include(400)&&ang.max==180,"angle ±180 stays fixed");
        check(SliderRange.angleLike(1,360)&&SliderRange.angleLike(-45,45)&&!SliderRange.angleLike(.1,10),"angle ranges recognised");
        System.out.println("PASS SliderRangeHostTest: "+checks+" checks");
    }
}
