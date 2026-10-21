package pl.tabletcad.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Program options (Opcje): per-device preferences that change how TabletCAD looks and behaves,
 * never the stored geometry of a project. Kept in SharedPreferences "options".
 */
final class AppOptions {
    private AppOptions() {}
    static final String[] THEMES={"Jasny","Ciemny"};
    static final String[] BACKGROUNDS={"Jasne","Szare","Błękitne","Ciepłe"};
    /** Dark theme: the same four backgrounds as deep graphite tones. */
    private static final int[][] BG_DARK={{36,42,50},{50,54,60},{28,38,52},{46,41,36}};
    private static final int[][] GRID_DARK={{52,60,70},{66,71,78},{42,54,70},{62,56,49}};
    private static final int[][] BG={{242,245,247},{214,219,224},{226,234,243},{245,241,233}};
    private static final int[][] GRID={{220,227,232},{196,203,209},{205,216,228},{226,220,208}};
    static final String[] ACCENTS={"Morski","Niebieski","Pomarańczowy","Fioletowy","Grafitowy"};
    private static final int[][] ACCENT={{13,148,136},{37,99,190},{214,110,24},{124,77,196},{70,84,98}};
    private static final int[][] ACCENT_DARK={{10,118,108},{28,78,152},{176,88,16},{98,58,160},{48,60,72}};
    static final String[] MODEL_COLORS={"Morski","Stalowy","Niebieski","Pomarańczowy","Zielony"};
    private static final int[][] SHADOW={{70,98,104},{78,84,92},{52,78,120},{122,72,34},{56,96,60}};
    private static final int[][] LIGHT={{232,245,243},{236,238,241},{222,233,248},{250,228,204},{226,243,221}};

    /** Threads are swept B-Rep cuts; when off they are left out of the view and measurements (exports keep them). */
    static boolean showThreads=true,showGrid=true,showViewCube=true,showSketchDimensions=true;
    static int theme=0,background=0,accent=0,modelColor=0;
    /** Theme the running activity was started with: a new choice applies to everything at the next start. */
    private static int activeTheme=0;
    static String defaultUnits="mm";

    static SharedPreferences prefs(Context c){return c.getSharedPreferences("options",Context.MODE_PRIVATE);}
    static void load(Context c) {
        SharedPreferences p=prefs(c);
        showThreads=p.getBoolean("showThreads",true);showGrid=p.getBoolean("showGrid",true);showViewCube=p.getBoolean("showViewCube",true);
        showSketchDimensions=p.getBoolean("showSketchDimensions",true);
        theme=clamp(p.getInt("theme",0),THEMES.length);activeTheme=theme;background=clamp(p.getInt("background",0),BACKGROUNDS.length);accent=clamp(p.getInt("accent",0),ACCENTS.length);modelColor=clamp(p.getInt("modelColor",0),MODEL_COLORS.length);
        String units=p.getString("defaultUnits","mm");defaultUnits="mm";for(String code:Units.CODES)if(code.equals(units))defaultUnits=code;
        apply();
    }
    static void save(Context c) {
        prefs(c).edit().putBoolean("showThreads",showThreads).putBoolean("showGrid",showGrid).putBoolean("showViewCube",showViewCube)
            .putBoolean("showSketchDimensions",showSketchDimensions).putInt("theme",theme).putInt("background",background).putInt("accent",accent).putInt("modelColor",modelColor)
            .putString("defaultUnits",defaultUnits).apply();
        apply();
    }
    private static int clamp(int v,int n){return v<0||v>=n?0:v;}
    private static int rgb(int[] c){return Color.rgb(c[0],c[1],c[2]);}
    /** Pushes the choices into the drawing code (colours, kernel packing). */
    static void apply() {
        CadWorkspaceView.ACCENT=rgb(ACCENT[accent]);PanelStyle.ACCENT=rgb(ACCENT[accent]);PanelStyle.ACCENT_DARK=rgb(ACCENT_DARK[accent]);
        ShadeModel.setPalette(SHADOW[modelColor],LIGHT[modelColor]);
        BodyGraph.displayThreads=showThreads;
        boolean d=dark();
        CadWorkspaceView.INK=d?Color.rgb(226,232,238):Color.rgb(28,39,49);CadWorkspaceView.MUTED=d?Color.rgb(150,163,176):Color.rgb(101,116,130);
        CadWorkspaceView.PANEL=d?Color.rgb(30,36,44):Color.WHITE;CadWorkspaceView.DIVIDER=d?Color.rgb(58,67,78):Color.rgb(214,222,228);
        CadWorkspaceView.SKETCH_LINE=d?Color.rgb(110,170,255):Color.rgb(30,105,210);CadWorkspaceView.DIMENSION=d?Color.rgb(205,165,250):Color.rgb(117,70,160);
        CadWorkspaceView.MEASURE=d?Color.rgb(125,175,255):Color.rgb(45,90,190);CadWorkspaceView.SELECTION_ROW=d?Color.rgb(28,74,72):Color.rgb(226,242,240);
        CadWorkspaceView.ROW_BG=d?Color.rgb(40,47,56):Color.rgb(244,247,249);CadWorkspaceView.SKETCH_POINT=d?Color.rgb(176,190,204):Color.rgb(90,110,130);
        PanelStyle.INK=CadWorkspaceView.INK;PanelStyle.MUTED=CadWorkspaceView.MUTED;PanelStyle.FIELD=d?Color.rgb(40,47,56):Color.WHITE;
        PanelStyle.LINE=d?Color.rgb(66,76,88):Color.rgb(214,222,228);PanelStyle.SURFACE=d?Color.rgb(40,47,56):Color.rgb(246,248,250);
    }
    static boolean dark(){return activeTheme==1;}
    static boolean themePending(){return theme!=activeTheme;}
    /** Framework theme of the activity (native panels, lists, dialogs); takes effect at the next start. */
    static int activityTheme(){return dark()?android.R.style.Theme_Material_NoActionBar:0;}
    static int background(){return rgb((dark()?BG_DARK:BG)[background]);}
    static int grid(){return rgb((dark()?GRID_DARK:GRID)[background]);}
}
