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
            new Group("Rysuj","Polilinia","Linia","Polilinia","Prostokąt","Kwadrat","Prostokąt od środka","Wielokąt","Okrąg","Okrąg 3 pkt","Elipsa","Szczelina","Łuk","Splajn","Punkt","Napis"),
            new Group("Geometria","Geometria","Geometria","Odsunięcie","Rzutuj","Połącz kontur","Powiązania profilu"),
            new Group("Przekształć","Lustro","Położenie","Przesuń szkic","Obróć szkic","Skaluj szkic","Lustro","Szyk liniowy","Szyk kołowy","Szyk po ścieżce","Edytuj szyk"),
            new Group("Przytnij","Przytnij","Przytnij","Przedłuż","Zaokrąglij szkic"),
            new Group("Więzy","Więzy","Więzy","Wymiar"),
            new Group("Usuń","Usuń","Usuń","Wyczyść szkic"),
            new Group("Widok","Obróć widok","Obróć widok","Widok na szkic","Bryła","Bryła z liniami niewidocznymi","Przekrój","Ukryj bryłę","Wymiary","Dopasuj"),
            new Group("Anuluj szkic","Anuluj szkic","Anuluj szkic"),
            new Group("Zakończ","Zakończ","Zakończ")
        }:new Group[]{
            new Group("Wybierz","Wybierz","Wybierz"),
            new Group("Szkic","Szkic","Szkic","Edytuj"),
            new Group("Utwórz","Wyciągnij","Wyciągnij","Obrót profilu","Przejście","Przeciągnij","Otwór","Gwint","Powłoka"),
            new Group("Zmień","Zaokrąglij","Zaokrąglij","Fazuj","Pochyl ściany","Odsuń ścianę","Głębokość","Edytuj profil źródłowy","Powiązania profilu","Usuń cechę"),
            new Group("Bryły","Połącz bryły","Połącz bryły","Odejmij bryły","Część wspólna brył","Podziel bryłę","Przesuń bryłę","Obróć bryłę","Skaluj bryłę","Połóż na stole"),
            new Group("Powiel","Szyk liniowy bryły","Lustro bryły","Szyk liniowy bryły","Szyk kołowy bryły","Lustro operacji","Szyk liniowy operacji","Szyk kołowy operacji"),
            new Group("Złożenie","Komponent","Nowy komponent","Wstaw wystąpienie","Dosuń ściany","Kolizje","Lista części"),
            new Group("Konstrukcja","Elementy konstrukcyjne","Płaszczyzna odsunięta","Płaszczyzna pod kątem","Płaszczyzna przez 3 punkty","Oś konstrukcyjna","Punkt konstrukcyjny"),
            new Group("Pomiary","Zmierz","Zmierz","Zmierz kąt","Pole ściany","Właściwości bryły","Odległość brył","Nawisy","Rysunek"),
            new Group("Widok","Dopasuj","Dopasuj","Izometryczny","Przód","Góra","Prawy bok","Rzut prostokątny","Bryła","Bryła z liniami niewidocznymi","Krawędzie","Przezroczystość","Przekrój","Wyczyść widok")
        };
    }
    public static boolean allows(boolean sketch,String command) {
        for(Group group:groups(sketch))for(String item:group.commands)if(item.equals(command))return true;
        return false;
    }
}
