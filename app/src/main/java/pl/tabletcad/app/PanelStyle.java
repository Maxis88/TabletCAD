package pl.tabletcad.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Visual language of the contextual side panel and its menus: a header with a close control,
 * a filled primary action, outlined secondary actions and flat list rows sized for touch.
 * Colours follow the workspace palette (graphite chrome, white panel, teal accent).
 */
public final class PanelStyle {
    private PanelStyle() {}
    public static final int INK=Color.rgb(28,39,49),MUTED=Color.rgb(101,116,130),ACCENT=Color.rgb(13,148,136),
        ACCENT_DARK=Color.rgb(10,118,108),LINE=Color.rgb(214,222,228),SURFACE=Color.rgb(246,248,250),DANGER=Color.rgb(196,64,52);

    static float dp(Context c,float v){return v*c.getResources().getDisplayMetrics().density;}
    static GradientDrawable shape(Context c,int fill,int stroke,float radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radius));
        if(stroke!=0)d.setStroke((int)Math.max(1,dp(c,1.2f)),stroke);return d;
    }
    static RippleDrawable ripple(Context c,GradientDrawable content,int pressed) {
        return new RippleDrawable(ColorStateList.valueOf(pressed),content,shape(c,Color.WHITE,0,10));
    }
    private static void base(Button b,Context c,int height) {
        b.setAllCaps(false);b.setTextSize(14);b.setMinHeight((int)dp(c,height));b.setMinimumHeight((int)dp(c,height));
        b.setPadding((int)dp(c,14),0,(int)dp(c,14),0);b.setStateListAnimator(null);b.setElevation(0);
    }
    /** Main action of a panel (Zastosuj, Utwórz…). */
    public static void primary(Button b) {
        Context c=b.getContext();base(b,c,48);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);
        b.setBackground(ripple(c,shape(c,ACCENT,0,10),Color.argb(70,255,255,255)));
    }
    /** Secondary action (Anuluj, Do powierzchni…). */
    public static void secondary(Button b) {
        Context c=b.getContext();base(b,c,46);b.setTypeface(Typeface.DEFAULT);b.setTextColor(INK);
        b.setBackground(ripple(c,shape(c,Color.WHITE,LINE,10),Color.argb(40,13,148,136)));
    }
    /** Destructive secondary action (Usuń…). */
    public static void danger(Button b) {
        Context c=b.getContext();base(b,c,46);b.setTextColor(DANGER);
        b.setBackground(ripple(c,shape(c,Color.WHITE,Color.argb(110,196,64,52),10),Color.argb(40,196,64,52)));
    }
    /** Row of a context menu: left aligned, full width, light surface. */
    public static void row(Button b) {
        Context c=b.getContext();base(b,c,46);b.setTextColor(b.getText().toString().startsWith("Usuń")?DANGER:INK);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        b.setBackground(ripple(c,shape(c,SURFACE,0,10),Color.argb(40,13,148,136)));
    }
    static boolean destructive(String label){String l=label.toLowerCase(java.util.Locale.ROOT);return l.startsWith("usuń")||l.startsWith("wyczyść");}
    static boolean dismissive(String label){return label.equals("Anuluj")||label.equals("Zamknij")||label.equals("Wróć");}
    /** Header: title and a close control on the right. */
    public static LinearLayout header(Context c,String title,Runnable close) {
        LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=new TextView(c);heading.setText(title);heading.setTextColor(INK);heading.setTextSize(17);heading.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(heading,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
        android.view.View x=new android.view.View(c){
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);Glyphs.close(canvas,paint,getWidth()/2f,getHeight()/2f,dp(c,6.5f),MUTED,dp(c,1.8f));}
        };x.setContentDescription("Zamknij panel");
        x.setBackground(ripple(c,shape(c,Color.TRANSPARENT,0,22),Color.argb(40,0,0,0)));x.setClickable(true);x.setOnClickListener(v->close.run());
        row.addView(x,new LinearLayout.LayoutParams((int)dp(c,44),(int)dp(c,44)));
        return row;
    }
    public static LinearLayout.LayoutParams spaced(Context c,float top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);p.topMargin=(int)dp(c,top);return p;
    }

    /** Uniform look of the form widgets inside a panel (labels, fields, check boxes, sliders, lists). */
    public static void form(android.view.View view) {
        Context c=view.getContext();int themeDefault=new TextView(c).getCurrentTextColor();styleTree(view,c,themeDefault);
    }
    private static void styleTree(android.view.View view,Context c,int themeDefault) {
        ColorStateList accent=ColorStateList.valueOf(ACCENT);
        if(view instanceof android.widget.CheckBox||view instanceof android.widget.RadioButton) {
            android.widget.CompoundButton b=(android.widget.CompoundButton)view;b.setButtonTintList(accent);b.setTextColor(INK);b.setTextSize(14);b.setMinHeight((int)dp(c,44));
        } else if(view instanceof android.widget.EditText) {
            android.widget.EditText e=(android.widget.EditText)view;e.setTextSize(16);e.setTextColor(INK);e.setBackgroundTintList(accent);e.setMinHeight((int)dp(c,46));
        } else if(view instanceof Button) {
            // Buttons placed inside forms by tools (e.g. "Do powierzchni…", "Powiel całą bryłę…").
            // Android's default button also has a RippleDrawable; style it explicitly.
            secondary((Button)view);
        } else if(view instanceof TextView) {
            TextView t=(TextView)view;if(t.getCurrentTextColor()==themeDefault){t.setTextColor(MUTED);t.setTextSize(13);t.setLineSpacing(0,1.1f);t.setPadding(t.getPaddingLeft(),(int)dp(c,6),t.getPaddingRight(),(int)dp(c,2));}
        } else if(view instanceof android.widget.SeekBar) {
            android.widget.SeekBar s=(android.widget.SeekBar)view;s.setProgressTintList(accent);s.setThumbTintList(accent);
        } else if(view instanceof android.widget.Spinner) {
            view.setMinimumHeight((int)dp(c,46));
        }
        if(view instanceof android.view.ViewGroup&&!(view instanceof android.widget.Spinner)){android.view.ViewGroup g=(android.view.ViewGroup)view;for(int i=0;i<g.getChildCount();i++)styleTree(g.getChildAt(i),c,themeDefault);}
    }
}
