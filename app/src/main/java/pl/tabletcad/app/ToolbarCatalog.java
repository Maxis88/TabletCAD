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
            new Group("Rysuj","Polilinia","Linia","Polilinia","Prostokąt"),
            new Group("Krzywe","Łuk","Okrąg","Łuk"),
            new Group("Pomocnicza","Pomocnicza","Pomocnicza"),
            new Group("Zmień","Geometria","Geometria","Połącz kontur","Przytnij","Lustro"),
            new Group("Więzy","Więzy","Więzy","Wymiar"),
            new Group("Usuń","Usuń","Usuń","Wyczyść szkic"),
            new Group("Widok","Ukryj bryłę","Ukryj bryłę","Dopasuj"),
            new Group("Anuluj szkic","Anuluj szkic","Anuluj szkic"),
            new Group("Zakończ","Zakończ","Zakończ")
        }:new Group[]{
            new Group("Wybierz","Wybierz","Wybierz"),
            new Group("Szkice","Szkic","Szkic","Edytuj"),
            new Group("Modeluj","Wyciągnij","Wyciągnij","Obrót profilu"),
            new Group("Zmień","Zaokrąglij","Głębokość","Zaokrąglij","Fazuj","Usuń cechę"),
            new Group("Sprawdź","Zmierz","Zmierz","Właściwości bryły","Rysunek"),
            new Group("Widok","Dopasuj","Dopasuj","Wyczyść widok")
        };
    }
    public static boolean allows(boolean sketch,String command) {
        for(Group group:groups(sketch))for(String item:group.commands)if(item.equals(command))return true;
        return false;
    }
}
