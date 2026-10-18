import org.json.*;
import pl.tabletcad.app.*;
import pl.tabletcad.app.model.*;

/** Unit conversion, expression entry and persistence of project units (0.73). */
public final class UnitsHostTest {
    interface Work{void run()throws Exception;}
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static void near(double a,double b,String label){check(Math.abs(a-b)<1e-12*Math.max(1,Math.abs(b)),label+" ("+a+")");}
    static void rejects(Work w,String label)throws Exception{try{w.run();}catch(IllegalArgumentException|JSONException e){check(true,label+" → "+e.getMessage());return;}throw new AssertionError(label);}
    public static void main(String[] args)throws Exception {
        near(Units.parseLength("125,75","mm"),125.75,"decimal comma");
        near(Units.parseLength("125.75","mm"),125.75,"decimal point");
        near(Units.parseLength("6,5","mm"),6.5,"hole diameter 6,5 mm");
        near(Units.parseLength("100/3","mm"),100.0/3,"division keeps full precision");
        near(Units.parseLength("2*(10+2,5)","mm"),25,"parentheses and product");
        near(Units.parseLength("1in","mm"),25.4,"inch suffix");
        near(Units.parseLength("1\"","mm"),25.4,"inch mark");
        near(Units.parseLength("1in+2mm","mm"),27.4,"mixed units");
        near(Units.parseLength("10mm+5","cm"),60,"plain number added to a length uses the project unit");
        near(Units.parseLength("2*10mm","in"),20,"number times length is a length");
        near(Units.parseLength("1.2","cm"),12,"project unit cm");
        near(Units.parseLength("0.125","m"),125,"project unit m");
        near(Units.parseLength("0.5","in"),12.7,"project unit in");
        near(Units.parseLength("-3","mm"),-3,"unary minus");
        near(Units.parseLength(" 1e-3 m ","mm"),1,"scientific notation and spaces");
        near(Units.parseLength("(1+1)cm","mm"),20,"unit after a group");
        near(Units.parseNumber("360/7"),360.0/7,"plain number field");
        near(Units.parseNumber("6"),6,"integer field");
        rejects(()->Units.parseLength("10mm*10mm","mm"),"area is not a length");
        rejects(()->Units.parseLength("10/0","mm"),"division by zero");
        rejects(()->Units.parseLength("1..2","mm"),"malformed number");
        rejects(()->Units.parseLength("abc","mm"),"letters rejected");
        rejects(()->Units.parseLength("","mm"),"empty field");
        rejects(()->Units.parseLength("(2+3","mm"),"missing parenthesis");
        rejects(()->Units.parseNumber("5mm"),"units rejected in a plain number field");
        rejects(()->Units.parseLength("5mmcm","mm"),"double unit rejected");
        check(Units.length(125.75,"mm",2).equals("125.75 mm"),"display mm");
        check(Units.length(125.75,"cm",3).equals("12.575 cm"),"display cm");
        check(Units.length(25.4,"in",3).equals("1.000 in"),"display in");
        check(Units.length(-0.0004,"mm",3).equals("0.000 mm"),"no negative zero");
        check(Units.input(12.7,"in").equals("0.5"),"input text trims zeros");
        near(Units.parseLength(Units.input(100.0/3,"mm"),"mm"),33.333,"input text round trip at 0.001 mm");
        check(Units.label("Głębokość [mm]","in").equals("Głębokość [in]"),"labels follow the unit");
        check(Units.area(100,"cm",1).equals("1.0 cm²")&&Units.volume(1e6,"m",6).equals("0.001000 m³"),"area and volume conversion");

        CadProject p=new CadProject();p.units="in";p.displayDecimals=4;
        CadProject loaded=CadProject.fromJson(new JSONObject(p.toJson().toString()));
        check(loaded.units.equals("in")&&loaded.displayDecimals==4,"units and precision persist");
        JSONObject old=new JSONObject(p.toJson().toString());old.remove("displayDecimals");
        check(CadProject.fromJson(old).displayDecimals==3,"older documents keep the previous 3-decimal display");
        JSONObject bad=new JSONObject(p.toJson().toString());bad.put("units","ft");rejects(()->CadProject.fromJson(bad),"unknown unit refused");
        JSONObject places=new JSONObject(p.toJson().toString());places.put("displayDecimals",9);rejects(()->CadProject.fromJson(places),"precision limited to 6 places");
        System.out.println("PASS UnitsHostTest: "+checks+" checks");
    }
}
