package pl.tabletcad.app;

/** Context-specific command groups shared by native toolbar and fallback hit-testing. */
public final class ToolbarCatalog {
    private ToolbarCatalog() {}
    public static final class Group {
        public final String title,icon;
        private final String[] commands;
        Group(String title,String icon,String... commands){this.title=title;this.icon=icon;this.commands=commands.clone();}
        public String[] commands(){return commands.clone();}
        public boolean direct(){return commands.length==1;}
    }
    public static Group[] groups(boolean sketch) {
        return sketch?new Group[]{
            new Group("Edytuj","Edytuj","Edytuj"),
            new Group("Wyci\u0105gnij","Wyci\u0105gnij","Wyci\u0105gnij"),
            new Group("Rysuj","Polilinia","Linia","Polilinia","Prostokąt","Kwadrat","Prostokąt od środka","Wielokąt","Okrąg","Łuk","Splajn"),
            new Group("Geometria","Geometria","Geometria","Połącz kontur","Odsunięcie","Lustro","Szyk liniowy","Szyk kołowy","Zaokrąglij szkic"),
            new Group("Przytnij","Przytnij","Przytnij","Przedłuż"),
            new Group("Więzy","Więzy","Więzy","Wymiar"),
            new Group("Usuń","Usuń","Usuń","Wyczyść szkic"),
            new Group("Widok","Obróć widok","Obróć widok","Widok na szkic","Ukryj bryłę","Wymiary","Dopasuj"),
            new Group("Anuluj szkic","Anuluj szkic","Anuluj szkic"),
            new Group("Zakończ","Zakończ","Zakończ")
        }:new Group[]{
            new Group("Wybierz","Wybierz","Wybierz"),
            new Group("Szkice","Szkic","Szkic","Edytuj"),
            new Group("Modeluj","Wyciągnij","Wyciągnij","Obrót profilu","Gwint"),
            new Group("Zmień","Zaokrąglij","Głębokość","Zaokrąglij","Fazuj","Usuń cechę"),
            new Group("Sprawdź","Zmierz","Zmierz","Właściwości bryły","Rysunek"),
            new Group("Widok","Dopasuj","Dopasuj","Izometryczny","Przód","Góra","Prawy bok","Rzut prostokątny","Wyczyść widok")
        };
    }
    public static boolean allows(boolean sketch,String command) {
        for(Group group:groups(sketch))for(String item:group.commands)if(item.equals(command))return true;
        return false;
    }
}
