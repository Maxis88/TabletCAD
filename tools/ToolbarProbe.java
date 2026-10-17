import java.util.*;
import pl.tabletcad.app.ToolbarCatalog;
import pl.tabletcad.app.SketchReadability;

public final class ToolbarProbe {
    static void require(boolean v,String s){if(!v)throw new AssertionError(s);System.out.println("PASS "+s);}
    static Set<String> commands(boolean sketch) {
        Set<String> all=new HashSet<>(),groups=new HashSet<>();
        for(ToolbarCatalog.Group group:ToolbarCatalog.groups(sketch)) {
            require(groups.add(group.title),"unique group "+group.title);
            String[] commands=group.commands();require(commands.length>0&&commands.length<=4,"bounded nonempty icon menu "+group.title);
            require(!group.icon.isEmpty(),"group has icon "+group.title);
            for(String command:commands){require(all.add(command),"single command location "+command);require(ToolbarCatalog.allows(sketch,command),"context permits "+command);}
            String first=commands[0];commands[0]="invalid";require(group.commands()[0].equals(first),"menu data immutable to consumer");
        }
        return all;
    }
    public static void main(String[] args) {
        Set<String> model=commands(false),sketch=commands(true);
        require(model.equals(new HashSet<>(Arrays.asList("Wybierz","Szkic","Edytuj","Wyciągnij","Wytnij","Obrót profilu","Głębokość","Zaokrąglij","Fazuj","Usuń cechę","Zmierz","Właściwości bryły","Dopasuj","Wyczyść widok","Rysunek"))),"all modeling commands retained plus revolution and exact properties");
        require(sketch.equals(new HashSet<>(Arrays.asList("Edytuj","Linia","Polilinia","Prostokąt","Okrąg","Łuk","Pomocnicza","Geometria","Więzy","Połącz kontur","Przytnij","Lustro","Wymiar","Usuń","Wyczyść szkic","Ukryj bryłę","Dopasuj","Anuluj szkic","Zakończ"))),"all sketch commands retained plus mirror");
        for(String draw:Arrays.asList("Linia","Polilinia","Prostokąt","Okrąg","Łuk","Pomocnicza","Przytnij","Lustro","Wymiar","Więzy"))require(!ToolbarCatalog.allows(false,draw),"drawing command absent from solid toolbar "+draw);
        for(String solid:Arrays.asList("Wyciągnij","Wytnij","Obrót profilu","Zaokrąglij","Fazuj","Usuń cechę","Rysunek"))require(!ToolbarCatalog.allows(true,solid),"solid command absent from sketch toolbar "+solid);
        ToolbarCatalog.Group finish=ToolbarCatalog.groups(true)[ToolbarCatalog.groups(true).length-1];
        require(finish.direct()&&finish.commands()[0].equals("Zakończ"),"finish sketch always one tap");
        require(ToolbarCatalog.groups(false)[0].direct(),"select always one tap");
        double[] mesh={0,0,-6,40,0,6,0,20,6,0,0,1,1};
        require(SketchReadability.bodyInFront(mesh,0,0,0,0,0),"symmetric extrusion hides interior XY sketch");
        require(!SketchReadability.bodyInFront(mesh,0,0,6,0,0),"front face remains visible in sketch editor");
        require(SketchReadability.bodyInFront(mesh,0,0,-6,0,0),"back face sketch is isolated");
        require(!SketchReadability.bodyInFront(mesh,0,20,0,0,90),"XZ front plane uses actual camera direction");
        require(SketchReadability.bodyInFront(mesh,0,0,0,0,90),"XZ interior plane is isolated");
        require(!SketchReadability.bodyInFront(mesh,0,0,0,90,0),"YZ front plane with negative X camera remains visible");
        require(SketchReadability.bodyInFront(mesh,40,0,0,90,0),"YZ back plane is isolated");
        require(!SketchReadability.bodyInFront(new double[0],0,0,0,0,0),"empty sketch needs no body hiding");
        System.out.println("ALL GROUPED TOOLBAR TESTS PASSED");
    }
}
