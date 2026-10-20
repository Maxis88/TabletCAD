import pl.tabletcad.app.StylusFilter;

/** 0.92 stylus and palm rejection decisions. */
public final class StylusHostTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("PASS "+text);}
    public static void main(String[] args) {
        StylusFilter f=new StylusFilter();
        check(f.down(StylusFilter.FINGER,8,0),"before any stylus a finger works normally");
        f.up(StylusFilter.FINGER,10);
        check(f.down(StylusFilter.FINGER,30,20),"before any stylus even a large contact is accepted (no stylus user)");
        f.up(StylusFilter.FINGER,30);
        check(f.down(StylusFilter.STYLUS,1,100)&&f.stylusDown(),"stylus stroke accepted");
        check(!f.pointerDown(StylusFilter.FINGER),"finger joining the stylus stroke is ignored");
        check(!f.down(StylusFilter.FINGER,8,150),"palm touching while the stylus writes is rejected");
        f.up(StylusFilter.FINGER,160);
        f.up(StylusFilter.STYLUS,200);
        check(!f.down(StylusFilter.FINGER,8,200+StylusFilter.PALM_GRACE_MS-1),"palm right after the stylus lifted is rejected");
        f.up(StylusFilter.FINGER,700);
        check(f.down(StylusFilter.FINGER,8,2000),"a fingertip later is accepted (camera, menus)");
        f.up(StylusFilter.FINGER,2100);
        check(!f.down(StylusFilter.FINGER,25,3000),"after stylus use a palm-sized contact is rejected");
        f.up(StylusFilter.FINGER,3100);
        check(StylusFilter.pickScale(StylusFilter.STYLUS)<StylusFilter.pickScale(StylusFilter.FINGER),"stylus picks with a smaller radius");
        check(f.down(StylusFilter.ERASER,1,5000),"eraser end counts as stylus");
        System.out.println("PASS StylusHostTest: "+checks+" checks");
    }
}
