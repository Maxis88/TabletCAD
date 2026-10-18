package pl.tabletcad.app;

import java.util.Locale;

/**
 * Project length units and exact value entry. Geometry is always stored in millimetres;
 * units only change how values are shown and how typed text is interpreted.
 *
 * <p>Typed values may be arithmetic expressions with + − × ÷ ( ) and a decimal comma or
 * point, e.g. {@code 125,75}, {@code 100/3}, {@code 2*(10+2.5)}, {@code 1in + 2mm}.
 * A number without a unit uses the project unit. Explicit units: mm, cm, m, in (also ″ or ").
 * Products and quotients are dimension-checked, so {@code 2*10mm} is a length while
 * {@code 10mm*10mm} is rejected for a length field.</p>
 */
public final class Units {
    public static final String[] CODES={"mm","cm","m","in"};
    public static final String[] NAMES={"Milimetry (mm)","Centymetry (cm)","Metry (m)","Cale (in)"};
    private Units() {}

    public static boolean valid(String unit){for(String c:CODES)if(c.equals(unit))return true;return false;}
    /** Millimetres per one unit. */
    public static double factor(String unit) {
        switch(unit==null?"mm":unit) {
            case "mm":return 1;
            case "cm":return 10;
            case "m":return 1000;
            case "in":return 25.4;
            default:throw new IllegalArgumentException("Nieznana jednostka: "+unit);
        }
    }
    public static void validateDecimals(int decimals){if(decimals<0||decimals>6)throw new IllegalArgumentException("Dokładność wyświetlania: od 0 do 6 miejsc po przecinku");}

    /** Value in the given unit, rounded only for display. */
    public static String number(double mm,String unit,int decimals) {
        validateDecimals(decimals);
        String text=String.format(Locale.US,"%."+decimals+"f",mm/factor(unit));
        if(text.startsWith("-")&&Double.parseDouble(text)==0)text=text.substring(1);
        return text;
    }
    public static String length(double mm,String unit,int decimals){return number(mm,unit,decimals)+" "+unit;}
    public static String area(double mm2,String unit,int decimals){double f=factor(unit);return String.format(Locale.US,"%."+decimals+"f %s²",mm2/(f*f),unit);}
    public static String volume(double mm3,String unit,int decimals){double f=factor(unit);return String.format(Locale.US,"%."+decimals+"f %s³",mm3/(f*f*f),unit);}
    /** Input text: enough digits that re-reading it does not lose a typed 0.001 mm step. */
    public static String input(double mm,String unit) {
        int digits=unit.equals("mm")?3:unit.equals("cm")?4:unit.equals("m")?6:5;
        String text=number(mm,unit,digits);
        if(text.contains(".")){text=text.replaceAll("0+$","");if(text.endsWith("."))text=text.substring(0,text.length()-1);}
        return text;
    }
    /** Replaces the "[mm]" marker of a label by the project unit. */
    public static String label(String text,String unit){return text==null?null:text.replace("[mm]","["+unit+"]");}

    /** Parses a length (expression) and returns millimetres. */
    public static double parseLength(String text,String unit) {
        Value v=new Parser(text,factor(unit)).parse();
        if(v.power==0)return v.value*factor(unit);
        if(v.power==1)return v.value;
        throw new IllegalArgumentException("Wynik wyrażenia nie jest długością");
    }
    /** Parses a plain number (angle, count, factor); units are not allowed. */
    public static double parseNumber(String text) {
        Value v=new Parser(text,1).parse();
        if(v.power!=0)throw new IllegalArgumentException("To pole nie przyjmuje jednostek długości");
        return v.value;
    }

    /** Magnitude in mm^power; power 0 is a pure number. */
    private static final class Value {
        final double value;final int power;
        Value(double value,int power){this.value=value;this.power=power;}
    }
    private static final class Parser {
        private final String s;private final double unitFactor;private int at;
        Parser(String text,double unitFactor) {
            if(text==null)throw new IllegalArgumentException("Wpisz wartość");
            String t=text.trim().replace('−','-').replace('×','*').replace('·','*').replace('÷','/').replace('″','"');
            // A single comma is a decimal separator (Polish notation); commas are never operators.
            this.s=t.replace(',','.');this.unitFactor=unitFactor;
            if(s.isEmpty())throw new IllegalArgumentException("Wpisz wartość");
        }
        Value parse() {
            Value v=sum();skip();
            if(at!=s.length())throw new IllegalArgumentException("Niepoprawne wyrażenie przy „"+s.substring(at)+"”");
            if(!Double.isFinite(v.value))throw new IllegalArgumentException("Wynik wyrażenia nie jest skończony");
            return v;
        }
        private void skip(){while(at<s.length()&&Character.isWhitespace(s.charAt(at)))at++;}
        private boolean eat(char c){skip();if(at<s.length()&&s.charAt(at)==c){at++;return true;}return false;}
        private Value sum() {
            Value left=product();
            while(true) {
                if(eat('+'))left=add(left,product(),1);
                else if(eat('-'))left=add(left,product(),-1);
                else return left;
            }
        }
        private Value add(Value a,Value b,int sign) {
            // A plain number added to a length is taken in the project unit (10mm + 5).
            if(a.power==b.power)return new Value(a.value+sign*b.value,a.power);
            if(a.power==1&&b.power==0)return new Value(a.value+sign*b.value*unitFactor,1);
            if(a.power==0&&b.power==1)return new Value(a.value*unitFactor+sign*b.value,1);
            throw new IllegalArgumentException("Nie można dodawać wielkości o różnych wymiarach");
        }
        private Value product() {
            Value left=unary();
            while(true) {
                if(eat('*')){Value r=unary();left=new Value(left.value*r.value,left.power+r.power);}
                else if(eat('/')){Value r=unary();if(r.value==0)throw new IllegalArgumentException("Dzielenie przez zero");left=new Value(left.value/r.value,left.power-r.power);}
                else return left;
            }
        }
        private Value unary() {
            if(eat('-')){Value v=unary();return new Value(-v.value,v.power);}
            if(eat('+'))return unary();
            return primary();
        }
        private Value primary() {
            skip();
            if(eat('(')){Value v=sum();if(!eat(')'))throw new IllegalArgumentException("Brak nawiasu zamykającego");return withUnit(v);}
            int start=at;
            while(at<s.length()&&(Character.isDigit(s.charAt(at))||s.charAt(at)=='.'))at++;
            if(at<s.length()&&(s.charAt(at)=='e'||s.charAt(at)=='E')&&at+1<s.length()&&(Character.isDigit(s.charAt(at+1))||((s.charAt(at+1)=='-'||s.charAt(at+1)=='+')&&at+2<s.length()&&Character.isDigit(s.charAt(at+2))))) {
                at+=2;while(at<s.length()&&Character.isDigit(s.charAt(at)))at++;
            }
            if(start==at)throw new IllegalArgumentException(at<s.length()?"Niepoprawny znak „"+s.charAt(at)+"”":"Niekompletne wyrażenie");
            String literal=s.substring(start,at);
            if(literal.chars().filter(c->c=='.').count()>1)throw new IllegalArgumentException("Niepoprawna liczba „"+literal+"”");
            double value;
            try{value=Double.parseDouble(literal);}catch(NumberFormatException e){throw new IllegalArgumentException("Niepoprawna liczba „"+literal+"”");}
            return withUnit(new Value(value,0));
        }
        private Value withUnit(Value v) {
            skip();
            for(String unit:new String[]{"mm","cm","in","m","\""}) {
                if(s.startsWith(unit,at)&&(at+unit.length()==s.length()||!Character.isLetter(s.charAt(at+unit.length())))) {
                    if(v.power!=0)throw new IllegalArgumentException("Podwójna jednostka w wyrażeniu");
                    at+=unit.length();return new Value(v.value*factor(unit.equals("\"")?"in":unit),1);
                }
            }
            return v;
        }
    }
}
